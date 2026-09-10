package com.example.agentcollab.service;

import com.example.agentcollab.client.ProviderSyncException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CodeEvidenceWorker {
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
        catch (RuntimeException ex) { evidence.handleFailure(claimed.get(),
                "Unexpected evidence collection error: " + ex.getClass().getSimpleName(), false); }
        return true;
    }
}
