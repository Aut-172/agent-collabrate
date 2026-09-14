package com.example.agentcollab.service;

import com.example.agentcollab.client.AgentProviderClient;
import com.example.agentcollab.client.AgentProviderException;
import com.example.agentcollab.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
public class AgentRunWorker {
    private static final Logger log = LoggerFactory.getLogger(AgentRunWorker.class);

    private final AgentRunExecutionService executions;
    private final AgentRequestFactory requests;
    private final AgentOutputValidator outputValidator;
    private final AgentProviderClient provider;
    private final AgentCallRecordService callRecords;
    private final Executor executor;
    private final int concurrency;

    @Autowired
    public AgentRunWorker(AgentRunExecutionService executions, AgentRequestFactory requests,
                          AgentOutputValidator outputValidator, AgentProviderClient provider,
                          AgentCallRecordService callRecords,
                          @Qualifier("agentRunExecutor") Executor executor,
                          @Value("${app.agent.worker.concurrency:4}") int concurrency) {
        this.executions = executions;
        this.requests = requests;
        this.outputValidator = outputValidator;
        this.provider = provider;
        this.callRecords = callRecords;
        this.executor = executor;
        this.concurrency = Math.max(1, concurrency);
    }

    /** Keeps focused unit tests and embedders compatible when call persistence is not configured. */
    public AgentRunWorker(AgentRunExecutionService executions, AgentRequestFactory requests,
                          AgentOutputValidator outputValidator, AgentProviderClient provider,
                          Executor executor, int concurrency) {
        this(executions, requests, outputValidator, provider, null, executor, concurrency);
    }

    @Scheduled(fixedDelayString = "${app.agent.worker.poll-delay-ms:1000}")
    public void poll() {
        executions.recoverTimedOut();
        for (int i = 0; i < concurrency; i++) {
            try {
                executor.execute(this::processNext);
            } catch (RejectedExecutionException ignored) {
                // The fixed-size executor is full. The next scheduled poll will retry.
                break;
            }
        }
    }

    public boolean processNext() {
        var claimed = executions.claimNext();
        if (claimed.isEmpty()) return false;
        long startedAt = System.nanoTime();
        String runType = null;
        Long callRecordId = null;
        boolean callSucceeded = false;
        com.example.agentcollab.client.AgentProviderResult providerResult = null;
        try {
            var request = requests.create(claimed.get().runId());
            runType = request.runType().name();
            if (callRecords != null) {
                callRecordId = callRecords.start(claimed.get().runId(), provider.providerName(),
                        provider.modelName(), request, provider.requestPayload(request)).getId();
            }
            providerResult = provider.generate(request);
            outputValidator.validate(request, providerResult);
            if (callRecordId != null) {
                callRecords.succeed(callRecordId, providerResult, elapsedMillis(startedAt));
                callSucceeded = true;
            }
            executions.complete(claimed.get(), providerResult);
            log.atInfo()
                    .setMessage("Agent run completed")
                    .addKeyValue("runId", claimed.get().runId())
                    .addKeyValue("runType", runType)
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .log();
        } catch (AgentProviderException ex) {
            if (callRecordId != null && !callSucceeded) {
                callRecords.fail(callRecordId, providerResult, ex.getCode(), ex.getMessage(), ex.isRetryable(), elapsedMillis(startedAt));
            }
            executions.handleFailure(claimed.get(), ex.getCode(), ex.getMessage(), ex.isRetryable());
            log.atWarn()
                    .setMessage("Agent run failed")
                    .addKeyValue("runId", claimed.get().runId())
                    .addKeyValue("runType", runType)
                    .addKeyValue("errorCode", ex.getCode())
                    .addKeyValue("retryable", ex.isRetryable())
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .log();
        } catch (ApiException ex) {
            if (callRecordId != null && !callSucceeded) {
                callRecords.fail(callRecordId, providerResult, ex.getCode(), ex.getMessage(), false, elapsedMillis(startedAt));
            }
            executions.handleFailure(claimed.get(), ex.getCode(), ex.getMessage(), false);
            log.atError()
                    .setMessage("Agent run failed with business error")
                    .setCause(ex)
                    .addKeyValue("runId", claimed.get().runId())
                    .addKeyValue("runType", runType)
                    .addKeyValue("errorCode", ex.getCode())
                    .addKeyValue("details", ex.getDetails())
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .log();
        } catch (RuntimeException ex) {
            if (callRecordId != null && !callSucceeded) {
                callRecords.fail(callRecordId, providerResult, "AGENT_EXECUTION_FAILED", describe(ex), false, elapsedMillis(startedAt));
            }
            String message = describe(ex);
            String errorCode = ex instanceof DataIntegrityViolationException
                    && "GENERATE_CODE_CONTEXT_PLAN".equals(runType)
                    ? "CODE_CONTEXT_PERSISTENCE_ERROR" : "AGENT_EXECUTION_FAILED";
            executions.handleFailure(claimed.get(), errorCode,
                    "Unexpected provider error: " + message, false);
            log.atError()
                    .setMessage("Agent run failed unexpectedly")
                    .setCause(ex)
                    .addKeyValue("runId", claimed.get().runId())
                    .addKeyValue("runType", runType)
                    .addKeyValue("errorType", ex.getClass().getSimpleName())
                    .addKeyValue("rootCause", rootCauseType(ex))
                    .addKeyValue("rootCauseMessage", rootCauseMessage(ex))
                    .addKeyValue("errorMessage", ex.getMessage())
                    .addKeyValue("persistenceError", ex instanceof DataIntegrityViolationException)
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .log();
        }
        return true;
    }

    private long elapsedMillis(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String describe(Throwable error) {
        Throwable root = rootCause(error);
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) detail = error.getMessage();
        if (detail == null || detail.isBlank()) detail = root.getClass().getSimpleName();
        detail = detail.replace('\r', ' ').replace('\n', ' ');
        if (detail.length() > 500) detail = detail.substring(0, 500);
        return root.getClass().getSimpleName() + ": " + detail;
    }

    private String rootCauseType(Throwable error) {
        return rootCause(error).getClass().getSimpleName();
    }

    private String rootCauseMessage(Throwable error) {
        String message = rootCause(error).getMessage();
        if (message == null || message.isBlank()) return "";
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}
