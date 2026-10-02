package com.paytm.assignment.seatreservation.dto;

public record SeatCounts(
        long available,
        long held,
        long confirmed,
        long total
) {
}