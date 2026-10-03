package com.paytm.assignment.seatreservation.metrics;

import com.paytm.assignment.seatreservation.repository.ShowRepository;
import com.paytm.assignment.seatreservation.repository.ShowSeatRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.paytm.assignment.seatreservation.util.constant.Constants.SEATS_AVAILABLE;
import static com.paytm.assignment.seatreservation.util.constant.Constants.CURRENT_AVAILABLE_SEATS_MSG;
import static com.paytm.assignment.seatreservation.util.constant.Constants.SHOW_ID;

@Component
public class SeatsAvailabilityMetrics {

    private final MeterRegistry meterRegistry;
    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;

    private final Set<UUID> registeredShows = ConcurrentHashMap.newKeySet();

    public SeatsAvailabilityMetrics(
            MeterRegistry meterRegistry,
            ShowSeatRepository showSeatRepository,
            ShowRepository showRepository) {

        this.meterRegistry = meterRegistry;
        this.showSeatRepository = showSeatRepository;
        this.showRepository = showRepository;
    }

    public void registerShow(UUID showId) {

        if (!registeredShows.add(showId)) {
            return;
        }

        Gauge.builder(
                        SEATS_AVAILABLE,
                        showSeatRepository,
                        repository -> repository.countAvailableSeats(showId)
                )
                .description(CURRENT_AVAILABLE_SEATS_MSG)
                .tag(SHOW_ID, showId.toString())
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerExistingShows() {

        showRepository.findAll()
                .forEach(show -> registerShow(show.getId()));
    }
}