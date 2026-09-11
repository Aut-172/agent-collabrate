package com.example.agentcollab.service;

import com.example.agentcollab.domain.AuditLog;
import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.repository.AuditLogRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock AuditLogRepository logs;
    @Mock ProjectMemberRepository members;

    @Test
    void recordRemovesSensitiveKeysAndTruncatesLongText() {
        AuditLogService service = new AuditLogService(logs, members, new ObjectMapper());
        when(logs.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));
        AuditLog saved = service.record(1L, 2L, "TEST", "TASK", 3L, null,
                Map.of("token", "secret", "nested", Map.of("password", "pw", "ok", "yes"), "ok", "yes"));
        assertThat(saved.getDetailsJson().has("token")).isFalse();
        assertThat(saved.getDetailsJson().path("nested").has("password")).isFalse();
        assertThat(saved.getDetailsJson().path("nested").path("ok").asText()).isEqualTo("yes");
    }

    @Test
    void listRequiresProjectMembership() {
        AuditLogService service = new AuditLogService(logs, members, new ObjectMapper());
        when(members.findByProjectIdAndUserId(9L, 7L)).thenReturn(java.util.Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.list(7L, 9L, null, null, 0, 50))
                .hasMessageContaining("项目不存在或无权访问");
        verifyNoInteractions(logs);
    }
}
