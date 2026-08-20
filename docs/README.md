# Hyper AI Agent

Hyper AI Agent 是一个基于 Java 21、Spring Boot 与 Spring AI 构建的 AI 应用项目，集成阿里云 DashScope，提供通用对话、PDF 文档问答、心理咨询场景 RAG 和多步骤工具调用能力。

## 当前能力

- 通用 AI 对话：支持 SSE 流式响应和会话历史。
- PDF 文档问答：支持上传、解析、OCR、向量化与按文档会话。
- 心理咨询场景：加载内置知识语料，通过 RAG 增强回答。
- HyperManus Agent：支持 ReAct 循环、工具选择、多步骤执行和终止控制。
- 工具调用：包括文件操作、网页搜索、网页抓取、资源下载、PDF 生成和受限终端执行。
- 前后端分离：后端提供 REST/SSE 接口，前端使用 Vue 3 与 Vite。

## 技术栈

### 后端

- Java 21
- Spring Boot 3.5.9
- Spring AI / Spring AI Alibaba
- DashScope
- PostgreSQL 与 pgvector
- Maven

### 前端

- Vue 3
- Vue Router 4
- Axios
- Vite
- PDF.js

## 目录结构

```text
hyper-ai-agent/
├── docs/                         # 项目文档
├── hyper-ai-agent-frontend/      # Vue 前端
├── src/main/java/                # 后端业务代码
├── src/main/resources/document/  # RAG 运行时知识语料
├── src/test/                     # 后端测试
├── .env.example                  # 环境变量模板
└── pom.xml                       # Maven 配置
```

`src/main/resources/document/` 下的 Markdown 是应用运行时读取的知识语料，不属于项目说明文档，因此不迁入 `docs/`。

## 本地启动

### 1. 准备环境变量

```bash
cp .env.example .env
```

在 `.env` 中填写实际使用的模型 API Key。`.env` 只用于本地配置，不应提交到 Git。

### 2. 启动后端

```bash
./mvnw spring-boot:run
```

后端默认地址为 `http://localhost:8123/api`。

### 3. 启动前端

```bash
cd hyper-ai-agent-frontend
npm ci
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5174`。

## 验证命令

```bash
./mvnw test
cd hyper-ai-agent-frontend && npm run build
```

## 主要接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/chat` | GET | 通用流式对话 |
| `/api/ai/pdf/upload` | POST | 上传 PDF |
| `/api/ai/pdf/chat` | GET | PDF 文档问答 |
| `/api/ai/psychology/chat` | GET | 心理咨询场景对话 |
| `/api/ai/manus/chat` | GET | HyperManus Agent 对话 |

## 仓库约定

- 项目只提交这一份 Markdown 说明文档。
- `.env`、`application-local.yml` 和 `mcp-servers.json` 仅用于本地配置，不纳入版本控制。
- `pdf-files/`、`storage/`、`tmp/` 和 `src/main/resources/document/` 属于本地运行数据，不纳入版本控制。
- API Key、密码和访问令牌必须通过环境变量或本地配置注入。
- 提交前执行后端测试、前端构建、`git diff --check` 和敏感信息检查。
