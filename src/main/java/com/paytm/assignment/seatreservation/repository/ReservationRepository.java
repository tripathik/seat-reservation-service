package com.paytm.assignment.seatreservation.repository;

import com.paytm.assignment.seatreservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID> {
}