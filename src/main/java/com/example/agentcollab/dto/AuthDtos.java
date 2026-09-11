package com.example.agentcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record InitializeRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 128) String password) {}
    public record CreateUserRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(Long userId, String username, String accessToken, String tokenType, long expiresInMs) {}
    public record MeResponse(Long userId, String username) {}
}
