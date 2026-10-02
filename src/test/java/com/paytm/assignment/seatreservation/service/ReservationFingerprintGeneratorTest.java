package com.paytm.assignment.seatreservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationFingerprintGeneratorTest {

    private ReservationFingerprintGenerator fingerprintGenerator;

    @BeforeEach
    void setUp() {
        fingerprintGenerator = new ReservationFingerprintGenerator();
    }

    @Test
    void shouldGenerateSameFingerprintForSameRequest() {

        UUID showId = UUID.randomUUID();
        List<String> seats = List.of("A1", "A2");

        String firstFingerprint =
                fingerprintGenerator.generate(showId, seats);

        String secondFingerprint =
                fingerprintGenerator.generate(showId, seats);

        assertEquals(firstFingerprint, secondFingerprint);
    }

    @Test
    void shouldGenerateDifferentFingerprintForDifferentSeats() {

        UUID showId = UUID.randomUUID();

        String firstFingerprint =
                fingerprintGenerator.generate(
                        showId,
                        List.of("A1", "A2")
                );

        String secondFingerprint =
                fingerprintGenerator.generate(
                        showId,
                        List.of("A1", "A3")
                );

        assertNotEquals(firstFingerprint, secondFingerprint);
    }

    @Test
    void shouldGenerateDifferentFingerprintForDifferentShows() {

        String firstFingerprint =
                fingerprintGenerator.generate(
                        UUID.randomUUID(),
                        List.of("A1", "A2")
                );

        String secondFingerprint =
                fingerprintGenerator.generate(
                        UUID.randomUUID(),
                        List.of("A1", "A2")
                );

        assertNotEquals(firstFingerprint, secondFingerprint);
    }
}