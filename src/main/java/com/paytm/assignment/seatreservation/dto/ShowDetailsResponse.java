package com.paytm.assignment.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ShowDetailsResponse(

        UUID id,

        String name,

        @JsonProperty("price_paise")
        long pricePaise,

        @JsonProperty("per_user_limit")
        int perUserLimit,

        List<SeatResponse> seats,

        SeatCounts counts
) {
}