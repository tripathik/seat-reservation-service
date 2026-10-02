package com.paytm.assignment.seatreservation.exception;

public class DuplicateSeatException extends RuntimeException {

    public DuplicateSeatException(String message) {
        super(message);
    }
}