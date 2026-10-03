CREATE TABLE shows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_paise BIGINT NOT NULL,
    per_user_limit INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_reservations_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id)
);

CREATE TABLE show_seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL,
    seat_number VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    reservation_id UUID,

    CONSTRAINT fk_show_seats_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id),

    CONSTRAINT fk_show_seats_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations(id),

    CONSTRAINT uk_show_seat_number
        UNIQUE (show_id, seat_number)
);

CREATE TABLE show_user_bookings (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    confirmed_seat_count INTEGER NOT NULL,

    CONSTRAINT fk_show_user_bookings_show
        FOREIGN KEY (show_id)
        REFERENCES shows(id),

    CONSTRAINT uk_show_user_booking
        UNIQUE (show_id, user_id)
);

CREATE TABLE reservation_seats (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    seat_number VARCHAR(255) NOT NULL,

    CONSTRAINT fk_reservation_seats_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations(id),

    CONSTRAINT uk_reservation_seat
        UNIQUE (reservation_id, seat_number)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(255) NOT NULL,
    reservation_id UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_idempotency_records_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations(id),

    CONSTRAINT uk_user_idempotency_key
        UNIQUE (user_id, idempotency_key)
);