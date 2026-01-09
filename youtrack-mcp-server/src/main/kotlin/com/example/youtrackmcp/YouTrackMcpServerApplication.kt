package com.example.youtrackmcp

import com.example.youtrackmcp.mcp.YouTrackTools
import com.example.youtrackmcp.service.YouTrackService
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class YouTrackMcpServerApplication

fun main(args: Array<String>) {
    runApplication<YouTrackMcpServerApplication>(*args)
}
