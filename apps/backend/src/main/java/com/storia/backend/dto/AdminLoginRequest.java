package com.storia.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank String password) {
}
