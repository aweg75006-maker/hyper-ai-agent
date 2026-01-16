# Hyper AI Agent Frontend

Vue3 前端项目，提供 AI 心理咨询大师和 AI 超级智能体两个应用。

## 功能特性

- 🏠 **主页**：应用选择页面
- 💬 **AI 心理咨询大师**：专业的AI心理咨询服务
- 🤖 **AI 超级智能体**：强大的AI智能助手
- ⚡ **实时对话**：通过 SSE（Server-Sent Events）实现实时流式对话
- 🎨 **现代化 UI**：美观的聊天界面设计

## 技术栈

- Vue 3
- Vue Router 4
- Axios
- Vite

## 安装依赖

```bash
npm install
```

## 开发

```bash
npm run dev
```

项目将在 `http://localhost:5173` 启动

## 构建

```bash
npm run build
```

## 预览构建结果

```bash
npm run preview
```

## 项目结构

```
src/
├── views/           # 页面组件
│   ├── Home.vue           # 主页
│   ├── PsychologyChat.vue # AI心理咨询大师
│   └── ManusChat.vue      # AI超级智能体
├── router/          # 路由配置
├── utils/           # 工具函数
│   └── api.js            # API 接口封装
├── styles/          # 样式文件
├── App.vue          # 根组件
└── main.js          # 入口文件
```

## API 配置

默认 API 地址：`http://localhost:8123/api`

可以在 `src/utils/api.js` 中修改 `API_BASE_URL` 来更改 API 地址。

## 注意事项

1. 确保后端服务运行在 `http://localhost:8123`
2. AI 心理咨询大师页面会自动生成唯一的聊天室 ID
3. 两个应用都使用 SSE 方式实时接收 AI 响应
