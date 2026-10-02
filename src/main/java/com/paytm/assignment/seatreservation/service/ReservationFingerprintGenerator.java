package com.paytm.assignment.seatreservation.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.paytm.assignment.seatreservation.util.constant.Constants.*;

@Component
public class ReservationFingerprintGenerator {

    public String generate(UUID showId, List<String> seats) {

        String canonicalRequest =
                showId + "|" + String.join(",", seats);

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    SHA_256_ALGO_UNAVAILABLE,
                    exception
            );
        }
    }
}