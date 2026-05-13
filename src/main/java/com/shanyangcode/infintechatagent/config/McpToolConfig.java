package com.shanyangcode.infintechatagent.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class McpToolConfig {

    @Value("${bigmodel.api-key}")
    private String apiKey;

    @Bean
    public McpToolProvider mcpToolProvider() {
        List<McpClient> clients = new ArrayList<>();

        // BigModel 网络搜索（需要有效 API key）
        if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("xxx")) {
            try {
                McpTransport searchTransport = new HttpMcpTransport.Builder()
                        .sseUrl("https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization=" + apiKey.trim())
                        .build();
                clients.add(new DefaultMcpClient.Builder()
                        .key("BigModelSearchMcpClient")
                        .transport(searchTransport)
                        .build());
                log.info("MCP 网络搜索已启用");
            } catch (Exception e) {
                log.warn("MCP 网络搜索初始化失败，已跳过: {}", e.getMessage());
            }
        } else {
            log.info("MCP 网络搜索未配置，已跳过");
        }

        // 本地时间服务
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String timeCommand = os.contains("win")
                    ? System.getProperty("user.home") + "\\.local\\bin\\mcp-server-time.exe"
                    : "mcp-server-time";
            McpTransport timeTransport = new StdioMcpTransport.Builder()
                    .command(Arrays.asList(timeCommand, "--local-timezone=Asia/Shanghai"))
                    .build();
            clients.add(new DefaultMcpClient.Builder()
                    .key("timeClient")
                    .transport(timeTransport)
                    .build());
            log.info("MCP 时间服务已启用");
        } catch (Exception e) {
            log.warn("MCP 时间服务初始化失败，已跳过: {}", e.getMessage());
        }

        return McpToolProvider.builder()
                .mcpClients(clients)
                .build();
    }
}