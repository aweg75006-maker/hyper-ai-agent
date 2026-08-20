<template>
  <div class="platform-shell">
    <aside class="platform-sidebar">
      <router-link to="/" class="platform-brand">
        <span class="platform-brand-mark">H</span>
        <span><strong>Hyper AI</strong><small>CONTROL PLANE</small></span>
      </router-link>

      <nav class="platform-nav" aria-label="平台管理导航">
        <span class="platform-nav-label">PLATFORM</span>
        <router-link to="/operations" :class="navClass('operations')">
          <span class="platform-nav-icon">⌁</span><span>运行中心</span>
        </router-link>
        <router-link to="/models" :class="navClass('models')">
          <span class="platform-nav-icon">◉</span><span>模型管理</span>
        </router-link>
        <router-link to="/routes" :class="navClass('routes')">
          <span class="platform-nav-icon">⑂</span><span>路由策略</span>
        </router-link>

        <span class="platform-nav-label secondary">WORKSPACE</span>
        <router-link to="/" class="platform-nav-item">
          <span class="platform-nav-icon">⌂</span><span>应用中心</span>
        </router-link>
        <router-link to="/assistant" class="platform-nav-item">
          <span class="platform-nav-icon">◫</span><span>会话助手</span>
        </router-link>
      </nav>

      <div class="platform-environment">
        <div><i></i><span>Local Control Plane</span></div>
        <small>PostgreSQL · Gateway API</small>
      </div>
    </aside>

    <main class="platform-main">
      <header class="platform-header">
        <div>
          <span class="platform-eyebrow">{{ eyebrow }}</span>
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <div class="platform-header-actions">
          <slot name="actions" />
          <span class="platform-avatar">DEV</span>
        </div>
      </header>
      <section class="platform-content">
        <slot />
      </section>
    </main>
  </div>
</template>

<script>
export default {
  name: 'PlatformShell',
  props: {
    active: { type: String, required: true },
    eyebrow: { type: String, default: 'AI GOVERNANCE' },
    title: { type: String, required: true },
    subtitle: { type: String, required: true }
  },
  methods: {
    navClass(section) {
      return ['platform-nav-item', { active: this.active === section }]
    }
  }
}
</script>
