package com.paytm.assignment.seatreservation.controller;

import com.paytm.assignment.seatreservation.dto.CreateShowRequest;
import com.paytm.assignment.seatreservation.dto.ShowDetailsResponse;
import com.paytm.assignment.seatreservation.dto.ShowResponse;
import com.paytm.assignment.seatreservation.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(
            @Valid @RequestBody CreateShowRequest request) {

        ShowResponse response = showService.createShow(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{showId}")
    public ResponseEntity<ShowDetailsResponse> getShow(
            @PathVariable UUID showId) {

        ShowDetailsResponse response =
                showService.getShowDetails(showId);

        return ResponseEntity.ok(response);
    }
}