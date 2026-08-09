package me.setched.easysearch.api.web.dto;

/**
 * Response body for the health-check endpoint.
 *
 * @param status the health status, e.g. {@code "ok"}
 */
public record PingResponse(String status) {
}
