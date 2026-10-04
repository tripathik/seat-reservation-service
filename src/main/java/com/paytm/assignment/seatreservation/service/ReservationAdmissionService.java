package com.paytm.assignment.seatreservation.service;

import com.paytm.assignment.seatreservation.dto.ReservationResult;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public class ReservationAdmissionService {

    private final ReservationService reservationService;
    private final Semaphore reservationPermits;

    public ReservationAdmissionService(
            ReservationService reservationService,
            @Value("${reservation.max-concurrent}") int maxConcurrentReservations) {

        if (maxConcurrentReservations <= 0) {
            throw new IllegalArgumentException(
                    "reservation.max-concurrent must be greater than zero"
            );
        }

        this.reservationService = reservationService;
        this.reservationPermits = new Semaphore(maxConcurrentReservations, true);
    }

    public ReservationResult reserve(UUID showId, String userId, ReserveSeatsRequest request) {

        boolean acquired = false;

        try {
            reservationPermits.acquire();
            acquired = true;

            return reservationService.reserve(
                    showId,
                    userId,
                    request
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while waiting for reservation capacity",
                    exception
            );

        } finally {

            if (acquired) {
                reservationPermits.release();
            }
        }
    }
}