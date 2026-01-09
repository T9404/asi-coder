package com.example.youtrackmcp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(YouTrackProperties::class)
class YouTrackConfig

@ConfigurationProperties(prefix = "youtrack")
data class YouTrackProperties(
    var baseUrl: String = "http://localhost:8080",
    var token: String = ""
)
