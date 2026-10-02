package com.paytm.assignment.seatreservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "show_user_bookings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_show_user_booking",
                        columnNames = {"show_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowUserBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "confirmed_seat_count", nullable = false)
    private int confirmedSeatCount;

    public ShowUserBooking(Show show, String userId) {
        this.show = show;
        this.userId = userId;
        this.confirmedSeatCount = 0;
    }

    public void addConfirmedSeats(int count) {
        this.confirmedSeatCount += count;
    }

    public void removeConfirmedSeats(int count) {
        this.confirmedSeatCount -= count;
    }
}