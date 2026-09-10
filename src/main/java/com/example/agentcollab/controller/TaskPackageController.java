package com.example.agentcollab.controller;

import com.example.agentcollab.dto.TaskPackageDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.TaskPackageService;
import com.example.agentcollab.service.TaskPackageConfirmationService;
import com.example.agentcollab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks/{taskId}/packages")
public class TaskPackageController {
    private final TaskPackageService packages;
    private final UserService users;
    private final TaskPackageConfirmationService confirmations;
    public TaskPackageController(TaskPackageService packages, UserService users,
                                 TaskPackageConfirmationService confirmations) {
        this.packages = packages; this.users = users; this.confirmations = confirmations;
    }
    @GetMapping("/current") public TaskPackageDtos.PackageResponse current(@PathVariable Long taskId) { return TaskPackageDtos.PackageResponse.from(packages.current(userId(), taskId)); }
    @GetMapping("/{version}") public TaskPackageDtos.PackageResponse version(@PathVariable Long taskId, @PathVariable int version) { return TaskPackageDtos.PackageResponse.from(packages.version(userId(), taskId, version)); }
    @GetMapping("/diff") public TaskPackageDtos.PackageDiffResponse diff(@PathVariable Long taskId, @RequestParam int from, @RequestParam int to) { return packages.diff(userId(), taskId, from, to); }
    @PostMapping("/{version}/confirm")
    public TaskPackageDtos.ConfirmationResponse confirm(@PathVariable Long taskId, @PathVariable int version,
                                                        @Valid @RequestBody TaskPackageDtos.ConfirmRequest request) {
        return confirmations.confirm(userId(), taskId, version, request);
    }
    private Long userId() { return users.requireByUsername(CurrentUser.username()).getId(); }
}
