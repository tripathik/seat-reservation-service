package com.paytm.assignment.seatreservation.service;

import com.paytm.assignment.seatreservation.dto.ReservationResponse;
import com.paytm.assignment.seatreservation.dto.ReservationResult;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.assignment.seatreservation.entity.*;
import com.paytm.assignment.seatreservation.exception.*;
import com.paytm.assignment.seatreservation.metrics.ReservationMetrics;
import com.paytm.assignment.seatreservation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static com.paytm.assignment.seatreservation.util.constant.Constants.*;

@Service
public class ReservationService {
    private static final Logger log =
            LoggerFactory.getLogger(ReservationService.class);

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ReservationRepository reservationRepository;
    private final ShowUserBookingRepository showUserBookingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationFingerprintGenerator fingerprintGenerator;
    private final ReservationMetrics reservationMetrics;

    public ReservationService(
            ShowRepository showRepository,
            ShowSeatRepository showSeatRepository,
            ReservationRepository reservationRepository,
            ShowUserBookingRepository showUserBookingRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            ReservationSeatRepository reservationSeatRepository,
            ReservationFingerprintGenerator fingerprintGenerator,
            ReservationMetrics reservationMetrics) {

        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.reservationRepository = reservationRepository;
        this.showUserBookingRepository = showUserBookingRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.fingerprintGenerator = fingerprintGenerator;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional
    public ReservationResult reserve(UUID showId, String userId, ReserveSeatsRequest request) {

        List<String> requestedSeats = request.seats()
                .stream()
                .map(String::trim)
                .sorted()
                .toList();

        if (requestedSeats.stream().distinct().count() != requestedSeats.size()) {
            throw new DuplicateSeatException(DUPLICATE_SEATS_NOT_ALLOWED);
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(SHOW_NOT_FOUND.formatted(showId)));

        String fingerprint = fingerprintGenerator.generate(showId, requestedSeats);

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
                                new IllegalStateException(NO_IDEMPOTENT_RECORD));

        if (!idempotencyRecord.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    IDEMPOTENCY_KEY_ALREADY_USED
            );
        }

        // Existing successful reservation = idempotent replay.
        if (idempotencyRecord.getReservation() != null) {

            reservationMetrics.recordIdempotentReplay();

            log.info("Reservation replayed: reservationId={}, showId={}, userId={}, seats={}",
                    idempotencyRecord.getReservation().getId(),
                    showId,
                    userId,
                    requestedSeats
            );

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
                                new IllegalStateException(SHOW_USER_BOOKING_NOT_FOUND));

        if (userBooking.getConfirmedSeatCount() + requestedSeats.size()
                > show.getPerUserLimit()) {

            reservationMetrics.recordPerUserLimitDecline();

            log.warn(
                    "Reservation declined: reason=per_user_limit, showId={}, userId={}, seats={}",
                    showId,
                    userId,
                    requestedSeats
            );

            throw new PerUserLimitExceededException(USER_BOOKING_LIMIT_EXCEEDED);
        }

        // 3. Lock requested seats in deterministic order.
        List<ShowSeat> seats =
                showSeatRepository.findSeatsForUpdate(
                        showId,
                        requestedSeats
                );

        if (seats.size() != requestedSeats.size()) {
            throw new SeatUnavailableException(SEAT_DOES_NOT_EXIST);
        }

        boolean unavailable = seats.stream()
                .anyMatch(seat ->
                        seat.getStatus() != SeatStatus.AVAILABLE);

