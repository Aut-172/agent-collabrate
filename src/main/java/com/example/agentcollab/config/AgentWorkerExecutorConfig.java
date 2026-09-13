package com.example.agentcollab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AgentWorkerExecutorConfig {
    @Bean(name = "agentRunExecutor")
    public ThreadPoolTaskExecutor agentRunExecutor(
            @Value("${app.agent.worker.concurrency:4}") int concurrency,
            @Value("${app.agent.worker.queue-capacity:0}") int queueCapacity) {
        int poolSize = Math.max(1, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("agent-run-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
