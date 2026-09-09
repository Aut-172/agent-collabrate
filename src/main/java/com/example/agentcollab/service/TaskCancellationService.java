package com.example.agentcollab.service;

import com.example.agentcollab.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCancellationService {
    private final TaskRepository tasks;

    public TaskCancellationService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional
    public void cancelForWorkflow(Long workflowId) {
        for (var task : tasks.findByWorkflowIdOrderById(workflowId)) task.cancel();
    }
}
