package com.example.agentcollab.client;

import com.example.agentcollab.domain.CiRun;
import com.example.agentcollab.domain.CiRunStatus;
import com.example.agentcollab.domain.Project;

public interface CiProviderClient {
    CiProviderResult sync(Project project, CiRun run);

    record CiProviderResult(String externalId, String headSha, CiRunStatus status, String conclusion,
                            String detailsUrl, boolean configurationPresent,
                            boolean configurationRecognized) {}
}
