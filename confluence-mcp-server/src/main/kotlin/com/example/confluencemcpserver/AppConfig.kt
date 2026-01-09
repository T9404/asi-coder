package com.example.confluencemcpserver

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {

    @Bean
    fun confluenceTools(client: ConfluenceClient, properties: ConfluenceProperties): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder().toolObjects(ConfluenceMcpTools(client, properties)).build()
    }
}