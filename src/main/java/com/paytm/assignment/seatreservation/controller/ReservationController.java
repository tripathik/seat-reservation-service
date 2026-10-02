package com.paytm.assignment.seatreservation.controller;

import com.paytm.assignment.seatreservation.dto.ReservationResult;
import com.paytm.assignment.seatreservation.exception.InvalidAuthenticationException;
import com.paytm.assignment.seatreservation.dto.ReservationResponse;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.assignment.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ReserveSeatsRequest request) {

        String userId = extractUserId(authorization);

        ReservationResult result =
                reservationService.reserve(showId, userId, request);

        if(result.replay()){
            return ResponseEntity.ok(result.response());
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result.response());
    }

    private String extractUserId(String authorization) {

        if (authorization == null || authorization.isBlank()) {
            throw new InvalidAuthenticationException(
                    "Authorization header is required"
            );
        }

        if (authorization.equals("Bearer")) {
            throw new InvalidAuthenticationException(
                    "Bearer token cannot be empty"
            );
        }

        if (!authorization.startsWith("Bearer ")) {
            throw new InvalidAuthenticationException(
                    "Authorization header must use Bearer authentication"
            );
        }

        String userId = authorization.substring(7).trim();

        if (userId.isBlank()) {
            throw new InvalidAuthenticationException(
                    "Bearer token cannot be empty"
            );
        }

        return userId;
    }
}