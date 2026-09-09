package com.example.agentcollab.service;

import com.example.agentcollab.domain.MemberProfileVersion;
import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.domain.User;
import com.example.agentcollab.dto.ProjectDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.MemberProfileVersionRepository;
import com.example.agentcollab.repository.ProjectMemberRepository;
import com.example.agentcollab.repository.ProjectRepository;
import com.example.agentcollab.repository.UserRepository;
import com.example.agentcollab.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final MemberProfileVersionRepository profileVersions;
    private final UserRepository users;
    private final ProjectAccessService access;
    private final ProfileJsonMapper profileMapper;
    private final UserService userService;
    private final TaskRepository tasks;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members,
                          MemberProfileVersionRepository profileVersions, UserRepository users,
                          ProjectAccessService access, ProfileJsonMapper profileMapper, UserService userService,
                          TaskRepository tasks) {
        this.projects = projects;
        this.members = members;
        this.profileVersions = profileVersions;
        this.users = users;
        this.access = access;
        this.profileMapper = profileMapper;
        this.userService = userService;
        this.tasks = tasks;
    }

    @Transactional
    public Project create(Long actorId, ProjectDtos.CreateProjectRequest request) {
        User actor = userService.require(actorId);
        String provider = request.gitProvider() == null || request.gitProvider().isBlank() ? "github" : request.gitProvider();
        if (!provider.equalsIgnoreCase("github")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GIT_PROVIDER", "MVP 仅支持 GitHub");
        }
        Project project = projects.save(new Project(request.name(), request.repositoryUrl(), provider.toLowerCase(),
                request.defaultBranch(), actor.getId()));
        members.save(new ProjectMember(project.getId(), actor.getId(), ProjectMember.Role.LEADER));
        return project;
    }

    @Transactional(readOnly = true)
    public Project get(Long actorId, Long projectId) {
        access.requireMember(projectId, actorId);
        return projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
    }

    @Transactional(readOnly = true)
    public List<Project> listForUser(Long actorId) {
        return members.findByUserIdAndStatus(actorId, ProjectMember.Status.ACTIVE).stream()
                .map(ProjectMember::getProjectId).map(projects::findById).flatMap(java.util.Optional::stream).toList();
    }

    @Transactional
    public ProjectMember addMember(Long actorId, Long projectId, ProjectDtos.AddMemberRequest request) {
        access.requireLeader(projectId, actorId);
        User user = userService.require(request.userId());
        if (members.findByProjectIdAndUserId(projectId, user.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_MEMBER_EXISTS", "用户已经是项目成员");
        }
        return members.save(new ProjectMember(projectId, user.getId(), ProjectMember.Role.MEMBER));
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> listMembers(Long actorId, Long projectId) {
        access.requireMember(projectId, actorId);
        return members.findByProjectIdAndStatus(projectId, ProjectMember.Status.ACTIVE);
    }

    @Transactional
    public void removeMember(Long actorId, Long projectId, Long userId) {
        access.requireLeader(projectId, actorId);
        if (actorId.equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "LEADER_SELF_REMOVAL_NOT_ALLOWED", "Leader 不能移除自己");
        }
        ProjectMember member = members.findByProjectIdAndUserIdForUpdate(projectId, userId)
                .filter(value -> value.getStatus() == ProjectMember.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_MEMBER_NOT_FOUND", "项目成员不存在"));
        if (tasks.existsOpenTaskForAssignee(projectId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_HAS_OPEN_TASKS", "成员仍负责未完成任务，请先转派");
        }
        member.remove();
        members.save(member);
    }

    @Transactional(readOnly = true)
    public ProjectMember getOwnMember(Long actorId, Long projectId) {
        return access.requireMember(projectId, actorId);
    }

    @Transactional
    public ProjectMember updateOwnProfile(Long actorId, Long projectId, ProjectDtos.CapabilityProfileRequest request) {
        access.requireMember(projectId, actorId);
        ProjectMember member = members.findByProjectIdAndUserIdForUpdate(projectId, actorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "PROJECT_MEMBER_NOT_FOUND", "项目成员不存在"));
        updateProfileInternal(member, request, actorId);
        return member;
    }

    public ProjectDtos.MemberResponse toMemberResponse(ProjectMember member) {
        String username = users.findById(member.getUserId()).map(User::getUsername).orElse("unknown");
        return new ProjectDtos.MemberResponse(member.getId(), member.getUserId(), username,
                member.getProjectRole(), member.getStatus(), member.isProfileCompleted(),
                member.getProfileVersion(), member.getCapabilityProfile(), member.getWeeklyCapacityPoints(),
                member.getAvailability(), member.getJoinedAt());
    }

    private void updateProfileInternal(ProjectMember member, ProjectDtos.CapabilityProfileRequest request, Long changedBy) {
        var profile = profileMapper.toJson(request);
        int nextVersion = member.getProfileVersion() + 1;
        profileVersions.save(new MemberProfileVersion(member.getId(), nextVersion, profile, changedBy));
        member.updateProfile(profile, request.weeklyCapacityPoints(), request.availability());
        members.save(member);
    }
}
