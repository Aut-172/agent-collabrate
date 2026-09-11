package com.example.agentcollab.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, length = 50, updatable = false) private String type;
    @Column(name = "entity_type", length = 50, updatable = false) private String entityType;
    @Column(name = "entity_id", updatable = false) private Long entityId;
    @Column(nullable = false, length = 200, updatable = false) private String title;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String content;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Notification() {}

    public Notification(Long userId, String type, String entityType, Long entityId, String title, String content) {
        this.userId = userId; this.type = type; this.entityType = entityType; this.entityId = entityId;
        this.title = title; this.content = content; this.createdAt = Instant.now();
    }

    public void markRead() { if (readAt == null) readAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
