package com.example.agentcollab.controller;

import com.example.agentcollab.dto.ProjectDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.ProjectService;
import com.example.agentcollab.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final UserService userService;

    public ProjectController(ProjectService projectService, UserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDtos.ProjectResponse create(@Valid @RequestBody ProjectDtos.CreateProjectRequest request) {
        return ProjectDtos.ProjectResponse.from(projectService.create(currentUserId(), request));
    }

    @GetMapping
    public List<ProjectDtos.ProjectResponse> list() {
        return projectService.listForUser(currentUserId()).stream().map(ProjectDtos.ProjectResponse::from).toList();
    }

    @GetMapping("/{projectId}")
    public ProjectDtos.ProjectResponse get(@PathVariable Long projectId) {
        return ProjectDtos.ProjectResponse.from(projectService.get(currentUserId(), projectId));
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDtos.MemberResponse addMember(@PathVariable Long projectId,
                                                 @Valid @RequestBody ProjectDtos.AddMemberRequest request) {
        return projectService.toMemberResponse(projectService.addMember(currentUserId(), projectId, request));
    }

    @GetMapping("/{projectId}/members")
    public List<ProjectDtos.MemberResponse> listMembers(@PathVariable Long projectId) {
        return projectService.listMembers(currentUserId(), projectId).stream().map(projectService::toMemberResponse).toList();
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(currentUserId(), projectId, userId);
    }

    @GetMapping("/{projectId}/members/me/profile")
    public ProjectDtos.MemberResponse ownProfile(@PathVariable Long projectId) {
        return projectService.toMemberResponse(projectService.getOwnMember(currentUserId(), projectId));
    }

    @PutMapping("/{projectId}/members/me/profile")
    public ProjectDtos.MemberResponse updateOwnProfile(@PathVariable Long projectId,
                                                        @Valid @RequestBody ProjectDtos.CapabilityProfileRequest request) {
        return projectService.toMemberResponse(projectService.updateOwnProfile(currentUserId(), projectId, request));
    }

    private Long currentUserId() {
        return userService.requireByUsername(CurrentUser.username()).getId();
    }
}
