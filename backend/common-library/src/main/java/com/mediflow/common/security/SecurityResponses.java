package com.mediflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Writes security errors from servlet filters, where {@code @RestControllerAdvice} does
 * not apply. Keeps the API envelope identical to controller-level errors.
 */
public final class SecurityResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SecurityResponses() {
    }

    public static void unauthorized(HttpServletResponse response, String message) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, message);
    }

    public static void serviceUnavailable(HttpServletResponse response, String message) throws IOException {
        write(response, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public static void forbidden(HttpServletResponse response, String message) throws IOException {
        write(response, HttpStatus.FORBIDDEN, message);
    }

    private static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), ApiResponse.failure(message, null));
    }
}
