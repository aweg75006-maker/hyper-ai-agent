<template>
  <PlatformShell
    active="routes"
    eyebrow="ROUTING CONTROL"
    title="路由策略"
    subtitle="配置业务路由的能力约束、候选顺序、超时与 Fallback 边界"
  >
    <template #actions>
      <button class="platform-button primary" type="button" @click="openCreate">创建路由</button>
    </template>

    <div v-if="message" :class="['platform-alert', messageType]">
      <strong>{{ messageType === 'error' ? '操作失败' : '操作成功' }}</strong><span>{{ message }}</span>
    </div>

    <section class="route-grid">
      <article v-for="route in routes" :key="route.routeKey" class="route-card">
        <div class="route-card-head">
          <span :class="['status-chip', route.enabled ? 'online' : 'offline']">{{ route.enabled ? 'ACTIVE' : 'DISABLED' }}</span>
          <span class="route-version">v{{ route.configVersion }}</span>
        </div>
        <h2>{{ route.routeKey }}</h2>
        <div class="capability-list"><span v-for="capability in route.requiredCapabilities" :key="capability">{{ capability }}</span></div>
        <ol class="candidate-stack">
          <li v-for="(modelKey, index) in route.targetModelKeys" :key="modelKey"><span>{{ index + 1 }}</span><strong>{{ modelKey }}</strong><small>{{ index === 0 ? 'PRIMARY' : 'FALLBACK' }}</small></li>
        </ol>
        <div class="route-stats">
          <span><small>最大尝试</small><strong>{{ route.maxAttempts }}</strong></span>
          <span><small>首 Token</small><strong>{{ durationLabel(route.firstTokenTimeout) }}</strong></span>
          <span><small>Fallback</small><strong>{{ route.fallbackEnabled ? 'ON' : 'OFF' }}</strong></span>
        </div>
        <div class="route-card-actions">
          <button type="button" @click="openEdit(route)">编辑策略</button>
          <button type="button" @click="openSimulator(route)">路由模拟</button>
        </div>
      </article>
      <article v-if="!routes.length" class="route-card route-empty"><strong>还没有路由策略</strong><button type="button" @click="openCreate">创建第一条路由</button></article>
    </section>

    <div v-if="editorOpen" class="platform-modal-overlay" @click.self="closeEditor">
      <section class="platform-modal route-editor">
        <div class="platform-modal-head">
          <div><span class="panel-kicker">{{ editingExisting ? 'EDIT ROUTE' : 'CREATE ROUTE' }}</span><h2>{{ editingExisting ? form.routeKey : '新建路由策略' }}</h2></div>
          <button type="button" @click="closeEditor">×</button>
        </div>

        <form class="platform-form" @submit.prevent="submit">
          <div class="form-grid two-columns">
            <label><span>路由标识 routeKey</span><input v-model.trim="form.routeKey" required :disabled="editingExisting" placeholder="content-generation" /></label>
            <label><span>最大尝试次数</span><input v-model.number="form.maxAttempts" type="number" min="1" :max="Math.max(1, form.targetModelKeys.length)" required /></label>
            <label><span>请求总超时（ms）</span><input v-model.number="form.timeoutMs" type="number" min="1" required /></label>
            <label><span>首 Token 超时（ms）</span><input v-model.number="form.firstTokenTimeoutMs" type="number" min="1" required /></label>
            <label class="switch-field"><span>启用路由</span><input v-model="form.enabled" type="checkbox" /><i></i></label>
            <label class="switch-field"><span>允许 Fallback</span><input v-model="form.fallbackEnabled" type="checkbox" /><i></i></label>
          </div>

          <fieldset class="capability-fieldset">
            <legend>请求能力约束</legend>
            <label v-for="capability in capabilityOptions" :key="capability"><input v-model="form.requiredCapabilities" type="checkbox" :value="capability" /><span>{{ capability }}</span></label>
          </fieldset>

          <fieldset class="candidate-fieldset">
            <legend>候选模型顺序</legend>
            <div class="candidate-picker">
              <select v-model="candidateToAdd"><option value="">选择模型加入路由</option><option v-for="model in availableModels" :key="model.modelKey" :value="model.modelKey">{{ model.displayName }} · {{ model.modelKey }}</option></select>
              <button type="button" :disabled="!candidateToAdd" @click="addCandidate">加入</button>
            </div>
            <div class="candidate-editor-list">
              <div v-for="(modelKey, index) in form.targetModelKeys" :key="modelKey">
                <span>{{ index + 1 }}</span><strong>{{ modelKey }}</strong>
                <button type="button" :disabled="index === 0" @click="moveCandidate(index, -1)">↑</button>
                <button type="button" :disabled="index === form.targetModelKeys.length - 1" @click="moveCandidate(index, 1)">↓</button>
                <button type="button" class="danger-text" @click="removeCandidate(index)">移除</button>
              </div>
              <p v-if="!form.targetModelKeys.length">至少添加一个候选模型</p>
            </div>
          </fieldset>

          <div class="platform-form-actions">
            <button class="platform-button secondary" type="button" @click="closeEditor">取消</button>
            <button class="platform-button primary" type="submit" :disabled="submitting">{{ submitting ? '保存中…' : '保存路由' }}</button>
          </div>
        </form>
      </section>
    </div>

    <div v-if="simulatorOpen" class="platform-modal-overlay" @click.self="closeSimulator">
      <section class="platform-modal simulator-modal">
        <div class="platform-modal-head">
          <div><span class="panel-kicker">ROUTE SIMULATOR</span><h2>{{ simulatorRoute?.routeKey }}</h2></div>
          <button type="button" @click="closeSimulator">×</button>
        </div>
        <p class="simulator-description">模拟只执行能力过滤和候选排序，不会调用真实模型，也不会消耗 Token。</p>
        <button class="platform-button primary full" type="button" :disabled="simulating" @click="simulate">{{ simulating ? '计算路由中…' : '开始模拟' }}</button>
        <div v-if="simulation" class="simulation-result">
          <div class="simulation-summary"><span>候选数量 <strong>{{ simulation.candidates?.length || 0 }}</strong></span><span>最大尝试 <strong>{{ simulation.maxAttempts }}</strong></span><span>Fallback <strong>{{ simulation.fallbackEnabled ? 'ON' : 'OFF' }}</strong></span></div>
          <div v-for="(candidate, index) in simulation.candidates" :key="candidate.model.modelKey" class="simulation-candidate">
            <span>{{ index + 1 }}</span>
            <div><strong>{{ candidate.model.modelKey }}</strong><small>{{ candidate.provider.providerType }} · {{ candidate.reason || candidate.reasons?.join(' / ') || '能力匹配' }}</small></div>
          </div>
        </div>
      </section>
    </div>
  </PlatformShell>
