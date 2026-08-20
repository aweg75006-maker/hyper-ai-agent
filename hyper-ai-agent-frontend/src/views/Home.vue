<template>
  <div class="workspace-shell">
    <aside class="workspace-sidebar">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true">H</div>
        <div><strong>Hyper AI</strong><span>Agent Platform</span></div>
      </div>

      <nav class="workspace-nav" aria-label="工作台导航">
        <div class="nav-label">Workspace</div>
        <button class="nav-item active" type="button"><span class="nav-symbol">⌂</span>应用中心</button>
        <button class="nav-item" type="button" @click="goToAssistant"><span class="nav-symbol">◫</span>会话助手</button>
        <button class="nav-item" type="button" @click="goToPDF"><span class="nav-symbol">▤</span>知识问答</button>
        <div class="nav-label platform-label">Control plane</div>
        <button class="nav-item" type="button" @click="goToOperations"><span class="nav-symbol">⌁</span>运行中心</button>
        <button class="nav-item" type="button" @click="goToModels"><span class="nav-symbol">◉</span>模型管理</button>
        <button class="nav-item" type="button" @click="goToRoutes"><span class="nav-symbol">⑂</span>路由策略</button>
      </nav>

      <div class="sidebar-foot">
        <div class="environment-card">
          <div class="environment-head"><span class="status-dot"></span>Local Environment</div>
          <p>后端服务运行正常</p>
          <span>Spring Boot · 8123</span>
        </div>
      </div>
    </aside>

    <main class="workspace-main">
      <header class="workspace-topbar">
        <div><span class="eyebrow">AI WORKSPACE</span><h1>应用中心</h1></div>
        <div class="topbar-meta">
          <span class="live-badge"><i></i> All systems operational</span>
          <div class="user-avatar" aria-label="本地开发环境">DEV</div>
        </div>
      </header>

      <section class="workspace-content">
        <div class="hero-panel">
          <div class="hero-copy">
            <span class="hero-kicker">SPRING AI · AGENT RUNTIME</span>
            <h2>让模型能力进入<br><em>可执行的业务流程</em></h2>
            <p>统一体验对话、任务规划与知识库检索能力。每个应用都由真实的 Java 后端服务驱动。</p>
            <div class="hero-actions">
              <button class="primary-action" type="button" @click="goToAssistant">开始新会话 <span>→</span></button>
              <button class="secondary-action" type="button" @click="goToPDF">上传知识文档</button>
            </div>
          </div>

          <div class="runtime-visual" aria-label="Agent 运行链路">
            <div class="runtime-title"><span>Agent Runtime</span><span class="runtime-state"><i></i> Ready</span></div>
            <div class="runtime-flow">
              <div class="flow-node"><span class="node-index">01</span><div><strong>Request</strong><small>Context input</small></div></div>
              <div class="flow-line"><span></span></div>
              <div class="flow-node active"><span class="node-index">02</span><div><strong>Reasoning</strong><small>ReAct loop</small></div></div>
              <div class="flow-line"><span></span></div>
              <div class="flow-node"><span class="node-index">03</span><div><strong>Execute</strong><small>Tools &amp; RAG</small></div></div>
            </div>
            <div class="runtime-footer">
              <span><i class="violet"></i> Tool Calling</span>
              <span><i class="cyan"></i> Vector Search</span>
              <span><i class="green"></i> SSE Stream</span>
            </div>
          </div>
        </div>

        <div class="section-heading">
          <div><h3>可用应用</h3><p>选择一个能力开始体验</p></div>
          <span class="app-count">4 APPS</span>
        </div>

        <div class="apps-grid">
          <article class="app-card" tabindex="0" @click="goToPsychology" @keyup.enter="goToPsychology">
            <div class="card-topline"><div class="app-icon psychology-icon">PS</div><span class="availability"><i></i> Available</span></div>
            <div class="card-copy"><span class="app-category">CONVERSATIONAL AI</span><h4>心理咨询助手</h4><p>面向心理支持场景的流式对话服务，提供连续、自然的咨询体验。</p></div>
            <div class="card-meta"><span>SSE Streaming</span><span>Context Memory</span></div>
            <div class="card-link">打开应用 <span>↗</span></div>
          </article>

          <article class="app-card featured" tabindex="0" @click="goToManus" @keyup.enter="goToManus">
            <div class="card-topline"><div class="app-icon manus-icon">AG</div><span class="availability"><i></i> Available</span></div>
            <div class="card-copy"><span class="app-category">AUTONOMOUS AGENT</span><h4>任务智能体</h4><p>通过 ReAct 循环自主规划与执行任务，并支持关键节点人工确认。</p></div>
            <div class="card-meta"><span>Agent Loop</span><span>Ask Human</span></div>
            <div class="card-link">打开应用 <span>↗</span></div>
          </article>

          <article class="app-card" tabindex="0" @click="goToAssistant" @keyup.enter="goToAssistant">
            <div class="card-topline"><div class="app-icon assistant-icon">CH</div><span class="availability"><i></i> Available</span></div>
            <div class="card-copy"><span class="app-category">CHAT SERVICE</span><h4>多会话助手</h4><p>支持会话创建、历史记录与上下文持久化的通用对话服务。</p></div>
            <div class="card-meta"><span>Chat History</span><span>Persistence</span></div>
            <div class="card-link">打开应用 <span>↗</span></div>
          </article>

          <article class="app-card" tabindex="0" @click="goToPDF" @keyup.enter="goToPDF">
            <div class="card-topline"><div class="app-icon pdf-icon">KB</div><span class="availability"><i></i> Available</span></div>
            <div class="card-copy"><span class="app-category">KNOWLEDGE BASE</span><h4>文档知识问答</h4><p>上传 PDF 并完成向量检索，让模型基于文档上下文回答问题。</p></div>
            <div class="card-meta"><span>RAG Pipeline</span><span>PDF Preview</span></div>
            <div class="card-link">打开应用 <span>↗</span></div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script>
export default {
  name: 'Home',
  methods: {
    goToPsychology() { this.$router.push('/psychology') },
    goToManus() { this.$router.push('/manus') },
    goToAssistant() { this.$router.push('/assistant') },
    goToPDF() { this.$router.push('/pdf') },
    goToOperations() { this.$router.push('/operations') },
    goToModels() { this.$router.push('/models') },
    goToRoutes() { this.$router.push('/routes') }
  }
}
</script>
