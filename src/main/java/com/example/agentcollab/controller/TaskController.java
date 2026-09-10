package com.example.agentcollab.controller;

import com.example.agentcollab.dto.TaskDtos;
import com.example.agentcollab.dto.TaskDeliveryDtos;
import com.example.agentcollab.dto.DeliveryEvidenceDtos;
import com.example.agentcollab.dto.TaskBlockerDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.TaskService;
import com.example.agentcollab.service.TaskDeliveryService;
import com.example.agentcollab.service.DeliveryEvidenceService;
import com.example.agentcollab.service.TaskBlockerService;
import com.example.agentcollab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService tasks;
    private final UserService users;
    private final TaskDeliveryService deliveries;
    private final DeliveryEvidenceService evidence;
    private final TaskBlockerService blockers;

    public TaskController(TaskService tasks, UserService users, TaskDeliveryService deliveries,
                          DeliveryEvidenceService evidence, TaskBlockerService blockers) {
        this.tasks = tasks;
        this.users = users;
        this.deliveries = deliveries;
        this.evidence = evidence;
        this.blockers = blockers;
    }

    @GetMapping("/{taskId}")
    public TaskDtos.TaskResponse get(@PathVariable Long taskId) {
        return tasks.get(currentUserId(), taskId);
    }

    @PutMapping("/{taskId}/assignee")
    public TaskDtos.TaskResponse reassign(@PathVariable Long taskId,
                                          @Valid @RequestBody TaskDtos.ReassignRequest request) {
        return tasks.reassign(currentUserId(), taskId, request);
    }

    @PostMapping("/{taskId}/delivery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskDeliveryDtos.DeliveryResponse submitDelivery(
            @PathVariable Long taskId, @Valid @RequestBody TaskDeliveryDtos.SubmitRequest request) {
        return deliveries.submit(currentUserId(), taskId, request);
    }

    @GetMapping("/{taskId}/deliveries")
    public List<TaskDeliveryDtos.DeliveryResponse> deliveries(@PathVariable Long taskId) {
        return deliveries.list(currentUserId(), taskId);
    }

    @GetMapping("/{taskId}/git-operations")
    public List<DeliveryEvidenceDtos.GitOperationResponse> gitOperations(@PathVariable Long taskId) {
        return evidence.listGitOperations(currentUserId(), taskId);
    }

    @GetMapping("/{taskId}/ci-runs")
    public List<DeliveryEvidenceDtos.CiRunResponse> ciRuns(@PathVariable Long taskId) {
        return evidence.listCiRuns(currentUserId(), taskId);
    }

    @PostMapping("/{taskId}/block")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskBlockerDtos.BlockerResponse reportBlocker(
            @PathVariable Long taskId, @Valid @RequestBody TaskBlockerDtos.ReportRequest request) {
        return blockers.report(currentUserId(), taskId, request);
    }

    @GetMapping("/{taskId}/blockers")
    public List<TaskBlockerDtos.BlockerResponse> blockers(@PathVariable Long taskId) {
        return blockers.list(currentUserId(), taskId);
    }

    @PostMapping("/{taskId}/blockers/{blockerId}/resolve")
    public TaskBlockerDtos.BlockerResponse resolveBlocker(
            @PathVariable Long taskId, @PathVariable Long blockerId,
            @Valid @RequestBody TaskBlockerDtos.CloseRequest request) {
        return blockers.resolve(currentUserId(), taskId, blockerId, request);
    }

    @PostMapping("/{taskId}/blockers/{blockerId}/cancel")
    public TaskBlockerDtos.BlockerResponse cancelBlocker(
            @PathVariable Long taskId, @PathVariable Long blockerId,
            @Valid @RequestBody TaskBlockerDtos.CloseRequest request) {
        return blockers.cancel(currentUserId(), taskId, blockerId, request);
    }

    private Long currentUserId() {
        return users.requireByUsername(CurrentUser.username()).getId();
    }
}
