package com.example.agentcollab.service;

import com.example.agentcollab.client.ProviderSyncException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RepoIngestionWorker {
    private final RepoIngestionService ingestion;
    private final RepoInventoryBuilder inventoryBuilder;

    public RepoIngestionWorker(RepoIngestionService ingestion, RepoInventoryBuilder inventoryBuilder) {
        this.ingestion = ingestion;
        this.inventoryBuilder = inventoryBuilder;
    }

    @Scheduled(fixedDelayString = "${app.code-context.worker.poll-delay-ms:1000}")
    public void poll() {
        ingestion.recoverTimedOut();
        processNext();
    }

    public boolean processNext() {
        var claimed = ingestion.claimNext();
        if (claimed.isEmpty()) return false;
        try {
            var context = ingestion.context(claimed.get());
            var snapshot = inventoryBuilder.fetch(context.project());
            ingestion.complete(claimed.get(), snapshot);
        } catch (ProviderSyncException ex) {
            ingestion.handleFailure(claimed.get(), ex.getMessage(), ex.isRetryable());
        } catch (RuntimeException ex) {
            ingestion.handleFailure(claimed.get(),
                    "Unexpected Code Context Provider error: " + ex.getClass().getSimpleName(), false);
        }
        return true;
    }
}
