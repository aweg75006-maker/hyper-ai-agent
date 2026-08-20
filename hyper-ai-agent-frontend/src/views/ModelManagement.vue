<template>
  <PlatformShell
    active="models"
    eyebrow="MODEL REGISTRY"
    title="模型管理"
    subtitle="管理物理模型、能力标签、路由优先级与启停状态"
  >
    <template #actions>
      <button class="platform-button primary" type="button" @click="openCreate">注册模型</button>
    </template>

    <div v-if="message" :class="['platform-alert', messageType]">
      <strong>{{ messageType === 'error' ? '操作失败' : '操作成功' }}</strong><span>{{ message }}</span>
    </div>

    <section class="provider-strip">
      <article v-for="provider in providers" :key="provider.id" class="provider-card">
        <div class="provider-card-head"><span class="provider-logo">{{ provider.providerType.slice(0, 2) }}</span><span :class="['status-chip', provider.enabled ? 'online' : 'offline']">{{ provider.enabled ? 'ENABLED' : 'DISABLED' }}</span></div>
        <strong>{{ provider.name }}</strong>
        <small>{{ provider.providerType }} · {{ provider.status }}</small>
        <code>{{ provider.credentialRef }}</code>
      </article>
      <article v-if="!providers.length" class="provider-card empty">暂无 Provider 配置</article>
    </section>

    <section class="platform-panel table-panel">
      <div class="panel-heading-row">
        <div><span class="panel-kicker">REGISTERED MODELS</span><h2>模型注册表</h2></div>
        <span class="panel-meta">{{ models.length }} MODELS</span>
      </div>
      <div class="platform-table-wrap">
        <table class="platform-table model-table">
          <thead><tr><th>模型</th><th>物理模型</th><th>能力</th><th>上下文</th><th>优先级</th><th>状态</th><th></th></tr></thead>
          <tbody>
            <tr v-for="model in models" :key="model.modelKey">
              <td><strong>{{ model.displayName }}</strong><small>{{ model.modelKey }}</small></td>
              <td><span class="provider-badge">{{ providerName(model.providerAccountId) }}</span><small>{{ model.providerModelName }}</small></td>
              <td><div class="capability-list"><span v-for="capability in model.capabilities" :key="capability">{{ capability }}</span></div></td>
              <td>{{ number(model.contextWindow) }}</td>
              <td><span class="priority-value">P{{ model.priority }}</span></td>
              <td><span :class="['status-chip', model.enabled ? 'online' : 'offline']">{{ model.enabled ? '可路由' : '已停用' }}</span></td>
              <td class="table-actions">
                <button type="button" @click="openEdit(model)">编辑</button>
                <button type="button" :class="model.enabled ? 'danger-text' : 'success-text'" :disabled="savingKey === model.modelKey" @click="toggle(model)">{{ model.enabled ? '停用' : '启用' }}</button>
              </td>
            </tr>
            <tr v-if="!models.length"><td colspan="7"><div class="platform-empty">还没有注册模型</div></td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="editorOpen" class="platform-modal-overlay" @click.self="closeEditor">
      <section class="platform-modal model-editor">
        <div class="platform-modal-head">
          <div><span class="panel-kicker">{{ editingExisting ? 'EDIT MODEL' : 'REGISTER MODEL' }}</span><h2>{{ editingExisting ? '编辑模型配置' : '注册新模型' }}</h2></div>
          <button type="button" @click="closeEditor">×</button>
        </div>

        <form class="platform-form" @submit.prevent="submit">
          <div class="form-grid two-columns">
            <label><span>模型标识 modelKey</span><input v-model.trim="form.modelKey" required :disabled="editingExisting" placeholder="dashscope-qwen-max" /></label>
            <label><span>展示名称</span><input v-model.trim="form.displayName" required placeholder="Qwen Max" /></label>
            <label><span>Provider</span><select v-model="form.providerAccountId" required><option disabled value="">请选择 Provider</option><option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }}</option></select></label>
            <label><span>Provider 模型名</span><input v-model.trim="form.providerModelName" required placeholder="qwen-max" /></label>
            <label><span>上下文窗口</span><input v-model.number="form.contextWindow" type="number" min="1" required /></label>
            <label><span>路由优先级</span><input v-model.number="form.priority" type="number" min="0" required /><small>数字越小越优先</small></label>
            <label><span>成本等级</span><input v-model.number="form.costLevel" type="number" min="0" step="0.1" required /></label>
            <label class="switch-field"><span>启用模型</span><input v-model="form.enabled" type="checkbox" /><i></i></label>
          </div>

          <fieldset class="capability-fieldset">
            <legend>模型能力</legend>
            <label v-for="capability in capabilityOptions" :key="capability"><input v-model="form.capabilities" type="checkbox" :value="capability" /><span>{{ capability }}</span></label>
          </fieldset>

          <div class="platform-form-actions">
            <button class="platform-button secondary" type="button" @click="closeEditor">取消</button>
            <button class="platform-button primary" type="submit" :disabled="submitting">{{ submitting ? '保存中…' : '保存模型' }}</button>
          </div>
        </form>
      </section>
    </div>
  </PlatformShell>
