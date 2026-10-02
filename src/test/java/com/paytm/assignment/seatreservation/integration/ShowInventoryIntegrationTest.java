package com.paytm.assignment.seatreservation.integration;

import com.paytm.assignment.seatreservation.dto.CreateShowRequest;
import com.paytm.assignment.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.assignment.seatreservation.dto.ShowDetailsResponse;
import com.paytm.assignment.seatreservation.dto.ShowResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShowInventoryIntegrationTest extends AbstractIntegrationTest {
    @Test
    void shouldReconcileShowInventoryCounts() {

        ShowResponse show = showService.createShow(
                new CreateShowRequest(
                        "inventory-reconciliation-show",
                        List.of("M1", "M2", "M3", "M4", "M5", "M6"),
                        25000
                )
        );

        reservationService.reserve(
                show.id(),
                "inventory-user-1",
                new ReserveSeatsRequest(
                        List.of("M1", "M2"),
                        "inventory-key-1"
                )
        );

        reservationService.reserve(
                show.id(),
                "inventory-user-2",
                new ReserveSeatsRequest(
                        List.of("M3"),
                        "inventory-key-2"
                )
        );

        ShowDetailsResponse details =
                showService.getShowDetails(show.id());

        assertEquals(6, details.counts().total());
        assertEquals(3, details.counts().available());
        assertEquals(0, details.counts().held());
        assertEquals(3, details.counts().confirmed());

        assertEquals(
                details.counts().total(),
                details.counts().available()
                        + details.counts().held()
                        + details.counts().confirmed()
        );
    }
}
