package com.paytm.assignment.seatreservation.integration;

import com.paytm.assignment.seatreservation.dto.*;
import com.paytm.assignment.seatreservation.entity.*;
import com.paytm.assignment.seatreservation.exception.ReservationAccessDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CancellationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldCancelReservationAndReleaseSeats() {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "cancellation-test-show",
                List.of("I1", "I2", "I3"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        ReservationResult reservationResult =
                reservationService.reserve(
                        show.id(),
                        "cancel-user",
                        new ReserveSeatsRequest(
                                List.of("I1", "I2"),
                                "cancel-key-1"
                        )
                );

        UUID reservationId =
                reservationResult.response().reservationId();

        ReservationResponse cancellationResponse =
                reservationService.cancel(
                        reservationId,
                        "cancel-user"
                );

        assertEquals(reservationId, cancellationResponse.reservationId());
        assertEquals("cancelled", cancellationResponse.status());

        Reservation reservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow();

        assertEquals(
                ReservationStatus.CANCELLED,
                reservation.getStatus()
        );

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(SeatStatus.AVAILABLE, seats.get(0).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(1).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(2).getStatus());

        assertNull(seats.get(0).getReservation());
        assertNull(seats.get(1).getReservation());
    }

    @Test
    void shouldHandleRepeatedCancellationIdempotently() {

        ShowResponse show = showService.createShow(
                new CreateShowRequest(
                        "repeated-cancellation-show",
                        List.of("J1", "J2", "J3"),
                        25000
                )
        );

        ReservationResult reservationResult =
                reservationService.reserve(
                        show.id(),
                        "cancel-user",
                        new ReserveSeatsRequest(
                                List.of("J1", "J2"),
                                "cancel-idempotency-key"
                        )
                );

        UUID reservationId =
                reservationResult.response().reservationId();

        ReservationResponse firstCancellation =
                reservationService.cancel(
                        reservationId,
                        "cancel-user"
                );

        ReservationResponse secondCancellation =
                reservationService.cancel(
                        reservationId,
                        "cancel-user"
                );

        assertEquals(reservationId, firstCancellation.reservationId());
        assertEquals(reservationId, secondCancellation.reservationId());

        assertEquals("cancelled", firstCancellation.status());
        assertEquals("cancelled", secondCancellation.status());

        Reservation reservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow();

        assertEquals(
                ReservationStatus.CANCELLED,
                reservation.getStatus()
        );

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertTrue(
                seats.stream()
                        .allMatch(seat ->
                                seat.getStatus() == SeatStatus.AVAILABLE)
        );

        assertNull(seats.get(0).getReservation());
        assertNull(seats.get(1).getReservation());

        ShowUserBooking userBooking =
                showUserBookingRepository
                        .findByShowIdAndUserId(show.id(), "cancel-user")
                        .orElseThrow();

        assertEquals(0, userBooking.getConfirmedSeatCount());
    }

    @Test
    void shouldRejectCancellationByDifferentUser() {

        ShowResponse show = showService.createShow(
                new CreateShowRequest(
                        "unauthorized-cancellation-show",
                        List.of("K1", "K2"),
                        25000
                )
        );

        ReservationResult reservationResult =
                reservationService.reserve(
                        show.id(),
                        "owner-user",
                        new ReserveSeatsRequest(
                                List.of("K1"),
                                "owner-key"
                        )
                );

        UUID reservationId =
                reservationResult.response().reservationId();

        assertThrows(
                ReservationAccessDeniedException.class,
                () -> reservationService.cancel(
                        reservationId,
                        "attacker-user"
                )
        );

        // Reservation must remain confirmed
        Reservation reservation =
                reservationRepository.findById(reservationId)
                        .orElseThrow();

        assertEquals(
                ReservationStatus.CONFIRMED,
                reservation.getStatus()
        );

        // Seat must still belong to the original reservation
        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());
        assertNotNull(seats.get(0).getReservation());

        assertEquals(
                reservationId,
                seats.get(0).getReservation().getId()
        );

        // Owner's booking count must remain unchanged
        ShowUserBooking ownerBooking =
                showUserBookingRepository
                        .findByShowIdAndUserId(show.id(), "owner-user")
                        .orElseThrow();

        assertEquals(1, ownerBooking.getConfirmedSeatCount());

        assertEquals(1, reservationRepository.count());
    }

    @Test
    void shouldAllowAnotherUserToRebookSeatAfterCancellation() {

        ShowResponse show = showService.createShow(
                new CreateShowRequest(
                        "cancel-and-rebook-show",
                        List.of("L1", "L2"),
                        25000
                )
        );

        // User A reserves L1
        ReservationResult firstReservation =
                reservationService.reserve(
                        show.id(),
                        "user-a",
                        new ReserveSeatsRequest(
                                List.of("L1"),
                                "user-a-key"
                        )
                );

        UUID firstReservationId =
                firstReservation.response().reservationId();

        // User A cancels
        reservationService.cancel(
                firstReservationId,
                "user-a"
        );

        // User B reserves the released L1
        ReservationResult secondReservation =
                reservationService.reserve(
                        show.id(),
                        "user-b",
                        new ReserveSeatsRequest(
                                List.of("L1"),
                                "user-b-key"
                        )
                );

        UUID secondReservationId =
                secondReservation.response().reservationId();

        assertNotEquals(
                firstReservationId,
                secondReservationId
        );

        // Old reservation must remain in history as CANCELLED
        Reservation oldReservation =
                reservationRepository.findById(firstReservationId)
                        .orElseThrow();

        assertEquals(
                ReservationStatus.CANCELLED,
                oldReservation.getStatus()
        );

        // New reservation must be CONFIRMED
        Reservation newReservation =
                reservationRepository.findById(secondReservationId)
                        .orElseThrow();

        assertEquals(
                ReservationStatus.CONFIRMED,
                newReservation.getStatus()
        );

        // L1 must now belong to the new reservation
        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());

        assertNotNull(seats.get(0).getReservation());

        assertEquals(
                secondReservationId,
                seats.get(0).getReservation().getId()
        );

        assertEquals(2, reservationRepository.count());

        // User A should have zero active confirmed seats
        ShowUserBooking userABooking =
                showUserBookingRepository
                        .findByShowIdAndUserId(show.id(), "user-a")
                        .orElseThrow();

        assertEquals(0, userABooking.getConfirmedSeatCount());

        // User B should have one
        ShowUserBooking userBBooking =
                showUserBookingRepository
                        .findByShowIdAndUserId(show.id(), "user-b")
                        .orElseThrow();

        assertEquals(1, userBBooking.getConfirmedSeatCount());
    }

}
