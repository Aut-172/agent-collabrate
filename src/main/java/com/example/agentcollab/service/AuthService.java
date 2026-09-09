package com.example.agentcollab.service;

import com.example.agentcollab.domain.User;
import com.example.agentcollab.dto.AuthDtos.AuthResponse;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse initialize(String username, String password) {
        User user = userService.initializeLeader(username, password);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        User user = userService.requireByUsername(username);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
        return issue(user);
    }

    private AuthResponse issue(User user) {
        return new AuthResponse(user.getId(), user.getUsername(), jwtService.createToken(user), "Bearer", jwtService.expirationMs());
    }
}
