package com.example.documentationagentclient.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@EnableAsync
@Configuration
class AsyncConfig {

    @Bean("confluenceExecutor")
    fun confluenceExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 20
            setQueueCapacity(200)
            setThreadNamePrefix("confluence-save-")
            initialize()
        }

    @Bean("projectManagerExecutor")
    fun projectManagerExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 10
            setQueueCapacity(100)
            setThreadNamePrefix("project-manager-")
            initialize()
        }

}