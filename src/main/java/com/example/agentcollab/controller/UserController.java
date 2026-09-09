package com.example.agentcollab.controller;

import com.example.agentcollab.dto.AuthDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/me")
    public AuthDtos.MeResponse me() {
        var user = userService.requireByUsername(CurrentUser.username());
        return new AuthDtos.MeResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.MeResponse create(@Valid @RequestBody AuthDtos.CreateUserRequest request) {
        var user = userService.create(request.username(), request.password());
        return new AuthDtos.MeResponse(user.getId(), user.getUsername());
    }
}
