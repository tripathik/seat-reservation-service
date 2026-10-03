package com.paytm.assignment.seatreservation.exception;

public class SeatLockContentionException extends RuntimeException {

    public SeatLockContentionException(String message) {
        super(message);
    }
}