</template>

<script>
import PlatformShell from '../components/PlatformShell.vue'
import { getGatewayModels, getGatewayRoutes, saveGatewayRoute, simulateGatewayRoute } from '../utils/api'

const emptyForm = () => ({
  routeKey: '', requiredCapabilities: ['CHAT'], timeoutMs: 60000,
  firstTokenTimeoutMs: 15000, maxAttempts: 1, fallbackEnabled: true,
  enabled: true, targetModelKeys: []
})

export default {
  name: 'RouteManagement',
  components: { PlatformShell },
  data() {
    return {
      routes: [], models: [], capabilityOptions: ['CHAT', 'STREAM', 'TOOLS', 'VISION', 'EMBEDDING'],
      editorOpen: false, editingExisting: false, form: emptyForm(), candidateToAdd: '', submitting: false,
      simulatorOpen: false, simulatorRoute: null, simulation: null, simulating: false,
      message: '', messageType: 'success'
    }
  },
  computed: {
    availableModels() { return this.models.filter(model => model.enabled && !this.form.targetModelKeys.includes(model.modelKey)) }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      try {
        const [routes, models] = await Promise.all([getGatewayRoutes(), getGatewayModels()])
        this.routes = [...routes].sort((left, right) => left.routeKey.localeCompare(right.routeKey))
        this.models = models
      } catch (error) {
        this.notify(error.response?.data?.message || '路由策略加载失败', 'error')
      }
    },
    openCreate() {
      this.editingExisting = false
      this.form = emptyForm()
      this.editorOpen = true
    },
    openEdit(route) {
      this.editingExisting = true
      this.form = {
        routeKey: route.routeKey,
        requiredCapabilities: [...route.requiredCapabilities],
        timeoutMs: this.durationToMillis(route.timeout, 60000),
        firstTokenTimeoutMs: this.durationToMillis(route.firstTokenTimeout, 15000),
        maxAttempts: route.maxAttempts,
        fallbackEnabled: route.fallbackEnabled,
        enabled: route.enabled,
        targetModelKeys: [...route.targetModelKeys]
      }
      this.editorOpen = true
    },
    closeEditor() { this.editorOpen = false; this.candidateToAdd = '' },
    addCandidate() {
      if (this.candidateToAdd && !this.form.targetModelKeys.includes(this.candidateToAdd)) {
        this.form.targetModelKeys.push(this.candidateToAdd)
        this.candidateToAdd = ''
        if (this.form.maxAttempts > this.form.targetModelKeys.length) this.form.maxAttempts = this.form.targetModelKeys.length
      }
    },
    removeCandidate(index) {
      this.form.targetModelKeys.splice(index, 1)
      this.form.maxAttempts = Math.max(1, Math.min(this.form.maxAttempts, this.form.targetModelKeys.length))
    },
    moveCandidate(index, direction) {
      const destination = index + direction
      if (destination < 0 || destination >= this.form.targetModelKeys.length) return
      // splice 保留显式顺序；后端会把顺序写入 ai_route_target.target_order。
      const [item] = this.form.targetModelKeys.splice(index, 1)
      this.form.targetModelKeys.splice(destination, 0, item)
    },
    async submit() {
      if (!this.form.targetModelKeys.length) {
        this.notify('至少添加一个候选模型', 'error')
        return
      }
      if (this.form.maxAttempts > this.form.targetModelKeys.length) {
        this.notify('最大尝试次数不能超过候选模型数量', 'error')
        return
      }
      this.submitting = true
      try {
        await saveGatewayRoute(this.form, this.editingExisting)
        const routeKey = this.form.routeKey
        this.closeEditor()
        await this.load()
        this.notify(`路由 ${routeKey} 已保存并刷新`, 'success')
      } catch (error) {
        this.notify(error.response?.data?.message || '路由保存失败', 'error')
      } finally {
        this.submitting = false
      }
    },
    openSimulator(route) { this.simulatorRoute = route; this.simulation = null; this.simulatorOpen = true },
    closeSimulator() { this.simulatorOpen = false },
    async simulate() {
      this.simulating = true
      try {
        this.simulation = await simulateGatewayRoute(this.simulatorRoute.routeKey, {
          route: this.simulatorRoute.routeKey,
          model: null,
          messages: [{ role: 'user', content: 'route simulation' }],
          stream: false,
          temperature: 0,
          maxTokens: 32,
          requirements: this.simulatorRoute.requiredCapabilities,
          metadata: { application: 'route-console' }
        })
      } catch (error) {
        this.notify(error.response?.data?.message || '路由模拟失败', 'error')
      } finally {
        this.simulating = false
      }
    },
    durationToMillis(value, fallback) {
      if (typeof value === 'number') return Math.round(value * 1000)
      const matched = typeof value === 'string' ? value.match(/^PT([0-9.]+)S$/) : null
      return matched ? Math.round(Number(matched[1]) * 1000) : fallback
    },
    durationLabel(value) { return `${Math.round(this.durationToMillis(value, 0) / 1000)}s` },
    notify(message, type) {
      this.message = message; this.messageType = type
      window.clearTimeout(this.messageTimer)
      this.messageTimer = window.setTimeout(() => { this.message = '' }, 4200)
    }
  },
  beforeUnmount() { window.clearTimeout(this.messageTimer) }
}
</script>
