package com.example.agentcollab.dto;

import com.example.agentcollab.domain.Notification;
import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {}
    public record NotificationResponse(Long id, String type, String entityType, Long entityId,
                                        String title, String content, Instant readAt, Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getEntityType(), n.getEntityId(),
                    n.getTitle(), n.getContent(), n.getReadAt(), n.getCreatedAt());
        }
    }
}
