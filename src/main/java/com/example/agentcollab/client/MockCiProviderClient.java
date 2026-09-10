package com.example.agentcollab.client;

import com.example.agentcollab.domain.CiRun;
import com.example.agentcollab.domain.CiRunStatus;
import com.example.agentcollab.domain.Project;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "mock-provider"})
public class MockCiProviderClient implements CiProviderClient {
    @Override
    public CiProviderResult sync(Project project, CiRun run) {
        String externalId = run.getExternalId() == null ? "mock-ci:" + run.getCommitSha() : run.getExternalId();
        return new CiProviderResult(externalId, run.getCommitSha(), CiRunStatus.PASSED, "SUCCESS",
                "https://ci.example.invalid/runs/" + externalId, true, true);
    }
}
