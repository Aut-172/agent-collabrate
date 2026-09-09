package com.example.agentcollab.client;

public class AgentProviderException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public AgentProviderException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() { return code; }
    public boolean isRetryable() { return retryable; }
}