        if (unavailable) {
            reservationMetrics.recordSeatTakenDecline();

            log.info(
                    "Reservation declined: reason=seat_taken, showId={}, userId={}, seats={}",
                    showId,
                    userId,
                    requestedSeats
            );

            throw new SeatUnavailableException(SEATS_UNAVAILABLE);
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

        // Save reservation history such as "reservation_id" & "seat_number", which will
        // be helpful to identify the data for cancelled reservation for future operation
        List<ReservationSeat> reservationSeats = requestedSeats.stream()
                .map(seatNumber ->
                        new ReservationSeat(reservation, seatNumber))
                .toList();

        reservationSeatRepository.saveAll(reservationSeats);

        // 5. Assign every requested seat atomically.
        seats.forEach(seat -> seat.confirm(reservation));

        userBooking.addConfirmedSeats(seats.size());

        idempotencyRecord.attachReservation(reservation);

        recordConfirmedReservationAfterCommit(
                reservation.getId(),
                showId,
                userId,
                requestedSeats
        );

        return new ReservationResult(
                buildResponse(reservation, requestedSeats),
                false
        );
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId, String userId) {

        // 1. Lock the reservation itself.
        Reservation reservation =
                reservationRepository.findForUpdate(reservationId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        RESERVATION_NOT_FOUND.formatted(reservationId)
                                ));

        // 2. Only the owner may cancel.
        if (!reservation.getUserId().equals(userId)) {
            throw new ReservationAccessDeniedException(RESERVATION_CANCELLATION_NOT_ALLOWED);
        }

        // 3. Historical seat membership.
        List<ReservationSeat> reservationSeats =
                reservationSeatRepository
                        .findByReservationIdOrderBySeatNumber(reservationId);

        List<String> seatNumbers = reservationSeats.stream()
                .map(ReservationSeat::getSeatNumber)
                .toList();

        // 4. Repeated cancellation is idempotent.
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.info("Reservation Cancellation replayed: reservationId={}, showId={}, userId={}",
                    reservationId,
                    reservation.getShow().getId(),
                    userId
            );
            return buildResponse(reservation, seatNumbers);
        }

        // 5. Lock the user's show-level booking counter.
        ShowUserBooking userBooking =
                showUserBookingRepository.findForUpdate(
                                reservation.getShow().getId(),
                                userId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(SHOW_USER_BOOKING_NOT_FOUND));

        // 6. Lock the CURRENT inventory rows before releasing them.
        List<ShowSeat> seats =
                showSeatRepository.findSeatsForUpdate(
                        reservation.getShow().getId(),
                        seatNumbers
                );

        if (seats.size() != seatNumbers.size()) {
            throw new IllegalStateException(RESERVATION_SEAT_INCONSISTENT);
        }

        // 7. Defensive ownership verification.
        boolean invalidOwnership = seats.stream()
                .anyMatch(seat ->
                        seat.getStatus() != SeatStatus.CONFIRMED
                                || seat.getReservation() == null
                                || !seat.getReservation().getId()
                                .equals(reservationId));

        if (invalidOwnership) {
            throw new IllegalStateException(
                    RESERVATION_SEAT_OWNERSHIP_INCONSISTENT
            );
        }

        // 8. Release inventory + update user count + cancel reservation.
        seats.forEach(ShowSeat::release);

        userBooking.removeConfirmedSeats(seats.size());

        reservation.cancel();

        recordCancellationAfterCommit(
                reservation.getId(),
                reservation.getShow().getId(),
                userId,
                seatNumbers
        );

        return buildResponse(reservation, seatNumbers);
    }

    private ReservationResponse buildResponse(Reservation reservation, List<String> seats) {

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShow().getId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                reservation.getStatus().name().toLowerCase()
        );
    }

    private void recordConfirmedReservationAfterCommit(UUID reservationId, UUID showId, String userId, List<String> seats) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {

                        reservationMetrics.recordConfirmedReservation();

                        log.info("Reservation confirmed: reservationId={}, showId={}, userId={}, seats={}",
                                reservationId,
                                showId,
                                userId,
                                seats
                        );
                    }
                }
        );
    }

    private void recordCancellationAfterCommit(UUID reservationId, UUID showId, String userId, List<String> seats) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {

                        log.info("Reservation cancelled: reservationId={}, showId={}, userId={}, seats={}",
                                reservationId,
                                showId,
                                userId,
                                seats
                        );
                    }
                }
        );
    }
}