package com.paytm.assignment.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateShowRequest(

        @NotBlank(message = "Show name is required")
        String name,

        @NotEmpty(message = "At least one seat is required")
        List<@NotBlank(message = "Seat number cannot be blank") String> seats,

        @JsonProperty("price_paise")
        @Positive(message = "Price must be greater than zero")
        long pricePaise

) {
}