package me.setched.easysearch.api.web.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(Instant timestamp, int status, String error, String message, List<String> details) {

    public ErrorResponse(int status, String error, String message) {
        this(Instant.now(), status, error, message, List.of());
    }

    public ErrorResponse(int status, String error, String message, List<String> details) {
        this(Instant.now(), status, error, message, details);
    }
}
