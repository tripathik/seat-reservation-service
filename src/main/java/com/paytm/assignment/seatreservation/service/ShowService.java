package com.paytm.assignment.seatreservation.service;

import com.paytm.assignment.seatreservation.dto.*;
import com.paytm.assignment.seatreservation.entity.SeatStatus;
import com.paytm.assignment.seatreservation.entity.Show;
import com.paytm.assignment.seatreservation.entity.ShowSeat;
import com.paytm.assignment.seatreservation.exception.DuplicateSeatException;
import com.paytm.assignment.seatreservation.exception.ShowNotFoundException;
import com.paytm.assignment.seatreservation.repository.ShowRepository;
import com.paytm.assignment.seatreservation.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.paytm.assignment.seatreservation.util.constant.Constants.*;

@Service
public class ShowService {
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowService(
            ShowRepository showRepository,
            ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {

        validateUniqueSeats(request.seats());

        Show show = new Show(
                request.name(),
                request.pricePaise(),
                DEFAULT_PER_USER_LIMIT
        );

        Show savedShow = showRepository.save(show);

        List<ShowSeat> seats = request.seats()
                .stream()
                .map(seatNumber -> new ShowSeat(savedShow, seatNumber))
                .toList();

        showSeatRepository.saveAll(seats);

        List<SeatResponse> seatResponses = seats.stream()
                .map(seat -> new SeatResponse(
                        seat.getSeatNumber(),
                        seat.getStatus().name().toLowerCase()
                ))
                .toList();

        return new ShowResponse(
                savedShow.getId(),
                savedShow.getName(),
                savedShow.getPricePaise(),
                savedShow.getPerUserLimit(),
                seatResponses
        );
    }

    @Transactional(readOnly = true)
    public ShowDetailsResponse getShowDetails(UUID showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new ShowNotFoundException(
                                SHOW_NOT_FOUND.formatted(showId)
                        ));

        List<ShowSeat> showSeats =
                showSeatRepository.findByShowIdOrderBySeatNumber(showId);

        long available = showSeats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .count();

        long confirmed = showSeats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.CONFIRMED)
                .count();

        long held = 0;
        long total = showSeats.size();

        if (available + held + confirmed != total) {
            throw new IllegalStateException(
                    SEAT_INVENTORY_VIOLATED.formatted(showId)
            );
        }

        List<SeatResponse> seats = showSeats.stream()
                .map(seat -> new SeatResponse(
                        seat.getSeatNumber(),
                        seat.getStatus().name().toLowerCase()
                ))
                .toList();

        SeatCounts counts = new SeatCounts(
                available,
                held,
                confirmed,
                total
        );

        return new ShowDetailsResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                seats,
                counts
        );
    }

    private void validateUniqueSeats(List<String> seats) {

        Set<String> uniqueSeats = new HashSet<>(seats);

        if (uniqueSeats.size() != seats.size()) {
            throw new DuplicateSeatException(DUPLICATE_SEATS_NOT_ALLOWED);
        }
    }
}