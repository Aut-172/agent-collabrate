package com.example.agentcollab.service;

import com.example.agentcollab.domain.Notification;
import com.example.agentcollab.dto.NotificationDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    public NotificationService(NotificationRepository notifications) { this.notifications = notifications; }

    @Transactional(readOnly = true)
    public List<NotificationDtos.NotificationResponse> list(Long userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDtos.NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(Long userId, Long id) {
        Notification notification = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在"));
        notification.markRead();
        return NotificationDtos.NotificationResponse.from(notifications.save(notification));
    }

    @Transactional
    public Notification notifyUser(Long userId, String type, String entityType, Long entityId,
                                   String title, String content) {
        return notifications.save(new Notification(userId, type, entityType, entityId, title, content));
    }

    @Transactional
    public void notifyUsers(Collection<Long> userIds, String type, String entityType, Long entityId,
                            String title, String content) {
        userIds.stream().distinct().forEach(id -> notifyUser(id, type, entityType, entityId, title, content));
    }
}
