package com.example.agentcollab.client;

public class ProviderSyncException extends RuntimeException {
    private final boolean retryable;

    public ProviderSyncException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
}
