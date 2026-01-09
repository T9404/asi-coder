package com.example.youtrackmcp.config

import com.example.youtrackmcp.mcp.YouTrackTools
import com.example.youtrackmcp.service.YouTrackService
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class AppConfig {
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate(HttpComponentsClientHttpRequestFactory())

    @Bean
    fun youTrackTools(youTrackService: YouTrackService): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder().toolObjects(YouTrackTools(youTrackService)).build()
    }
}
