package com.paytm.assignment.seatreservation.service;

import com.paytm.assignment.seatreservation.dto.ReservationResponse;
import com.paytm.assignment.seatreservation.dto.ReservationResult;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.assignment.seatreservation.entity.*;
import com.paytm.assignment.seatreservation.exception.*;
import com.paytm.assignment.seatreservation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ReservationRepository reservationRepository;
    private final ShowUserBookingRepository showUserBookingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public ReservationService(
            ShowRepository showRepository,
            ShowSeatRepository showSeatRepository,
            ReservationRepository reservationRepository,
            ShowUserBookingRepository showUserBookingRepository,
            IdempotencyRecordRepository idempotencyRecordRepository) {

        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.reservationRepository = reservationRepository;
        this.showUserBookingRepository = showUserBookingRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public ReservationResult reserve(UUID showId, String userId, ReserveSeatsRequest request) {

        List<String> requestedSeats = request.seats()
                .stream()
                .map(String::trim)
                .sorted()
                .toList();

        if (requestedSeats.stream().distinct().count() != requestedSeats.size()) {
            throw new DuplicateSeatException(
                    "Duplicate seat numbers are not allowed"
            );
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(
                        "Show not found: %s".formatted(showId)
                ));

        String fingerprint = createFingerprint(showId, requestedSeats);

        // 1. Establish and lock the idempotency record.
        idempotencyRecordRepository.createIfAbsent(
                userId,
                request.idempotencyKey(),
                fingerprint
        );

        IdempotencyRecord idempotencyRecord =
                idempotencyRecordRepository.findForUpdate(
                                userId,
                                request.idempotencyKey()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Idempotency record was not created"
                                ));

        if (!idempotencyRecord.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used for a different request"
            );
        }

        // Existing successful reservation = idempotent replay.
        if (idempotencyRecord.getReservation() != null) {
            return new ReservationResult(
                    buildResponse(
                            idempotencyRecord.getReservation(),
                            requestedSeats
                    ),
                    true
            );
        }

        // 2. Establish and lock this user's counter for this show.
        showUserBookingRepository.createIfAbsent(showId, userId);

        ShowUserBooking userBooking =
                showUserBookingRepository.findForUpdate(showId, userId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Show-user booking row was not created"
                                ));

        if (userBooking.getConfirmedSeatCount() + requestedSeats.size()
                > show.getPerUserLimit()) {

            throw new PerUserLimitExceededException("Per-user booking limit exceeded");
        }

        // 3. Lock requested seats in deterministic order.
        List<ShowSeat> seats =
                showSeatRepository.findSeatsForUpdate(
                        showId,
                        requestedSeats
                );

        if (seats.size() != requestedSeats.size()) {
            throw new SeatUnavailableException("One or more requested seats do not exist");
        }

        boolean unavailable = seats.stream()
                .anyMatch(seat ->
                        seat.getStatus() != SeatStatus.AVAILABLE);

        if (unavailable) {
            throw new SeatUnavailableException("One or more requested seats are unavailable");
        }

        // 4. Create the reservation.
        long amountPaise = Math.multiplyExact(
                show.getPricePaise(),
                requestedSeats.size()
        );

        Reservation reservation = new Reservation(
                show,
                userId,
                amountPaise
        );

        reservationRepository.save(reservation);

        // 5. Assign every requested seat atomically.
        seats.forEach(seat -> seat.confirm(reservation));

        userBooking.addConfirmedSeats(seats.size());

        idempotencyRecord.attachReservation(reservation);

        return new ReservationResult(
                buildResponse(reservation, requestedSeats),
                false
        );
    }

    private ReservationResponse buildResponse(
            Reservation reservation,
            List<String> seats) {

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShow().getId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                reservation.getStatus().name().toLowerCase()
        );
    }

    private String createFingerprint(UUID showId, List<String> seats) {

        String canonicalRequest = showId + "|" + String.join(",", seats);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}