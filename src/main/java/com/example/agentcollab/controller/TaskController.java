package com.example.agentcollab.controller;

import com.example.agentcollab.dto.TaskDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.TaskService;
import com.example.agentcollab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService tasks;
    private final UserService users;

    public TaskController(TaskService tasks, UserService users) {
        this.tasks = tasks;
        this.users = users;
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

    private Long currentUserId() {
        return users.requireByUsername(CurrentUser.username()).getId();
    }
}
