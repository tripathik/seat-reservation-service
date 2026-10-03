package com.paytm.assignment.seatreservation.util.constant;

public class Constants {

    // Constants used in classes
    public static final int DEFAULT_PER_USER_LIMIT = 4;
    public static final String MESSAGE_DIGEST_ALGORITHM = "SHA-256";
    public static final String RESERVATION_CONFIRMED = "reservation.confirmed";
    public static final String RESERVATION_DECLINED = "reservation.declined";
    public static final String REASON = "reason";
    public static final String SEAT_TAKEN = "seat_taken";
    public static final String PER_USER_LIMIT = "per_user_limit";
    public static final String IDEMPOTENCY_REPLAY = "idempotent_replay";
    public static final String CONFIRMED_RESERVATION_MSG = "Number of successfully confirmed reservations";
    public static final String DECLINED_RESERVATION_MSG = "Number of declined reservation attempts";
    public static final String REPLAYED_RESERVATION_MSG = "Number of idempotent reservation replays";
    public static final String CURRENT_AVAILABLE_SEATS_MSG = "Current number of available seats for a show";
    public static final String SEATS_AVAILABLE = "seats.available";
    public static final String SHOW_ID = "show_id";


    // Validation & Error Messages
    public static final String AUTH_HEADER_REQUIRED = "Authorization header is required";
    public static final String BEARER_TOKEN_REQUIRED = "Bearer token cannot be empty";
    public static final String BEARER_KEYWORD_REQUIRED = "Authorization header must use 'Bearer' before token value";
    public static final String DUPLICATE_SEATS_NOT_ALLOWED = "Duplicate seat numbers are not allowed";
    public static final String SHOW_NOT_FOUND = "Show not found: %s";
    public static final String NO_IDEMPOTENT_RECORD = "Idempotency record was not created";
    public static final String IDEMPOTENCY_KEY_ALREADY_USED = "Idempotency key was already used for a different request";
    public static final String SHOW_USER_BOOKING_NOT_FOUND = "Show-user booking row does not exist";
    public static final String USER_BOOKING_LIMIT_EXCEEDED = "Per-user booking limit exceeded";
    public static final String SEAT_DOES_NOT_EXIST = "One or more requested seats do not exist";
    public static final String SEATS_UNAVAILABLE = "One or more requested seats are unavailable";
    public static final String RESERVATION_NOT_FOUND = "Reservation not found: %s";
    public static final String RESERVATION_CANCELLATION_NOT_ALLOWED = "You are not allowed to cancel this reservation";
    public static final String RESERVATION_SEAT_INCONSISTENT = "Reservation seat inventory is inconsistent";
    public static final String RESERVATION_SEAT_OWNERSHIP_INCONSISTENT = "Reservation seat ownership is inconsistent";
    public static final String SHA_256_ALGO_UNAVAILABLE = "SHA-256 algorithm is unavailable";
    public static final String SEAT_INVENTORY_VIOLATED = "Seat inventory invariant violated for show: %s";
    private Constants() {
    }
}
