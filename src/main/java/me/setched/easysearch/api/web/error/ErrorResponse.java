package me.setched.easysearch.api.web.error;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body returned by {@link GlobalExceptionHandler}.
 *
 * @param timestamp when the error occurred
 * @param status    the HTTP status code
 * @param error     the HTTP status reason phrase, e.g. {@code "Bad Request"}
 * @param message   a human-readable summary of the error
 * @param details   additional per-field details, e.g. validation failures; empty if not applicable
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, List<String> details) {

    /**
     * Creates an error response with no additional details, timestamped now.
     *
     * @param status  the HTTP status code
     * @param error   the HTTP status reason phrase
     * @param message a human-readable summary of the error
     */
    public ErrorResponse(int status, String error, String message) {
        this(Instant.now(), status, error, message, List.of());
    }

    /**
     * Creates an error response with additional details, timestamped now.
     *
     * @param status  the HTTP status code
     * @param error   the HTTP status reason phrase
     * @param message a human-readable summary of the error
     * @param details additional per-field details
     */
    public ErrorResponse(int status, String error, String message, List<String> details) {
        this(Instant.now(), status, error, message, details);
    }
}
