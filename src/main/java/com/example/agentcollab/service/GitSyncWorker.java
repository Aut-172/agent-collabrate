package com.example.agentcollab.service;

import com.example.agentcollab.client.CiProviderClient;
import com.example.agentcollab.client.GitProviderClient;
import com.example.agentcollab.client.ProviderSyncException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GitSyncWorker {
    private final GitSyncService git;
    private final CiSyncService ci;
    private final GitProviderClient gitProvider;
    private final CiProviderClient ciProvider;

    public GitSyncWorker(GitSyncService git, CiSyncService ci,
                         GitProviderClient gitProvider, CiProviderClient ciProvider) {
        this.git = git;
        this.ci = ci;
        this.gitProvider = gitProvider;
        this.ciProvider = ciProvider;
    }

    @Scheduled(fixedDelayString = "${app.git.worker.poll-delay-ms:1000}")
    public void poll() {
        git.recoverTimedOut();
        ci.recoverTimedOut();
        if (!processGitNext()) processCiNext();
    }

    public boolean processGitNext() {
        var claimed = git.claimNext();
        if (claimed.isEmpty()) return false;
        try {
            var context = git.context(claimed.get());
            git.complete(claimed.get(), gitProvider.validate(context.project(), context.delivery()));
        } catch (ProviderSyncException ex) {
            git.handleFailure(claimed.get(), ex.getMessage(), ex.isRetryable());
        } catch (RuntimeException ex) {
            git.handleFailure(claimed.get(),
                    "Unexpected Git Provider error: " + ex.getClass().getSimpleName(), false);
        }
        return true;
    }

    public boolean processCiNext() {
        var claimed = ci.claimNext();
        if (claimed.isEmpty()) return false;
        try {
            var context = ci.context(claimed.get());
            ci.complete(claimed.get(), ciProvider.sync(context.project(), context.run()));
        } catch (ProviderSyncException ex) {
            ci.handleFailure(claimed.get(), ex.getMessage(), ex.isRetryable());
        } catch (RuntimeException ex) {
            ci.handleFailure(claimed.get(),
                    "Unexpected CI Provider error: " + ex.getClass().getSimpleName(), false);
        }
        return true;
    }
}
