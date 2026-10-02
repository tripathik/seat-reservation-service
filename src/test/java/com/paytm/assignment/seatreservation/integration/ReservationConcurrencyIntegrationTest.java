package com.paytm.assignment.seatreservation.integration;

import com.paytm.assignment.seatreservation.dto.CreateShowRequest;
import com.paytm.assignment.seatreservation.dto.ReservationResult;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.assignment.seatreservation.dto.ShowResponse;
import com.paytm.assignment.seatreservation.entity.SeatStatus;
import com.paytm.assignment.seatreservation.entity.ShowSeat;
import com.paytm.assignment.seatreservation.exception.PerUserLimitExceededException;
import com.paytm.assignment.seatreservation.exception.SeatUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ReservationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldAllowOnlyOneWinnerWhenMultipleUsersReserveSameSeatConcurrently()
            throws Exception {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "hot-seat-show",
                List.of("F1"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        int numberOfUsers = 20;

        try (ExecutorService executorService =
                     Executors.newFixedThreadPool(numberOfUsers)) {

            CountDownLatch ready = new CountDownLatch(numberOfUsers);
            CountDownLatch start = new CountDownLatch(1);

            AtomicInteger successfulReservations = new AtomicInteger();
            AtomicInteger rejectedReservations = new AtomicInteger();
            AtomicInteger unexpectedErrors = new AtomicInteger();

            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < numberOfUsers; i++) {

                int userNumber = i;

                futures.add(executorService.submit(() -> {

                    ready.countDown();

                    try {
                        start.await();

                        reservationService.reserve(
                                show.id(),
                                "hot-seat-user-" + userNumber,
                                new ReserveSeatsRequest(
                                        List.of("F1"),
                                        "hot-seat-key-" + userNumber
                                )
                        );

                        successfulReservations.incrementAndGet();

                    } catch (SeatUnavailableException e) {

                        rejectedReservations.incrementAndGet();

                    } catch (Exception e) {

                        unexpectedErrors.incrementAndGet();
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            // Release all workers at approximately the same time.
            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            executorService.shutdown();

            assertEquals(1, successfulReservations.get());
            assertEquals(numberOfUsers - 1, rejectedReservations.get());
            assertEquals(0, unexpectedErrors.get());

            assertEquals(1, reservationRepository.count());

            List<ShowSeat> seats =
                    showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

            assertEquals(1, seats.size());
            assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());
            assertNotNull(seats.get(0).getReservation());
        }
    }

    @Test
    void shouldEnforcePerUserLimitUnderConcurrentReservations()
            throws Exception {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "concurrent-user-limit-show",
                List.of("G1", "G2", "G3", "G4", "G5", "G6"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            AtomicInteger successfulReservations = new AtomicInteger();
            AtomicInteger limitRejections = new AtomicInteger();
            AtomicInteger unexpectedErrors = new AtomicInteger();

            List<Future<?>> futures = new ArrayList<>();

            List<List<String>> requests = List.of(
                    List.of("G1", "G2", "G3"),
                    List.of("G4", "G5", "G6")
            );

            for (int i = 0; i < requests.size(); i++) {

                int requestNumber = i;
                List<String> requestedSeats = requests.get(i);

                futures.add(executorService.submit(() -> {

                    ready.countDown();

                    try {
                        start.await();

                        reservationService.reserve(
                                show.id(),
                                "same-user",
                                new ReserveSeatsRequest(
                                        requestedSeats,
                                        "concurrent-limit-key-" + requestNumber
                                )
                        );

                        successfulReservations.incrementAndGet();

                    } catch (PerUserLimitExceededException e) {

                        limitRejections.incrementAndGet();

                    } catch (Exception e) {

                        unexpectedErrors.incrementAndGet();
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            executorService.shutdown();

            assertEquals(1, successfulReservations.get());
            assertEquals(1, limitRejections.get());
            assertEquals(0, unexpectedErrors.get());

            assertEquals(1, reservationRepository.count());

            List<ShowSeat> seats =
                    showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

            long confirmedSeats = seats.stream()
                    .filter(seat -> seat.getStatus() == SeatStatus.CONFIRMED)
                    .count();

            assertEquals(3, confirmedSeats);
        }
    }

    @Test
    void shouldCreateOnlyOneReservationForConcurrentIdempotentRequests()
            throws Exception {

        CreateShowRequest createShowRequest = new CreateShowRequest(
                "concurrent-idempotency-show",
                List.of("H1", "H2"),
                25000
        );

        ShowResponse show = showService.createShow(createShowRequest);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<ReservationResult>> futures = new ArrayList<>();

            for (int i = 0; i < 2; i++) {

                futures.add(executorService.submit(() -> {

                    ready.countDown();
                    start.await();

                    return reservationService.reserve(
                            show.id(),
                            "same-idempotency-user",
                            new ReserveSeatsRequest(
                                    List.of("H1"),
                                    "same-concurrent-key"
                            )
                    );
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            ReservationResult firstResult =
                    futures.get(0).get(30, TimeUnit.SECONDS);

            ReservationResult secondResult =
                    futures.get(1).get(30, TimeUnit.SECONDS);

            executorService.shutdown();

            assertEquals(
                    firstResult.response().reservationId(),
                    secondResult.response().reservationId()
            );

            assertNotEquals(
                    firstResult.replay(),
                    secondResult.replay()
            );

            assertEquals(1, reservationRepository.count());

            List<ShowSeat> seats =
                    showSeatRepository.findByShowIdOrderBySeatNumber(show.id());

            assertEquals(SeatStatus.CONFIRMED, seats.get(0).getStatus());
            assertEquals(SeatStatus.AVAILABLE, seats.get(1).getStatus());
        }
    }

}
