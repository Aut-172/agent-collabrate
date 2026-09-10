package com.example.agentcollab.service;

import com.example.agentcollab.repository.TaskRepository;
import com.example.agentcollab.repository.TaskPackageRepository;
import com.example.agentcollab.domain.TaskPackageStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCancellationService {
    private final TaskRepository tasks;
    private final TaskPackageRepository packages;

    public TaskCancellationService(TaskRepository tasks, TaskPackageRepository packages) {
        this.tasks = tasks;
        this.packages = packages;
    }

    @Transactional
    public void cancelForWorkflow(Long workflowId) {
        for (var task : tasks.findByWorkflowIdOrderById(workflowId)) {
            task.cancel();
            packages.findByTaskIdAndStatus(task.getId(), TaskPackageStatus.CURRENT)
                    .ifPresent(taskPackage -> taskPackage.retire());
        }
    }
}
