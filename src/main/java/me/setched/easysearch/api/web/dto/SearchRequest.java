package me.setched.easysearch.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchRequest(@NotBlank String query) {
}
