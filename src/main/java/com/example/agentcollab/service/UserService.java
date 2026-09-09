package com.example.agentcollab.service;

import com.example.agentcollab.domain.User;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User initializeLeader(String username, String password) {
        if (users.count() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INITIALIZATION_ALREADY_COMPLETED", "系统已完成初始化");
        }
        return create(username, password);
    }

    @Transactional
    public User create(String username, String password) {
        if (users.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码长度至少为 8 位");
        }
        return users.save(new User(username, passwordEncoder.encode(password)));
    }

    @Transactional(readOnly = true)
    public User require(Long id) {
        return users.findById(id).filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    @Transactional(readOnly = true)
    public User requireByUsername(String username) {
        return users.findByUsername(username).filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误"));
    }
}
