package com.paytm.assignment.seatreservation.controller;

import com.paytm.assignment.seatreservation.auth.BearerUserResolver;
import com.paytm.assignment.seatreservation.dto.ReservationResponse;
import com.paytm.assignment.seatreservation.dto.ReservationResult;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.assignment.seatreservation.service.ReservationAdmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationAdmissionService reservationAdmissionService;
    private final BearerUserResolver bearerUserResolver;

    public ReservationController(
            ReservationAdmissionService reservationAdmissionService,
            BearerUserResolver bearerUserResolver) {

        this.reservationAdmissionService = reservationAdmissionService;
        this.bearerUserResolver = bearerUserResolver;
    }

    @PostMapping("/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReserveSeatsRequest request) {

        String userId = bearerUserResolver.resolve(authorization);

        ReservationResult result =
                reservationAdmissionService.reserve(showId, userId, request);

        if (result.replay()) {
            return ResponseEntity.ok(result.response());
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result.response());
    }
}