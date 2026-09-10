package com.example.agentcollab.client;

import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.TaskDelivery;

public interface GitProviderClient {
    GitValidationResult validate(Project project, TaskDelivery delivery);

    record GitValidationResult(boolean repositoryMatches, boolean commitExists, boolean branchMatches,
                               boolean pullRequestMatches, String verifiedCommitSha,
                               String pullRequestHeadSha, String externalId, String errorMessage) {
        public boolean isValid() {
            return repositoryMatches && commitExists && branchMatches && pullRequestMatches;
        }
    }
}
