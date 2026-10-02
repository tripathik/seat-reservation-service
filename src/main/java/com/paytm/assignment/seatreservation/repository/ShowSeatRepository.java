package com.paytm.assignment.seatreservation.repository;

import com.paytm.assignment.seatreservation.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT s
       FROM ShowSeat s
       WHERE s.show.id = :showId
         AND s.seatNumber IN :seatNumbers
       ORDER BY s.seatNumber
       """)
    List<ShowSeat> findSeatsForUpdate(
            @Param("showId") UUID showId,
            @Param("seatNumbers") Collection<String> seatNumbers
    );

    @Query("""
       SELECT s
       FROM ShowSeat s
       WHERE s.reservation.id = :reservationId
       ORDER BY s.seatNumber
       """)
    List<ShowSeat> findByReservationId(
            @Param("reservationId") UUID reservationId
    );
}