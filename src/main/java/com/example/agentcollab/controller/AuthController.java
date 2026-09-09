package com.example.agentcollab.controller;

import com.example.agentcollab.dto.AuthDtos;
import com.example.agentcollab.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse initialize(@Valid @RequestBody AuthDtos.InitializeRequest request) {
        return authService.initialize(request.username(), request.password());
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

}
