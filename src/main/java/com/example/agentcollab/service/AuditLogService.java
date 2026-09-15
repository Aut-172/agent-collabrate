package com.example.agentcollab.service;

import com.example.agentcollab.domain.AuditLog;
import com.example.agentcollab.dto.AuditLogDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.AuditLogRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Service
public class AuditLogService {
    private final AuditLogRepository logs;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final ObjectMapper mapper;
    public AuditLogService(AuditLogRepository logs, ProjectMemberRepository members, ObjectMapper mapper) {
        this(logs, members, null, mapper);
    }
    @Autowired
    public AuditLogService(AuditLogRepository logs, ProjectMemberRepository members, UserRepository users, ObjectMapper mapper) {
        this.logs = logs; this.members = members; this.users = users; this.mapper = mapper;
    }

    @Transactional
    public AuditLog record(Long actorUserId, Long projectId, String action, String entityType, Long entityId,
                           String requestId, Map<String, ?> details) {
        JsonNode safe = sanitize(details == null ? mapper.createObjectNode() : mapper.valueToTree(details));
        return logs.save(new AuditLog(actorUserId, projectId, action, entityType, entityId,
                requestId == null || requestId.isBlank() ? null : requestId, safe));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDtos.AuditLogResponse> list(Long actorId, Long projectId, String entityType, Long entityId,
                                                    int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "分页参数无效");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditLog> result;
        if (projectId != null) {
            var member = members.findByProjectIdAndUserId(projectId, actorId)
                    .filter(m -> m.getStatus() == com.example.agentcollab.domain.ProjectMember.Status.ACTIVE)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或无权访问"));
            if (member.getProjectRole() != com.example.agentcollab.domain.ProjectMember.Role.LEADER)
                throw new ApiException(HttpStatus.FORBIDDEN, "LEADER_REQUIRED", "需要项目 Leader 权限");
            result = entityType != null && entityId != null
                    ? logs.findByProjectIdAndEntityTypeAndEntityId(projectId, entityType, entityId, pageable)
                    : logs.findByProjectId(projectId, pageable);
        } else {
            List<Long> projectIds = members.findByUserIdAndStatus(actorId, com.example.agentcollab.domain.ProjectMember.Status.ACTIVE)
                    .stream().filter(m -> m.getProjectRole() == com.example.agentcollab.domain.ProjectMember.Role.LEADER)
                    .map(com.example.agentcollab.domain.ProjectMember::getProjectId).toList();
            result = projectIds.isEmpty() ? Page.empty(pageable) : logs.findByProjectIdIn(projectIds, pageable);
        }
        return result.map(log -> AuditLogDtos.AuditLogResponse.from(log,
                users == null || log.getActorUserId() == null ? null : users.findById(log.getActorUserId()).map(u -> u.getUsername()).orElse(null)));
    }

    private JsonNode sanitize(JsonNode node) {
        if (node == null || node.isNull()) return mapper.nullNode();
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> {
                String key = e.getKey();
                if (key.toLowerCase(Locale.ROOT).matches(".*(password|token|secret|api[_-]?key|authorization|credential).*")) return;
                out.set(key, sanitize(e.getValue()));
            });
            return out;
        }
        if (node.isArray()) { var out = mapper.createArrayNode(); node.forEach(v -> out.add(sanitize(v))); return out; }
        if (node.isTextual() && node.textValue().length() > 4000) return mapper.getNodeFactory().textNode(node.textValue().substring(0, 4000));
        return node;
    }
}
