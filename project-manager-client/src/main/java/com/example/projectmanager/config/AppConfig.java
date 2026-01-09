package com.example.projectmanager.config;

import com.example.projectmanager.controller.GenericController;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class AppConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, List<McpSyncClient> mcpClients) {
        return builder
                .defaultSystem("You're a helpful assistant specialized in project management tasks.")
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpClients))
                //.defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true", matchIfMissing = true)
    public McpSyncClient mcpClient(
            @Value("${spring.ai.mcp.client.request-timeout:30s}") Duration requestTimeout,
            @Value("${spring.ai.mcp.client.sse.connections.yourack-mcp-server.url}") String baseUrl,
            @Value("${spring.ai.mcp.client.sse.connections.yourack-mcp-server.sse-endpoint:/sse}") String sseEndpoint
    ) {
        McpClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .build();

        client.initialize();
        return client;
    }

    @Bean
    public ToolCallbackProvider callbackProvider(GenericController genericController) {
        return MethodToolCallbackProvider.builder().toolObjects(genericController).build();
    }

}
