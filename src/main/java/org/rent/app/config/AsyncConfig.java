package org.rent.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {
    @Bean(name = "cdcServiceTaskThreadPoolTaskExecutor")
    public Executor cdcServiceTaskThreadPoolTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1); //Only one thread!!
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("cdcTaskManager-");
        return executor;
    }
}
