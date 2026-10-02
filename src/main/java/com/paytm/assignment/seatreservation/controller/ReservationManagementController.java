package com.paytm.assignment.seatreservation.controller;

import com.paytm.assignment.seatreservation.auth.BearerUserResolver;
import com.paytm.assignment.seatreservation.dto.ReservationResponse;
import com.paytm.assignment.seatreservation.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class ReservationManagementController {

    private final ReservationService reservationService;
    private final BearerUserResolver bearerUserResolver;

    public ReservationManagementController(
            ReservationService reservationService,
            BearerUserResolver bearerUserResolver) {

        this.reservationService = reservationService;
        this.bearerUserResolver = bearerUserResolver;
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID reservationId,
                                                      @RequestHeader(
                                                              value = "Authorization",
                                                              required = false
                                                      ) String authorization) {

        String userId = bearerUserResolver.resolve(authorization);

        ReservationResponse response =
                reservationService.cancel(reservationId, userId);

        return ResponseEntity.ok(response);
    }

}
