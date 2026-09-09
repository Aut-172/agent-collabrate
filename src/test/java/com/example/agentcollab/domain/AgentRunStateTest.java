package com.example.agentcollab.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunStateTest {
    @Test
    void preservesAgentRunLifecycleAndRetryCount() {
        AgentRun run = new AgentRun(1L, AgentRunType.GENERATE_DESIGN, "mock", "model", "summary");
        assertThatThrownBy(() -> run.succeed("invalid")).isInstanceOf(IllegalStateException.class);
        run.start();
        run.recordRetry("TIMEOUT", "temporary");
        run.succeed("generated");

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run.getRetryCount()).isEqualTo(1);
        assertThat(run.getErrorCode()).isNull();
    }

    @Test
    void countsOutboxAttemptsWithoutLosingHistory() {
        OutboxJob job = new OutboxJob(OutboxJobType.AGENT_RUN, 1L);
        job.claim();
        job.retryAt(Instant.now(), "temporary");
        job.claim();
        job.succeed();

        assertThat(job.getStatus()).isEqualTo(OutboxJobStatus.SUCCEEDED);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getErrorMessage()).isNull();
    }
}
