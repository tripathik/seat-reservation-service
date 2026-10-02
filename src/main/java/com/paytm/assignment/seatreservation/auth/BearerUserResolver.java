package com.paytm.assignment.seatreservation.auth;

import com.paytm.assignment.seatreservation.exception.InvalidAuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class BearerUserResolver {

    public String resolve(String authorization) {

        if (authorization == null || authorization.isBlank()) {
            throw new InvalidAuthenticationException(
                    "Authorization header is required"
            );
        }

        if (authorization.equals("Bearer")) {
            throw new InvalidAuthenticationException(
                    "Bearer token cannot be empty"
            );
        }

        if (!authorization.startsWith("Bearer ")) {
            throw new InvalidAuthenticationException(
                    "Authorization header must use Bearer authentication"
            );
        }

        String userId = authorization.substring(7).trim();

        if (userId.isBlank()) {
            throw new InvalidAuthenticationException(
                    "Bearer token cannot be empty"
            );
        }

        return userId;
    }
}