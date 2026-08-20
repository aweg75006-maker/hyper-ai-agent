<template>
  <PlatformShell
    active="operations"
    eyebrow="GATEWAY OBSERVABILITY"
    title="运行中心"
    subtitle="从请求总览下钻到模型用量、Fallback 与 Trace 审计事件"
  >
    <template #actions>
      <select v-model.number="rangeHours" class="platform-select" @change="refresh">
        <option :value="6">最近 6 小时</option>
        <option :value="24">最近 24 小时</option>
        <option :value="168">最近 7 天</option>
        <option :value="720">最近 30 天</option>
      </select>
      <button class="platform-button secondary" type="button" :disabled="loading" @click="refresh">
        {{ loading ? '刷新中…' : '刷新数据' }}
      </button>
    </template>

    <div v-if="error" class="platform-alert error">
      <strong>数据加载失败</strong><span>{{ error }}</span>
    </div>

    <section class="metric-grid" aria-label="Gateway 核心指标">
      <article class="metric-card">
        <span class="metric-label">REQUESTS</span>
        <strong>{{ number(overview.requestCount) }}</strong>
        <small>{{ dateRangeLabel }}</small>
      </article>
      <article class="metric-card success">
        <span class="metric-label">SUCCESS RATE</span>
        <strong>{{ percent(overview.successRate) }}</strong>
        <small>{{ number(overview.successCount) }} 次成功 · {{ number(overview.failedCount) }} 次失败</small>
      </article>
      <article class="metric-card violet">
        <span class="metric-label">TOTAL TOKENS</span>
        <strong>{{ compactNumber(overview.totalTokens) }}</strong>
        <small>供应商 Usage 归一化统计</small>
      </article>
      <article class="metric-card amber">
        <span class="metric-label">AVG LATENCY</span>
        <strong>{{ duration(overview.averageDurationMs) }}</strong>
        <small>{{ number(overview.fallbackCount) }} 次 Fallback</small>
      </article>
    </section>

    <section class="operations-grid">
      <article class="platform-panel traffic-panel">
        <div class="panel-heading-row">
          <div><span class="panel-kicker">REQUEST VOLUME</span><h2>请求趋势</h2></div>
          <span class="panel-meta">{{ seriesBucket === 'DAY' ? '按天' : '按小时' }}</span>
        </div>

        <div v-if="series.length" class="traffic-chart">
          <svg viewBox="0 0 720 180" preserveAspectRatio="none" aria-label="请求数趋势图">
            <defs>
              <linearGradient id="trafficArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#766df8" stop-opacity=".28" />
                <stop offset="1" stop-color="#766df8" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path :d="areaPath" fill="url(#trafficArea)" />
            <polyline :points="chartPoints" fill="none" stroke="#6b61ed" stroke-width="3" vector-effect="non-scaling-stroke" />
          </svg>
          <div class="chart-axis"><span>{{ firstBucket }}</span><span>{{ lastBucket }}</span></div>
        </div>
        <div v-else class="platform-empty compact">当前时间范围内还没有 Gateway 请求</div>
      </article>

      <article class="platform-panel runtime-panel">
        <div class="panel-heading-row">
          <div><span class="panel-kicker">RUNTIME STATE</span><h2>实时状态</h2></div>
          <span class="health-pill"><i></i> HEALTHY</span>
        </div>
        <div class="runtime-stat-list">
          <div><span>活跃流</span><strong>{{ number(overview.activeStreams) }}</strong></div>
          <div><span>预估费用</span><strong>{{ costLabel }}</strong></div>
          <div><span>Fallback</span><strong>{{ number(overview.fallbackCount) }}</strong></div>
          <div><span>Trace 采样</span><strong>100%</strong></div>
        </div>
        <p class="runtime-note">费用仅在模型价格版本生效后计算；未配置价格时不会显示伪造的 0 元费用。</p>
      </article>
    </section>

    <section class="platform-panel table-panel">
      <div class="panel-heading-row">
        <div><span class="panel-kicker">MODEL USAGE</span><h2>模型用量</h2></div>
        <span class="panel-meta">按请求量排序</span>
      </div>
      <div class="platform-table-wrap">
        <table class="platform-table">
          <thead><tr><th>模型</th><th>Provider</th><th>请求</th><th>成功率</th><th>Token</th><th>平均耗时</th></tr></thead>
          <tbody>
            <tr v-for="item in modelUsage" :key="`${item.dimension}-${item.providerType}`">
              <td><strong>{{ item.dimension }}</strong></td>
              <td><span class="provider-badge">{{ item.providerType }}</span></td>
              <td>{{ number(item.requestCount) }}</td>
              <td>{{ percent(item.requestCount ? item.successCount / item.requestCount : 0) }}</td>
              <td>{{ compactNumber(item.totalTokens) }}</td>
              <td>{{ duration(item.averageDurationMs) }}</td>
            </tr>
            <tr v-if="!modelUsage.length"><td colspan="6"><div class="platform-empty compact">暂无模型用量</div></td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="platform-panel audit-panel">
      <div class="panel-heading-row">
        <div><span class="panel-kicker">AUDIT TRAIL</span><h2>最近审计事件</h2></div>
        <span class="panel-meta">不记录 Prompt、回复正文和密钥</span>
      </div>
      <div class="audit-list">
        <button
          v-for="event in auditEvents"
          :key="event.id"
          class="audit-row"
          type="button"
          :disabled="!event.traceId"
          @click="openTrace(event.traceId)"
        >
          <span :class="['audit-type-dot', auditTone(event.eventType)]"></span>
          <span class="audit-event-copy">
            <strong>{{ eventLabel(event.eventType) }}</strong>
            <small>{{ event.routeKey || '管理操作' }} · {{ event.modelKey || event.metadata?.resourceId || '—' }}</small>
          </span>
          <code>{{ shortTrace(event.traceId) }}</code>
          <time>{{ formatTime(event.createdAt) }}</time>
        </button>
        <div v-if="!auditEvents.length" class="platform-empty">暂无审计事件；发起一次 Gateway 请求后即可看到完整链路。</div>
      </div>
    </section>

    <div v-if="traceOpen" class="trace-overlay" @click.self="closeTrace">
      <aside class="trace-drawer" aria-label="Trace 详情">
        <div class="trace-drawer-head">
          <div><span class="panel-kicker">TRACE DETAIL</span><h2>{{ shortTrace(selectedTraceId, 18) }}</h2></div>
          <button type="button" @click="closeTrace">×</button>
        </div>
        <div v-if="traceLoading" class="platform-empty">正在加载 Trace…</div>
        <div v-else class="trace-timeline">
          <div v-for="event in traceEvents" :key="event.id" class="trace-step">
            <span></span>
            <div><strong>{{ eventLabel(event.eventType) }}</strong><small>{{ formatTime(event.createdAt) }}</small></div>
            <p>{{ event.routeKey || 'admin' }} / {{ event.modelKey || event.metadata?.resourceId || '—' }}</p>
          </div>
          <div v-if="!traceEvents.length" class="platform-empty">没有找到该 Trace 的审计事件</div>
        </div>
      </aside>
    </div>
  </PlatformShell>
