package com.example.agentcollab.controller;

import com.example.agentcollab.dto.TaskPackageDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.TaskPackageService;
import com.example.agentcollab.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks/{taskId}/packages")
public class TaskPackageController {
    private final TaskPackageService packages;
    private final UserService users;
    public TaskPackageController(TaskPackageService packages, UserService users) { this.packages = packages; this.users = users; }
    @GetMapping("/current") public TaskPackageDtos.PackageResponse current(@PathVariable Long taskId) { return TaskPackageDtos.PackageResponse.from(packages.current(userId(), taskId)); }
    @GetMapping("/{version}") public TaskPackageDtos.PackageResponse version(@PathVariable Long taskId, @PathVariable int version) { return TaskPackageDtos.PackageResponse.from(packages.version(userId(), taskId, version)); }
    @GetMapping("/diff") public TaskPackageDtos.PackageDiffResponse diff(@PathVariable Long taskId, @RequestParam int from, @RequestParam int to) { return packages.diff(userId(), taskId, from, to); }
    private Long userId() { return users.requireByUsername(CurrentUser.username()).getId(); }
}
