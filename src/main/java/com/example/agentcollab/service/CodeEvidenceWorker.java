package com.example.agentcollab.service;

import com.example.agentcollab.client.ProviderSyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CodeEvidenceWorker {
    private static final Logger log = LoggerFactory.getLogger(CodeEvidenceWorker.class);
    private final CodeEvidenceService evidence;
    private final CodeEvidenceCollector collector;

    public CodeEvidenceWorker(CodeEvidenceService evidence, CodeEvidenceCollector collector) {
        this.evidence = evidence; this.collector = collector;
    }

    @Scheduled(fixedDelayString = "${app.code-context.worker.poll-delay-ms:1000}")
    public void poll() { evidence.recoverTimedOut(); processNext(); }

    public boolean processNext() {
        var claimed = evidence.claimNext();
        if (claimed.isEmpty()) return false;
        try { evidence.complete(claimed.get(), collector.collect(evidence.context(claimed.get()))); }
        catch (ProviderSyncException ex) { evidence.handleFailure(claimed.get(), ex.getMessage(), ex.isRetryable()); }
        catch (RuntimeException ex) {
            String detail = describe(ex);
            String message = ex instanceof DataIntegrityViolationException
                    ? "CODE_CONTEXT_PERSISTENCE_ERROR: " + detail
                    : "Unexpected evidence collection error: " + detail;
            evidence.handleFailure(claimed.get(), message, false);
            log.atError()
                    .setMessage("Code Context evidence run failed")
                    .setCause(ex)
                    .addKeyValue("runId", claimed.get().runId())
                    .addKeyValue("jobId", claimed.get().jobId())
                    .addKeyValue("errorType", ex.getClass().getSimpleName())
                    .addKeyValue("rootCause", rootCause(ex).getClass().getSimpleName())
                    .addKeyValue("rootCauseMessage", rootCauseMessage(ex))
                    .addKeyValue("persistenceError", ex instanceof DataIntegrityViolationException)
                    .log();
        }
        return true;
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

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private String rootCauseMessage(Throwable error) {
        String message = rootCause(error).getMessage();
        if (message == null || message.isBlank()) return "";
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
