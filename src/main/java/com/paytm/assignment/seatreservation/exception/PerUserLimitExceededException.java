package com.paytm.assignment.seatreservation.exception;

public class PerUserLimitExceededException extends RuntimeException {

    public PerUserLimitExceededException(String message) {
        super(message);
    }
}