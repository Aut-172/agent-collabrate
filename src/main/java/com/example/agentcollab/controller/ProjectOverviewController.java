package com.example.agentcollab.controller;

import com.example.agentcollab.dto.ProjectOverviewDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.ProjectOverviewService;
import com.example.agentcollab.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/projects/{projectId}/overview-stats")
public class ProjectOverviewController {
    private final ProjectOverviewService overview;
    private final UserService users;

    public ProjectOverviewController(ProjectOverviewService overview, UserService users) {
        this.overview = overview;
        this.users = users;
    }

    @GetMapping
    public ProjectOverviewDtos.OverviewResponse get(
            @PathVariable Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return overview.get(currentUserId(), projectId, from, to);
    }

    private Long currentUserId() {
        return users.requireByUsername(CurrentUser.username()).getId();
    }
}
