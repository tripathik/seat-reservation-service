package com.paytm.assignment.seatreservation.integration;

import com.paytm.assignment.seatreservation.repository.ReservationRepository;
import com.paytm.assignment.seatreservation.repository.ShowSeatRepository;
import com.paytm.assignment.seatreservation.repository.ShowUserBookingRepository;
import com.paytm.assignment.seatreservation.service.ReservationService;
import com.paytm.assignment.seatreservation.service.ShowService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.TimeZone;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    static {
        postgres.start();
    }

    @Autowired
    public ShowService showService;
    @Autowired
    public ReservationService reservationService;
    @Autowired
    public ShowSeatRepository showSeatRepository;
    @Autowired
    public ReservationRepository reservationRepository;
    @Autowired
    public ShowUserBookingRepository showUserBookingRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    reservation_seats,
                    idempotency_records,
                    show_seats,
                    show_user_bookings,
                    reservations,
                    shows
                CASCADE
                """);
    }
}
