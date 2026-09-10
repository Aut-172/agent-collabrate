package com.example.agentcollab.service;

import com.example.agentcollab.dto.DeliveryEvidenceDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.CiRunRepository;
import com.example.agentcollab.repository.GitOperationRepository;
import com.example.agentcollab.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeliveryEvidenceService {
    private final TaskRepository tasks;
    private final WorkflowService workflows;
    private final GitOperationRepository gitOperations;
    private final CiRunRepository ciRuns;

    public DeliveryEvidenceService(TaskRepository tasks, WorkflowService workflows,
                                   GitOperationRepository gitOperations, CiRunRepository ciRuns) {
        this.tasks = tasks;
        this.workflows = workflows;
        this.gitOperations = gitOperations;
        this.ciRuns = ciRuns;
    }

    @Transactional(readOnly = true)
    public List<DeliveryEvidenceDtos.GitOperationResponse> listGitOperations(Long actorId, Long taskId) {
        requireTaskAccess(actorId, taskId);
        return gitOperations.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(DeliveryEvidenceDtos.GitOperationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryEvidenceDtos.CiRunResponse> listCiRuns(Long actorId, Long taskId) {
        requireTaskAccess(actorId, taskId);
        return ciRuns.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(DeliveryEvidenceDtos.CiRunResponse::from).toList();
    }

    private void requireTaskAccess(Long actorId, Long taskId) {
        var task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task 不存在"));
        workflows.get(actorId, task.getWorkflowId());
    }
}
