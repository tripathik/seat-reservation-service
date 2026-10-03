package com.paytm.assignment.seatreservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateSeatException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateSeat(
            DuplicateSeatException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "DUPLICATE_SEAT",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Invalid request");

        ApiErrorResponse response = new ApiErrorResponse(
                "VALIDATION_ERROR",
                message,
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler({
            SeatUnavailableException.class,
            PerUserLimitExceededException.class,
            IdempotencyConflictException.class
    })
    public ResponseEntity<ApiErrorResponse> handleReservationConflict(
            RuntimeException exception) {

        String code;

        if (exception instanceof SeatUnavailableException) {
            code = "SEAT_UNAVAILABLE";
        } else if (exception instanceof PerUserLimitExceededException) {
            code = "PER_USER_LIMIT_EXCEEDED";
        } else {
            code = "IDEMPOTENCY_CONFLICT";
        }

        ApiErrorResponse response = new ApiErrorResponse(
                code,
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleShowNotFound(
            ShowNotFoundException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "SHOW_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(InvalidAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAuthentication(
            InvalidAuthenticationException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_AUTHENTICATION",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReservationNotFound(
            ReservationNotFoundException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "RESERVATION_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(ReservationAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleReservationAccessDenied(
            ReservationAccessDeniedException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "RESERVATION_ACCESS_DENIED",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(SeatLockContentionException.class)
    public ResponseEntity<ApiErrorResponse> handleSeatLockContention(SeatLockContentionException exception) {

        ApiErrorResponse response = new ApiErrorResponse(
                "SEAT_UNAVAILABLE",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
}