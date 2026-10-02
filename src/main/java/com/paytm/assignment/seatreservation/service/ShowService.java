package com.paytm.assignment.seatreservation.service;

import com.paytm.assignment.seatreservation.dto.CreateShowRequest;
import com.paytm.assignment.seatreservation.dto.SeatResponse;
import com.paytm.assignment.seatreservation.dto.ShowResponse;
import com.paytm.assignment.seatreservation.entity.Show;
import com.paytm.assignment.seatreservation.entity.ShowSeat;
import com.paytm.assignment.seatreservation.exception.DuplicateSeatException;
import com.paytm.assignment.seatreservation.repository.ShowRepository;
import com.paytm.assignment.seatreservation.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ShowService {

    private static final int DEFAULT_PER_USER_LIMIT = 4;

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

    private void validateUniqueSeats(List<String> seats) {

        Set<String> uniqueSeats = new HashSet<>(seats);

        if (uniqueSeats.size() != seats.size()) {
            throw new DuplicateSeatException(
                    "Duplicate seat numbers are not allowed"
            );
        }
    }
}