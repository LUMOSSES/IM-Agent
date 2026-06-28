# Chat-Agent

基于 LangChain4j + Spring Boot 3.5 的智能对话服务，集成 RAG 知识库、MCP 外部工具、SSE 流式响应。

## 功能特性

- **AI 对话**：阿里 DashScope (qwen-max) 驱动的智能问答
- **RAG 知识检索**：PGVector 向量数据库 + 本地文档（社交技巧 / 心理学 / 聊天方法）
- **MCP 工具**：时间查询 (mcp-server-time) + 联网搜索 (智谱 BigModel)
- **SSE 流式输出**：Reactor Flux 实时流式响应
- **会话记忆**：Redis 存储，20 条上下文窗口，3600s TTL
- **安全认证**：JWT (HS512) + Gateway 来源校验（支持独立模式跳过）
- **监控**：Spring Boot Actuator + Prometheus + 自定义 AI 调用指标

## 技术栈

| 组件 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5.9, Java 17 |
| AI 框架 | LangChain4j 1.1.0 |
| 模型服务 | 阿里 DashScope (qwen-max + text-embedding-v4) |
| 向量数据库 | PGVector |
| 会话存储 | Redis |
| MCP 搜索 | 智谱 BigModel MCP |
| MCP 时间 | mcp-server-time (stdio) |
| 安全 | jjwt 0.11.5 + Spring Interceptor |
| 监控 | Actuator + Micrometer + Prometheus |

## 快速开始

### 前置依赖

```bash
# Redis
docker run -d --name redis -p 6379:6379 redis:7 --requirepass yourpassword

# PGVector
docker run -d --name pgvector -p 5432:5432 -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD=yourpassword -e POSTGRES_DB=dp \
  pgvector/pgvector:pg16

# MCP 时间服务
pip install pipx && pipx install mcp-server-time
```

### 配置

```bash
# 复制配置模板
cp application-template.yml src/main/resources/application.yml

# 编辑 application.yml，填入你的 API Key：
#   - langchain4j.community.dashscope.*.api-key  (阿里 DashScope)
#   - bigmodel.api-key                           (智谱 BigModel 搜索)
#   - spring.data.redis.password                 (Redis 密码)
#   - pgvector.password                          (PGVector 密码)
```

### 启动

```bash
# 独立模式（无需 Gateway，跳过认证）
mvn spring-boot:run -Dspring-boot.run.profiles=standalone
# 访问: http://localhost:8087/api/index.html
```

```bash
# 集成模式（配合 Chat-Agent Gateway，需要 JWT 认证）
mvn spring-boot:run
```

## 项目结构

```
src/main/java/com/aiagent/chatagent/
├── ai/                    # AI 对话接口与配置
│   ├── AiChat.java        # AI 服务接口（@SystemMessage + @Tool）
│   └── AiChatService.java # AI 服务装配（模型 + 工具 + 记忆）
├── config/                # 配置类
│   ├── CorsConfig.java    # CORS 跨域
│   ├── DashScopeModelConfig.java  # 模型配置
│   ├── EmbeddingStoreConfig.java  # PGVector 配置
│   ├── McpToolConfig.java # MCP 工具配置
│   └── RagConfig.java     # RAG 检索配置
├── controller/
│   └── AiChatController.java  # /chat, /streamChat, /insert
├── security/              # 安全拦截器
│   ├── WebConfig.java     # 拦截器注册
│   ├── SourceHandler.java # Gateway 来源校验
│   ├── JwtHandler.java    # JWT 认证
│   └── JwtUtil.java       # JWT 解析工具
├── tool/                  # LangChain4j @Tool
│   ├── RagTool.java       # 知识库检索
│   ├── EmailTool.java     # 邮件发送
│   └── TimeTool.java      # 本地时间
├── guardrail/             # 安全护栏
├── Monitor/               # AI 调用监控
├── job/                   # 启动任务（加载文档）
├── model/dto/             # 请求/响应 DTO
└── common/ / Exception/   # 公共工具与异常处理
```

## API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 普通对话 `{"sessionId":1,"prompt":"你好"}` |
| `/api/streamChat` | POST | SSE 流式对话 |
| `/api/insert` | POST | 插入知识库 `{"question":"...","answer":"...","sourceName":"xxx.md"}` |
| `/api/index.html` | GET | 独立演示页面 |
| `/actuator/health` | GET | 健康检查 |

## 认证模式

- **独立模式** (`agent.auth.enabled: false`)：跳过所有安全拦截器，可直接访问
- **集成模式**：需携带 `Authorization: Bearer <JWT>` 和 `X-Request-Source: Agent-Gateway` 请求头
