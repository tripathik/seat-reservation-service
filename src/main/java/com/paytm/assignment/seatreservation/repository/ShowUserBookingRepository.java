package com.paytm.assignment.seatreservation.repository;

import com.paytm.assignment.seatreservation.entity.ShowUserBooking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShowUserBookingRepository
        extends JpaRepository<ShowUserBooking, UUID> {

    @Modifying
    @Query(
            value = """
                INSERT INTO show_user_bookings
                    (id, show_id, user_id, confirmed_seat_count)
                VALUES
                    (gen_random_uuid(), :showId, :userId, 0)
                ON CONFLICT (show_id, user_id) DO NOTHING
                """,
            nativeQuery = true
    )
    void createIfAbsent(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT b
       FROM ShowUserBooking b
       WHERE b.show.id = :showId
         AND b.userId = :userId
       """)
    Optional<ShowUserBooking> findForUpdate(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    Optional<ShowUserBooking> findByShowIdAndUserId(
            UUID showId,
            String userId
    );
}