</template>

<script>
import PlatformShell from '../components/PlatformShell.vue'
import {
  gatewayTimeRange,
  getGatewayAuditEvents,
  getGatewayDimensions,
  getGatewayOverview,
  getGatewaySeries,
  getGatewayTrace
} from '../utils/api'

export default {
  name: 'OperationCenter',
  components: { PlatformShell },
  data() {
    return {
      rangeHours: 24,
      overview: {},
      series: [],
      modelUsage: [],
      auditEvents: [],
      loading: false,
      error: '',
      selectedTraceId: '',
      traceEvents: [],
      traceLoading: false,
      traceOpen: false
    }
  },
  computed: {
    seriesBucket() { return this.rangeHours > 168 ? 'DAY' : 'HOUR' },
    dateRangeLabel() { return this.rangeHours >= 24 ? `最近 ${this.rangeHours / 24} 天` : `最近 ${this.rangeHours} 小时` },
    chartCoordinates() {
      if (!this.series.length) return []
      const max = Math.max(...this.series.map(item => item.requestCount), 1)
      // SVG 使用固定逻辑坐标，浏览器缩放时不会改变数据比例。
      return this.series.map((item, index) => ({
        x: this.series.length === 1 ? 360 : (index / (this.series.length - 1)) * 720,
        y: 160 - (item.requestCount / max) * 135
      }))
    },
    chartPoints() { return this.chartCoordinates.map(point => `${point.x},${point.y}`).join(' ') },
    areaPath() {
      if (!this.chartCoordinates.length) return ''
      const line = this.chartCoordinates.map(point => `L ${point.x} ${point.y}`).join(' ')
      return `M 0 170 ${line} L 720 170 Z`
    },
    firstBucket() { return this.series.length ? this.formatBucket(this.series[0].bucket) : '—' },
    lastBucket() { return this.series.length ? this.formatBucket(this.series[this.series.length - 1].bucket) : '—' },
    costLabel() {
      const costs = this.overview.estimatedCosts || []
      return costs.length ? costs.map(item => `${item.currency} ${Number(item.amount).toFixed(4)}`).join(' / ') : '未配置价格'
    }
  },
  mounted() { this.refresh() },
  methods: {
    async refresh() {
      this.loading = true
      this.error = ''
      const range = gatewayTimeRange(this.rangeHours)
      try {
        // 四个查询彼此独立，并行请求可以显著降低运行中心首屏等待时间。
        const [overview, series, modelUsage, auditEvents] = await Promise.all([
          getGatewayOverview(range),
          getGatewaySeries(range, this.seriesBucket),
          getGatewayDimensions(range, 'MODEL'),
          getGatewayAuditEvents(range, 30)
        ])
        this.overview = overview
        this.series = series
        this.modelUsage = modelUsage
        this.auditEvents = auditEvents
      } catch (error) {
        this.error = error.response?.data?.message || error.message || '未知错误'
      } finally {
        this.loading = false
      }
    },
    async openTrace(traceId) {
      if (!traceId) return
      this.selectedTraceId = traceId
      this.traceOpen = true
      this.traceLoading = true
      try {
        this.traceEvents = await getGatewayTrace(traceId)
      } catch (error) {
        this.traceEvents = []
        this.error = error.response?.data?.message || 'Trace 加载失败'
      } finally {
        this.traceLoading = false
      }
    },
    closeTrace() { this.traceOpen = false },
    number(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) },
    compactNumber(value) { return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(Number(value || 0)) },
    percent(value) { return `${(Number(value || 0) * 100).toFixed(1)}%` },
    duration(value) { return `${Math.round(Number(value || 0))} ms` },
    formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' },
    formatBucket(value) { return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit' }) : '—' },
    shortTrace(value, length = 12) { return value ? `${value.slice(0, length)}…` : '无 Trace' },
    auditTone(type) {
      if (type?.includes('FAILED') || type?.includes('REJECTED')) return 'danger'
      if (type?.includes('FALLBACK') || type?.includes('CANCELLED')) return 'warning'
      if (type?.includes('SUCCEEDED')) return 'success'
      return 'neutral'
    },
    eventLabel(type) {
      const labels = {
        REQUEST_ACCEPTED: '请求进入网关', ROUTE_SELECTED: '路由选择模型',
        FALLBACK_TRIGGERED: '触发模型降级', REQUEST_SUCCEEDED: '请求执行成功',
        REQUEST_FAILED: '请求执行失败', REQUEST_REJECTED: '配额拒绝请求',
        STREAM_CANCELLED: '流式请求取消', PROVIDER_CONFIG_CHANGED: 'Provider 配置变更',
        MODEL_CONFIG_CHANGED: '模型配置变更', ROUTE_CONFIG_CHANGED: '路由策略变更',
        API_KEY_CREATED: '创建 API Key', API_KEY_ROTATED: '轮换 API Key',
        PRICE_CONFIG_CHANGED: '模型价格变更'
      }
      return labels[type] || type
    }
  }
}
</script>
