package com.scott.tech.mud.mud_game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables Spring's task scheduling and exposes a named {@link TaskScheduler} bean
 * used by game-event components such as {@link com.scott.tech.mud.mud_game.event.NpcWanderScheduler}.
 *
 * Using an explicit bean (instead of relying on the auto-created single-threaded one)
 * means events run on a named thread pool that is visible in thread dumps.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("game-event-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }
}
