package com.example.agentcollab.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {}
    public record InitializeRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record CreateUserRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(Long userId, String username, String accessToken, String tokenType, long expiresInMs) {}
    public record MeResponse(Long userId, String username) {}
}
