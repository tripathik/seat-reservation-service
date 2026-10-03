package com.paytm.assignment.seatreservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import static com.paytm.assignment.seatreservation.util.constant.Constants.RESERVATION_CONFIRMED;
import static com.paytm.assignment.seatreservation.util.constant.Constants.RESERVATION_DECLINED;
import static com.paytm.assignment.seatreservation.util.constant.Constants.REASON;
import static com.paytm.assignment.seatreservation.util.constant.Constants.SEAT_TAKEN;
import static com.paytm.assignment.seatreservation.util.constant.Constants.PER_USER_LIMIT;
import static com.paytm.assignment.seatreservation.util.constant.Constants.IDEMPOTENCY_REPLAY;
import static com.paytm.assignment.seatreservation.util.constant.Constants.CONFIRMED_RESERVATION_MSG;
import static com.paytm.assignment.seatreservation.util.constant.Constants.DECLINED_RESERVATION_MSG;
import static com.paytm.assignment.seatreservation.util.constant.Constants.REPLAYED_RESERVATION_MSG;

@Component
public class ReservationMetrics {

    private final Counter confirmedReservations;

    private final Counter seatTakenDeclines;
    private final Counter perUserLimitDeclines;
    private final Counter idempotentReplays;

    public ReservationMetrics(MeterRegistry meterRegistry) {

        this.confirmedReservations = Counter.builder(RESERVATION_CONFIRMED)
                .description(CONFIRMED_RESERVATION_MSG)
                .register(meterRegistry);

        this.seatTakenDeclines = Counter.builder(RESERVATION_DECLINED)
                .description(DECLINED_RESERVATION_MSG)
                .tag(REASON, SEAT_TAKEN)
                .register(meterRegistry);

        this.perUserLimitDeclines = Counter.builder(RESERVATION_DECLINED)
                .description(DECLINED_RESERVATION_MSG)
                .tag(REASON, PER_USER_LIMIT)
                .register(meterRegistry);

        this.idempotentReplays = Counter.builder(RESERVATION_DECLINED)
                .description(REPLAYED_RESERVATION_MSG)
                .tag(REASON, IDEMPOTENCY_REPLAY)
                .register(meterRegistry);
    }

    public void recordConfirmedReservation() {
        confirmedReservations.increment();
    }

    public void recordSeatTakenDecline() {
        seatTakenDeclines.increment();
    }

    public void recordPerUserLimitDecline() {
        perUserLimitDeclines.increment();
    }

    public void recordIdempotentReplay() {
        idempotentReplays.increment();
    }
}