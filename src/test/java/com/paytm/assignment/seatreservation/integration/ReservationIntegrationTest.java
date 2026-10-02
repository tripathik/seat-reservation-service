package com.paytm.assignment.seatreservation.integration;

import com.paytm.assignment.seatreservation.dto.*;
import com.paytm.assignment.seatreservation.entity.SeatStatus;
import com.paytm.assignment.seatreservation.entity.ShowSeat;
import com.paytm.assignment.seatreservation.exception.IdempotencyConflictException;
import com.paytm.assignment.seatreservation.exception.PerUserLimitExceededException;
import com.paytm.assignment.seatreservation.exception.SeatUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class ReservationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldReserveSeatsSuccessfully() {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "integration-test-show",
                List.of("A1", "A2", "A3", "A4"),
                25000
        );

        ShowResponse show =
                showService.createShow(createShowRequest);

        ReserveSeatsRequest reserveRequest =
                new ReserveSeatsRequest(
                        List.of("A1", "A2"),
                        "integration-key-1"
                );

        ReservationResult result =
                reservationService.reserve(
                        show.id(),
                        "test-user-1",
                        reserveRequest
                );

        assertNotNull(result.response().reservationId());
        assertFalse(result.replay());
        assertEquals("test-user-1", result.response().userId());
        assertEquals(50000, result.response().amountPaise());
        assertEquals("confirmed", result.response().status());
        assertEquals(List.of("A1", "A2"), result.response().seats());

        assertEquals(1, reservationRepository.count());

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(4, seats.size());

        assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());
        assertEquals(SeatStatus.CONFIRMED, seats.get(1).getStatus());

        assertEquals(SeatStatus.AVAILABLE, seats.get(2).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(3).getStatus());
    }

    @Test
    void shouldReturnSameReservationForIdempotentRetry() {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "idempotency-test-show",
                List.of("B1", "B2", "B3"),
                25000
        );

        ShowResponse show =
                showService.createShow(createShowRequest);

        ReserveSeatsRequest request =
                new ReserveSeatsRequest(
                        List.of("B1", "B2"),
                        "idempotency-key-1"
                );

        ReservationResult firstResult =
                reservationService.reserve(
                        show.id(),
                        "idempotency-user",
                        request
                );

        ReservationResult secondResult =
                reservationService.reserve(
                        show.id(),
                        "idempotency-user",
                        request
                );

        assertEquals(
                firstResult.response().reservationId(),
                secondResult.response().reservationId()
        );

        assertFalse(firstResult.replay());

        assertTrue(secondResult.replay());


        assertEquals(1, reservationRepository.count());
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequest() {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "idempotency-conflict-show",
                List.of("C1", "C2", "C3"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        ReserveSeatsRequest firstRequest =
                new ReserveSeatsRequest(
                        List.of("C1"),
                        "same-idempotency-key"
                );

        reservationService.reserve(
                show.id(),
                "idempotency-user",
                firstRequest
        );

        ReserveSeatsRequest conflictingRequest =
                new ReserveSeatsRequest(
                        List.of("C2"),
                        "same-idempotency-key"
                );

        assertThrows(
                IdempotencyConflictException.class,
                () -> reservationService.reserve(
                        show.id(),
                        "idempotency-user",
                        conflictingRequest
                )
        );

        assertEquals(1, reservationRepository.count());

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(1).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(2).getStatus());
    }

    @Test
    void shouldEnforcePerUserBookingLimit() {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "per-user-limit-show",
                List.of("D1", "D2", "D3", "D4", "D5"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        ReservationResult firstReservation =
                reservationService.reserve(
                        show.id(),
                        "limit-user",
                        new ReserveSeatsRequest(
                                List.of("D1", "D2", "D3"),
                                "limit-key-1"
                        )
                );

        assertEquals(3, firstReservation.response().seats().size());

        ReservationResult secondReservation =
                reservationService.reserve(
                        show.id(),
                        "limit-user",
                        new ReserveSeatsRequest(
                                List.of("D4"),
                                "limit-key-2"
                        )
                );

        assertEquals(1, secondReservation.response().seats().size());

        assertThrows(
                PerUserLimitExceededException.class,
                () -> reservationService.reserve(
                        show.id(),
                        "limit-user",
                        new ReserveSeatsRequest(
                                List.of("D5"),
                                "limit-key-3"
                        )
                )
        );

        assertEquals(2, reservationRepository.count());

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());
        assertEquals(SeatStatus.CONFIRMED, seats.get(1).getStatus());
        assertEquals(SeatStatus.CONFIRMED, seats.get(2).getStatus());
        assertEquals(SeatStatus.CONFIRMED, seats.get(3).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(4).getStatus());
    }

    @Test
    void shouldRejectEntireReservationWhenOneRequestedSeatIsUnavailable() {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "all-or-nothing-show",
                List.of("E1", "E2", "E3"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        reservationService.reserve(
                show.id(),
                "user-1",
                new ReserveSeatsRequest(
                        List.of("E2"),
                        "user-1-key"
                )
        );

        assertThrows(
                SeatUnavailableException.class,
                () -> reservationService.reserve(
                        show.id(),
                        "user-2",
                        new ReserveSeatsRequest(
                                List.of("E1", "E2", "E3"),
                                "user-2-key"
                        )
                )
        );

        assertEquals(1, reservationRepository.count());

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

        assertEquals(3, seats.size());

        assertEquals(SeatStatus.AVAILABLE, seats.get(0).getStatus());
        assertEquals(SeatStatus.CONFIRMED, seats.get(1).getStatus());
        assertEquals(SeatStatus.AVAILABLE, seats.get(2).getStatus());
    }
}