</template>

<script>
import PlatformShell from '../components/PlatformShell.vue'
import { getGatewayModels, getGatewayProviders, saveGatewayModel, setGatewayModelEnabled } from '../utils/api'

const emptyForm = () => ({
  id: '', modelKey: '', providerAccountId: '', providerModelName: '', displayName: '',
  capabilities: ['CHAT', 'STREAM'], contextWindow: 32768, enabled: true, priority: 100, costLevel: 1
})

export default {
  name: 'ModelManagement',
  components: { PlatformShell },
  data() {
    return {
      providers: [], models: [], capabilityOptions: ['CHAT', 'STREAM', 'TOOLS', 'VISION', 'EMBEDDING'],
      editorOpen: false, editingExisting: false, form: emptyForm(), submitting: false,
      savingKey: '', message: '', messageType: 'success'
    }
  },
  mounted() { this.load() },
  methods: {
    async load() {
      try {
        // Provider 与模型没有数据依赖，采用并行加载避免串行等待两次网络往返。
        const [providers, models] = await Promise.all([getGatewayProviders(), getGatewayModels()])
        this.providers = providers
        this.models = [...models].sort((left, right) => left.priority - right.priority)
      } catch (error) {
        this.notify(error.response?.data?.message || '模型注册表加载失败', 'error')
      }
    },
    openCreate() {
      this.editingExisting = false
      this.form = emptyForm()
      this.form.providerAccountId = this.providers[0]?.id || ''
      this.editorOpen = true
    },
    openEdit(model) {
      this.editingExisting = true
      // 深拷贝 capabilities，避免编辑弹窗中的勾选操作直接修改表格原对象。
      this.form = { ...model, capabilities: [...model.capabilities] }
      this.editorOpen = true
    },
    closeEditor() { this.editorOpen = false },
    async submit() {
      if (!this.form.capabilities.length) {
        this.notify('至少选择一项模型能力', 'error')
        return
      }
      this.submitting = true
      try {
        const payload = {
          ...this.form,
          id: this.form.id || `model-${this.form.modelKey.replace(/[^a-zA-Z0-9-]/g, '-')}`
        }
        await saveGatewayModel(payload)
        this.closeEditor()
        await this.load()
        this.notify(`模型 ${payload.modelKey} 已保存`, 'success')
      } catch (error) {
        this.notify(error.response?.data?.message || '模型保存失败', 'error')
      } finally {
        this.submitting = false
      }
    },
    async toggle(model) {
      this.savingKey = model.modelKey
      try {
        await setGatewayModelEnabled(model.modelKey, !model.enabled)
        await this.load()
        this.notify(`模型 ${model.modelKey} 已${model.enabled ? '停用' : '启用'}`, 'success')
      } catch (error) {
        this.notify(error.response?.data?.message || '模型状态更新失败', 'error')
      } finally {
        this.savingKey = ''
      }
    },
    providerName(providerId) { return this.providers.find(provider => provider.id === providerId)?.name || providerId },
    number(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) },
    notify(message, type) {
      this.message = message
      this.messageType = type
      window.clearTimeout(this.messageTimer)
      this.messageTimer = window.setTimeout(() => { this.message = '' }, 4200)
    }
  },
  beforeUnmount() { window.clearTimeout(this.messageTimer) }
}
</script>
