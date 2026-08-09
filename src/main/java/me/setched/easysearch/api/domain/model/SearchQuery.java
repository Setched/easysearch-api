package me.setched.easysearch.api.domain.model;

/**
 * A free-text product search query, as entered by the caller.
 *
 * @param query the raw search text
 */
public record SearchQuery(String query) {
}