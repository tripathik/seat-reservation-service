package com.paytm.assignment.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReserveSeatsRequest(

        @NotEmpty(message = "At least one seat is required")
        List<@NotBlank(message = "Seat number cannot be blank") String> seats,

        @JsonProperty("idempotency_key")
        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {
}