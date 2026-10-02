package com.paytm.assignment.seatreservation.auth;

import com.paytm.assignment.seatreservation.exception.InvalidAuthenticationException;
import org.springframework.stereotype.Component;

import static com.paytm.assignment.seatreservation.util.constant.Constants.AUTH_HEADER_REQUIRED;
import static com.paytm.assignment.seatreservation.util.constant.Constants.BEARER_TOKEN_REQUIRED;
import static com.paytm.assignment.seatreservation.util.constant.Constants.BEARER_KEYWORD_REQUIRED;

@Component
public class BearerUserResolver {

    public String resolve(String authorization) {

        if (authorization == null || authorization.isBlank()) {
            throw new InvalidAuthenticationException(
                    AUTH_HEADER_REQUIRED
            );
        }

        if (authorization.equals("Bearer")) {
            throw new InvalidAuthenticationException(
                    BEARER_TOKEN_REQUIRED
            );
        }

        if (!authorization.startsWith("Bearer ")) {
            throw new InvalidAuthenticationException(
                    BEARER_KEYWORD_REQUIRED
            );
        }

        String userId = authorization.substring(7).trim();

        if (userId.isBlank()) {
            throw new InvalidAuthenticationException(
                    BEARER_TOKEN_REQUIRED
            );
        }

        return userId;
    }
}