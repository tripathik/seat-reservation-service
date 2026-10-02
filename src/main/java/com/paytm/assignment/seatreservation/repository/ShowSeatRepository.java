package com.paytm.assignment.seatreservation.repository;

import com.paytm.assignment.seatreservation.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {
}