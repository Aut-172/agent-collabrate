package com.example.agentcollab.dto;

import com.example.agentcollab.domain.TaskStatus;
import java.time.Instant;
import java.util.List;

public final class WorkflowBoardDtos {
    private WorkflowBoardDtos() {}
    public record BoardResponse(Long workflowId, String workflowTitle, List<BoardColumn> columns) {}
    public record BoardColumn(String key, String label, List<TaskCard> cards) {}
    public record TaskCard(Long id, String taskKey, String externalKey, String title, TaskStatus status,
                           Assignee assignee, Long workflowId, String workflowTitle,
                           Integer currentPackageVersion, String branchName, String commitSha,
                           String pullRequestUrl, String gitStatus, String ciStatus,
                           String ciDetailsUrl, Instant updatedAt) {}
    public record Assignee(Long userId, String username) {}
}
