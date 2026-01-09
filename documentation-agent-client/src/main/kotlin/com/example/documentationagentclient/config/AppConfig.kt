package com.example.documentationagentclient.config

import com.example.documentationagentclient.controller.GenericController
import com.example.documentationagentclient.dto.IssueOperationResult
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport
import io.modelcontextprotocol.spec.McpClientTransport
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AppConfig {

    @Bean
    fun issueResultConverter(): BeanOutputConverter<IssueOperationResult?> {
        return BeanOutputConverter(IssueOperationResult::class.java)
    }

    @Bean
    fun chatClient(builder: ChatClient.Builder, mcpClients: MutableList<McpSyncClient?>): ChatClient {
        return builder
            .defaultSystem("You're a helpful assistant specialized in handling documentation issues.")
            .defaultToolCallbacks(SyncMcpToolCallbackProvider(mcpClients))
            //.defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .build()
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "spring.ai.mcp.client",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun mcpClient(
        @Value("\${spring.ai.mcp.client.request-timeout:30s}") requestTimeout: Duration?,
        @Value("\${spring.ai.mcp.client.sse.connections.confluence-mcp-server.url}") baseUrl: String?,
        @Value("\${spring.ai.mcp.client.sse.connections.confluence-mcp-server.sse-endpoint:/sse}") sseEndpoint: String?
    ): McpSyncClient {
        val transport: McpClientTransport = HttpClientSseClientTransport.builder(baseUrl)
            .sseEndpoint(sseEndpoint)
            .build()

        val client = McpClient.sync(transport)
            .requestTimeout(requestTimeout)
            .build()

        client.initialize()
        return client
    }

    @Bean
    fun callbackProvider(genericController: GenericController): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder().toolObjects(genericController).build()
    }

}