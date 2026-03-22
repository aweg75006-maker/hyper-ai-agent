# Hyper AI Agent

## 项目简介

Hyper AI Agent 是一个基于 Spring Boot 和 Spring AI 构建的智能 AI 代理系统，集成了阿里巴巴 DashScope 大模型服务。项目提供多种 AI 助手功能，包括智能对话、PDF 文档分析、心理咨询顾问、以及具备自主规划能力的 HyperManus 智能agent。

## 技术栈

### 后端
- **框架**：Spring Boot 3.5.9
- **Java 版本**：JDK 21
- **AI 框架**：Spring AI 1.1.0、Spring AI Alibaba 1.1.0.0-RC2（这个版本更新迭代很快，请以官网为主）
- **大模型服务**：阿里云 DashScope
- **数据库**：PostgreSQL（支持 PgVector 向量存储）
- **工具库**：Hutool、Lombok、Kryo（序列化）
- **API 文档**：Knife4j

### 前端
- **框架**：Vue 3
- **构建工具**：Vite 5
- **路由**：Vue Router 4
- **HTTP 客户端**：Axios
- **PDF 处理**：pdfjs-dist

## 核心功能

### 1. 智能对话 
- 基于阿里云 DashScope 大模型的流式对话
- 支持多会话管理和对话历史持久化
- SSE (Server-Sent Events) 实时响应

### 2. PDF 文档分析
- PDF 文档上传与解析
- 基于向量数据库的文档检索
- 智能问答与文档内容对话

### 3. 心理咨询顾问 (PsyApp)
- 基于阿里云知识库的 RAG 增强生成
- 针对不同年龄段的心理咨询场景
- 文件基础的对话记忆管理

### 4. HyperManus 自主代理
- 具备自主规划能力的智能代理
- 支持多步骤任务分解与执行
- 最大支持 20 步执行循环

### 5. 工具集成
| 工具 | 功能描述 |
|------|----------|
| FileOperationTool | 文件读写、创建、删除操作 |
| WebSearchTool | 网络搜索功能 |
| WebScrapingTool | 网页内容抓取 |
| ResourceDownloadTool | 资源文件下载 |
| TerminalOperationTool | 终端命令执行 |
| PDFGenerationTool | PDF 文档生成 |
| AskHumanTool | 向用户询问信息 |
| TerminateTool | 终止代理执行 |

### 6. RAG 检索增强生成
- 支持阿里云 DashScope 知识库
- 支持 PgVector 向量存储
- 自定义文档加载与分割策略

## 快速开始

### 环境要求
- JDK 21+
- Maven 3.6+
- Node.js 18+ (前端)
- PostgreSQL (可选，用于向量存储)

### 后端启动

```bash
# 克隆项目
git clone <repository-url>
cd hyper-ai-agent

# 配置环境变量或 application.yml
# 设置 DashScope API Key

# 启动后端服务
./mvnw spring-boot:run
```

### 前端启动

```bash
cd hyper-ai-agent-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

### 配置说明

在 `application.yml` 中配置必要参数：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      
search-api:
  api-key: ${SEARCH_API_KEY}
```

## API 文档

启动服务后访问：
- Swagger UI: `http://localhost:8123/api/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8123/api/v3/api-docs`

## 主要接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/ai/chat` | GET | 智能对话（流式） |
| `/api/ai/pdf/chat` | GET | PDF 文档对话 |
| `/api/ai/pdf/upload` | POST | 上传 PDF 文件 |
| `/api/ai/psychology/chat` | GET | 心理咨询对话 |
| `/api/ai/manus/chat` | GET | HyperManus 代理对话 |

