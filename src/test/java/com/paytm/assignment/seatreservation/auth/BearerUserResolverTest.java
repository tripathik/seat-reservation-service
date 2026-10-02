package com.paytm.assignment.seatreservation.auth;

import com.paytm.assignment.seatreservation.exception.InvalidAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BearerUserResolverTest {

    private BearerUserResolver bearerUserResolver;

    @BeforeEach
    void setUp() {
        bearerUserResolver = new BearerUserResolver();
    }

    @Test
    void shouldResolveUserIdFromValidBearerToken() {

        String userId =
                bearerUserResolver.resolve("Bearer user-123");

        assertEquals("user-123", userId);
    }

    @Test
    void shouldRejectMissingAuthorizationHeader() {

        assertThrows(
                InvalidAuthenticationException.class,
                () -> bearerUserResolver.resolve(null)
        );
    }

    @Test
    void shouldRejectAuthorizationHeaderWithoutBearerScheme() {

        assertThrows(
                InvalidAuthenticationException.class,
                () -> bearerUserResolver.resolve("Basic user-123")
        );
    }

    @Test
    void shouldRejectEmptyBearerToken() {

        assertThrows(
                InvalidAuthenticationException.class,
                () -> bearerUserResolver.resolve("Bearer")
        );
    }

    @Test
    void shouldRejectBlankAuthorizationHeader() {

        assertThrows(
                InvalidAuthenticationException.class,
                () -> bearerUserResolver.resolve("   ")
        );
    }

    @Test
    void shouldRejectBearerTokenContainingOnlyWhitespace() {

        assertThrows(
                InvalidAuthenticationException.class,
                () -> bearerUserResolver.resolve("Bearer    ")
        );
    }

    @Test
    void shouldTrimWhitespaceAroundUserId() {

        String userId =
                bearerUserResolver.resolve("Bearer   user-123   ");

        assertEquals("user-123", userId);
    }
}