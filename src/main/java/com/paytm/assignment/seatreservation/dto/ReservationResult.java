package com.paytm.assignment.seatreservation.dto;

public record ReservationResult(
        ReservationResponse response,
        boolean replay
) {
}