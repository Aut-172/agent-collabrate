package com.example.agentcollab.dto;

import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.domain.ProjectCiStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {}
    public record CreateProjectRequest(
            @NotBlank String name,
            @NotBlank String repositoryUrl,
            @NotBlank String defaultBranch,
            String gitProvider) {}

    public record AddMemberRequest(@NotNull Long userId) {}

    public record ProjectResponse(Long id, String name, String repositoryUrl, String gitProvider,
                                  String defaultBranch, String latestContextCommit, ProjectCiStatus ciStatus,
                                  Project.Status status, Long createdBy) {
        public static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getName(), p.getRepositoryUrl(), p.getGitProvider(),
                    p.getDefaultBranch(), p.getLatestContextCommit(), p.getCiStatus(), p.getStatus(), p.getCreatedBy());
        }
    }

    public record MemberResponse(Long id, Long userId, String username, ProjectMember.Role projectRole,
                                 ProjectMember.Status status, boolean profileCompleted, int profileVersion,
                                 com.fasterxml.jackson.databind.JsonNode capabilityProfile,
                                 Integer weeklyCapacityPoints, String availability, Instant joinedAt) {}

    public record CapabilityProfileRequest(
            @NotBlank String summary,
            @NotNull List<@NotBlank String> responsibilities,
            @NotNull List<@NotBlank String> skills,
            @NotNull List<@NotBlank String> experience,
            @NotNull List<@NotBlank String> preferredTaskTypes,
            @NotNull List<@NotBlank String> limitations,
            @NotBlank @Size(max = 30) String availability,
            @NotNull @Min(1) @Max(40) Integer weeklyCapacityPoints,
            String notes) {}
}
