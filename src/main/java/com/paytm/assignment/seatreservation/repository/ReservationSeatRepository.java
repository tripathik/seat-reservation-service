package com.paytm.assignment.seatreservation.repository;

import com.paytm.assignment.seatreservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository
        extends JpaRepository<ReservationSeat, UUID> {

    List<ReservationSeat> findByReservationIdOrderBySeatNumber(
            UUID reservationId
    );
}