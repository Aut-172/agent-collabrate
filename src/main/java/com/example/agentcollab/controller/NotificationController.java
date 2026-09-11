package com.example.agentcollab.controller;

import com.example.agentcollab.dto.NotificationDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.NotificationService;
import com.example.agentcollab.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final UserService users;
    public NotificationController(NotificationService notifications, UserService users) {
        this.notifications = notifications; this.users = users;
    }
    @GetMapping
    public List<NotificationDtos.NotificationResponse> list() { return notifications.list(currentUserId()); }
    @PostMapping("/{id}/read")
    public NotificationDtos.NotificationResponse markRead(@PathVariable Long id) {
        return notifications.markRead(currentUserId(), id);
    }
    private Long currentUserId() { return users.requireByUsername(CurrentUser.username()).getId(); }
}
