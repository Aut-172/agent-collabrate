package com.example.agentcollab.client;

import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.TaskDelivery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "mock-provider"})
public class MockGitProviderClient implements GitProviderClient {
    @Override
    public GitValidationResult validate(Project project, TaskDelivery delivery) {
        boolean validSha = delivery.getCommitSha().matches("[a-f0-9]{7,64}");
        boolean validBranch = delivery.getBranchName() != null && !delivery.getBranchName().equals(project.getDefaultBranch());
        boolean validPr = delivery.getPullRequestUrl() == null || delivery.getPullRequestUrl().startsWith("https://");
        return new GitValidationResult(true, validSha, validBranch, validPr,
                "mock-git:" + delivery.getCommitSha(), validSha ? null : "Commit 不存在或 SHA 格式无效");
    }
}
