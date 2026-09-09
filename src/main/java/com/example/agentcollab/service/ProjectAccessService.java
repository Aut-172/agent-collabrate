package com.example.agentcollab.service;

import com.example.agentcollab.domain.ProjectMember;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.ProjectMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {
    private final ProjectMemberRepository members;

    public ProjectAccessService(ProjectMemberRepository members) { this.members = members; }

    @Transactional(readOnly = true)
    public ProjectMember requireMember(Long projectId, Long userId) {
        return members.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> m.getStatus() == ProjectMember.Status.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或无权访问"));
    }

    @Transactional(readOnly = true)
    public ProjectMember requireLeader(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (member.getProjectRole() != ProjectMember.Role.LEADER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LEADER_REQUIRED", "需要项目 Leader 权限");
        }
        return member;
    }
}
