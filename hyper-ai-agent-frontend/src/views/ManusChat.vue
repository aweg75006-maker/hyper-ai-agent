<template>
  <div class="chat-container manus-page">
    <div class="chat-header">
      <button class="back-btn" type="button" @click="goBack" aria-label="返回应用列表">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M19 12H5M5 12l6-6m-6 6l6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <div class="page-heading">
        <span>AUTONOMOUS EXECUTION</span>
        <h2>任务智能体</h2>
      </div>
      <div class="header-meta">
        <span :class="['service-status', { running: isLoading }]">
          <i></i>{{ isLoading ? 'Agent Running' : 'Agent Ready' }}
        </span>
      </div>
    </div>

    <div ref="messagesContainer" class="chat-messages">
      <div v-if="messages.length === 0" class="welcome-message manus-welcome">
        <div class="welcome-icon agent">AG</div>
        <span class="welcome-kicker">REACT AGENT</span>
        <h3>把目标交给智能体执行</h3>
        <p>每次运行都会分开展示分析状态、思考小结和工具调用 JSON，并可随时手动终止。</p>
        <div class="prompt-suggestions">
          <button type="button" @click="inputMessage = '帮我制定一份本周的学习计划'">制定计划</button>
          <button type="button" @click="inputMessage = '分析一个复杂问题并给出执行步骤'">分析任务</button>
          <button type="button" @click="inputMessage = '帮我整理一份行动清单'">行动清单</button>
        </div>
      </div>

      <template v-for="message in messages" :key="message.id">
        <div v-if="message.type === 'user'" class="message user">
          <div class="message-content">
            <div class="message-avatar">ME</div>
            <div class="message-bubble">
              <div class="message-text plain-message">{{ message.content }}</div>
              <div class="message-time">{{ formatTime(message.time) }}</div>
            </div>
          </div>
        </div>

        <div v-else class="message ai manus-run-message">
          <div class="message-content">
            <div class="message-avatar">AI</div>
            <article class="agent-run-card">
              <header class="agent-run-header">
                <div>
                  <span class="agent-run-label">RUN {{ shortRunId(message.runId) }}</span>
                  <h3>任务执行过程</h3>
                </div>
                <div class="agent-run-header-actions">
                  <button
                    v-if="message.summaries.length || message.toolCalls.length || message.humanReplies.length"
                    type="button"
                    class="collapse-all-btn"
                    @click="toggleAllDetails(message)"
                  >
                    {{ isAllCollapsed(message) ? '展开步骤' : '折叠步骤' }}
                    <svg :class="{ collapsed: isAllCollapsed(message) }" viewBox="0 0 20 20" fill="none">
                      <path d="m6 8 4 4 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </button>
                  <span :class="['run-status', `status-${message.status.toLowerCase()}`]">
                    <i></i>{{ statusText(message.status) }}
                  </span>
                </div>
              </header>

              <!-- 第一部分：只呈现可观察的分析进度，不暴露模型内部隐藏思维链。 -->
              <section class="execution-section thinking-section">
                <div class="execution-section-heading">
                  <span class="section-index">01</span>
                  <div>
                    <h4>深度思考</h4>
                    <p>分析状态与当前执行步骤</p>
                  </div>
                </div>
                <div class="thinking-progress">
                  <span :class="['thinking-orb', { active: message.status === 'RUNNING' }]"></span>
                  <div>
                    <strong>{{ message.phase || '等待开始分析' }}</strong>
                    <p v-if="message.currentStep">当前第 {{ message.currentStep }} 步</p>
                  </div>
                </div>
              </section>

              <!-- 第二部分：展示模型明确输出或系统生成的可审计阶段小结。 -->
              <section class="execution-section">
                <div class="execution-section-heading">
                  <span class="section-index">02</span>
                  <div>
                    <h4>思考小结</h4>
                    <p>本步骤得出的结论与下一项行动</p>
                  </div>
                </div>
                <div v-if="message.summaries.length" class="summary-list">
                  <div v-for="(summary, index) in message.summaries" :key="`${summary.step}-${index}`" class="summary-item">
                    <button type="button" class="detail-toggle" @click="summary.expanded = !summary.expanded">
                      <span>步骤 {{ summary.step }}</span>
                      <span class="detail-toggle-action">
                        {{ summary.expanded ? '收起' : '展开' }}
                        <svg :class="{ collapsed: !summary.expanded }" viewBox="0 0 20 20" fill="none">
                          <path d="m6 8 4 4 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                      </span>
                    </button>
                    <p v-show="summary.expanded">{{ summary.text }}</p>
                  </div>
                </div>
                <p v-else class="section-empty">模型完成当前分析后，小结会显示在这里。</p>
              </section>

              <!-- 第三部分：工具名与参数保持 JSON 结构，便于开发和审计。 -->
              <section class="execution-section tool-section">
                <div class="execution-section-heading">
                  <span class="section-index">03</span>
                  <div>
                    <h4>工具调用 JSON</h4>
                    <p>模型请求的工具、参数与执行结果</p>
                  </div>
                </div>
                <div v-if="message.toolCalls.length" class="tool-call-list">
                  <div v-for="toolCall in message.toolCalls" :key="toolCall.key" class="tool-call-card">
                    <button type="button" class="tool-call-meta" @click="toolCall.expanded = !toolCall.expanded">
                      <strong>{{ toolDisplayName(toolCall.name) }}</strong>
                      <span class="tool-call-step">
                        步骤 {{ toolCall.step }} · {{ toolCall.expanded ? '收起' : '展开' }}
                        <svg :class="{ collapsed: !toolCall.expanded }" viewBox="0 0 20 20" fill="none">
                          <path d="m6 8 4 4 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                      </span>
                    </button>
                    <div v-show="toolCall.expanded">
                      <pre>{{ formatJson(toolCall.data) }}</pre>
                      <div v-if="toolCall.result" class="tool-result">
                        <span>工具返回</span>
                        <pre>{{ formatJson(toolCall.result) }}</pre>
                      </div>
                    </div>
                  </div>
                </div>
                <p v-else class="section-empty">当前尚未产生工具调用。</p>
              </section>

              <!-- 最终结论独立于步骤列表，正常完成后始终优先展示整体结果。 -->
              <section v-if="message.finalSummary" class="final-summary-section">
                <div class="final-summary-heading">
                  <span>完成</span>
                  <div>
                    <h4>最终结论</h4>
                    <p>任务智能体对本次运行的整体总结</p>
                  </div>
                </div>
                <div class="final-summary-content">{{ message.finalSummary }}</div>
              </section>

              <div v-if="message.error" class="run-error" role="alert">
                <strong>执行错误</strong>
                <p>{{ message.error }}</p>
              </div>

              <div v-if="message.humanReplies.length" class="human-reply-history">
                <button type="button" class="human-reply-toggle" @click="message.humanRepliesExpanded = !message.humanRepliesExpanded">
                  <span>人工回复 · {{ message.humanReplies.length }} 条</span>
                  <span>
                    {{ message.humanRepliesExpanded ? '收起' : '展开' }}
                    <svg :class="{ collapsed: !message.humanRepliesExpanded }" viewBox="0 0 20 20" fill="none">
                      <path d="m6 8 4 4 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </span>
                </button>
                <div v-show="message.humanRepliesExpanded">
                  <p v-for="(reply, index) in message.humanReplies" :key="index">{{ reply }}</p>
                </div>
              </div>

              <footer class="agent-run-footer">
                <span>{{ formatTime(message.time) }}</span>
                <span>{{ message.events.length }} 个事件</span>
              </footer>
            </article>
          </div>
        </div>
      </template>
    </div>

    <div class="chat-input-container manus-input-container">
      <div v-if="askHumanQuestion" class="ask-human-container">
        <div class="ask-human-question">
          <p>需要人工确认</p>
          <div class="question-text plain-message">{{ askHumanQuestion }}</div>
        </div>
        <div class="ask-human-input-wrapper">
          <input
            v-model="askHumanInput"
            class="ask-human-input"
            placeholder="输入确认信息…"
            @keyup.enter="replyToAskHuman"
          />
          <button
            type="button"
            class="ask-human-send-btn"
            :disabled="!askHumanInput.trim()"
            aria-label="提交人工回复"
            @click="replyToAskHuman"
          >
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>

      <div v-else class="chat-input-wrapper manus-input-wrapper">
        <input
          v-model="inputMessage"
          class="chat-input"
          placeholder="描述一个需要规划和执行的任务…"
          :disabled="isLoading"
          @keyup.enter="sendMessage"
        />
        <button
          v-if="isLoading"
          type="button"
          class="stop-run-btn"
          aria-label="终止当前任务"
          @click="stopCurrentRun"
        >
          <span></span>终止
        </button>
        <button
          v-else
          type="button"
          class="send-btn"
          :disabled="!inputMessage.trim()"
          aria-label="发送任务"
          @click="sendMessage"
        >
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
      <p v-if="!askHumanQuestion" class="input-caption">
        运行期间可手动终止；关键决策会暂停并请求人工确认。
      </p>
    </div>
  </div>
</template>

<script>
import { nextTick, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { cancelManusRun, chatWithManus, resumeManusRun } from '../utils/api'

export default {
  name: 'ManusChat',
  setup() {
    const router = useRouter()
    const messages = ref([])
    const inputMessage = ref('')
    const isLoading = ref(false)
    const messagesContainer = ref(null)
    const askHumanQuestion = ref('')
    const askHumanInput = ref('')

    let eventSource = null
    let activeRunId = ''
    let activeRunMessage = null

    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    const createRunId = () => {
      // randomUUID 在 localhost 和 HTTPS 环境均可用；兜底值仍满足服务端格式约束。
      if (globalThis.crypto?.randomUUID) {
        return `run_${globalThis.crypto.randomUUID().replaceAll('-', '')}`
      }
      return `run_${Date.now()}_${Math.random().toString(16).slice(2)}`
    }

    const createRunMessage = (runId) => ({
      id: `message_${runId}`,
      type: 'run',
      runId,
      time: new Date(),
      status: 'RUNNING',
      phase: '正在建立运行上下文',
      currentStep: 0,
      summaries: [],
      toolCalls: [],
      finalSummary: '',
      humanReplies: [],
      humanRepliesExpanded: false,
      events: [],
      error: ''
    })

    // 结束任务或收到最终结论时统一压缩执行细节，让用户先看到整体结果。
    const collapseAllDetails = (message) => {
      message.summaries.forEach((summary) => { summary.expanded = false })
      message.toolCalls.forEach((toolCall) => { toolCall.expanded = false })
      message.humanRepliesExpanded = false
    }

    // 汇总三类可折叠内容的状态，用于驱动右上角“展开/折叠步骤”按钮。
    const isAllCollapsed = (message) =>
      message.summaries.every((summary) => !summary.expanded) &&
      message.toolCalls.every((toolCall) => !toolCall.expanded) &&
      !message.humanRepliesExpanded

    // 全量切换只改变展示状态，不修改 SSE 事件与工具调用原始数据。
    const toggleAllDetails = (message) => {
      const shouldExpand = isAllCollapsed(message)
      message.summaries.forEach((summary) => { summary.expanded = shouldExpand })
      message.toolCalls.forEach((toolCall) => { toolCall.expanded = shouldExpand })
      message.humanRepliesExpanded = shouldExpand && message.humanReplies.length > 0
    }

    const finishLocalRun = (status) => {
      isLoading.value = false
      if (activeRunMessage && status) {
        activeRunMessage.status = status
      }
      eventSource = null
      if (status !== 'WAITING_HUMAN') {
        activeRunId = ''
      }
      scrollToBottom()
    }

    const handleRunEvent = (event) => {
      if (!activeRunMessage || event.runId !== activeRunMessage.runId) {
        return
      }
      activeRunMessage.events.push(event)
      activeRunMessage.currentStep = Math.max(activeRunMessage.currentStep, event.step || 0)

      switch (event.type) {
        case 'RUN_STARTED':
        case 'RUN_RESUMED':
          activeRunMessage.status = 'RUNNING'
          activeRunMessage.phase = event.summary
          break
        case 'THINKING_STARTED':
          activeRunMessage.status = 'RUNNING'
          activeRunMessage.phase = event.summary
          break
        case 'THINKING_SUMMARY':
          // 新步骤到达时自动收起旧步骤，只展开最新内容，避免长任务无限拉高页面。
          activeRunMessage.summaries.forEach((summary) => { summary.expanded = false })
          activeRunMessage.summaries.push({ step: event.step, text: event.summary, expanded: true })
          activeRunMessage.phase = `第 ${event.step} 步分析完成`
          break
        case 'FINAL_SUMMARY':
          activeRunMessage.finalSummary = event.summary
          activeRunMessage.phase = '已生成最终结论'
          collapseAllDetails(activeRunMessage)
          break
        case 'TOOL_CALL':
          activeRunMessage.toolCalls.forEach((toolCall) => { toolCall.expanded = false })
          activeRunMessage.toolCalls.push({
            key: event.data.id || `${event.step}-${activeRunMessage.toolCalls.length}`,
            step: event.step,
            name: event.data.name || 'unknown',
            data: event.data,
            result: null,
            expanded: true
          })
          activeRunMessage.phase = `正在调用${toolDisplayName(event.data.name)}`
          break
        case 'TOOL_RESULT': {
          // 优先按工具调用 ID 关联；供应商未返回 ID 时，再关联最后一个同名且未完成的调用。
          const toolCall = [...activeRunMessage.toolCalls].reverse().find((item) =>
            (event.data.toolCallId && item.data.id === event.data.toolCallId) ||
            (!event.data.toolCallId && item.name === event.data.name && !item.result)
          )
          if (toolCall) {
            toolCall.result = event.data
          }
          activeRunMessage.phase = `${toolDisplayName(event.data.name)}执行完成`
          break
        }
        case 'HUMAN_INPUT_REQUIRED':
          activeRunMessage.status = 'WAITING_HUMAN'
          activeRunMessage.phase = '等待人工确认后继续'
          askHumanQuestion.value = event.data.question || event.summary
          finishLocalRun('WAITING_HUMAN')
          break
        case 'RUN_COMPLETED':
          // 兼容旧服务端或异常缺失 FINAL_SUMMARY 的情况，至少把最后一步小结提升为整体结论。
          if (!activeRunMessage.finalSummary && activeRunMessage.summaries.length) {
            activeRunMessage.finalSummary = activeRunMessage.summaries.at(-1).text
          }
          collapseAllDetails(activeRunMessage)
          activeRunMessage.phase = event.summary
          finishLocalRun('COMPLETED')
          break
        case 'RUN_CANCELLED':
          activeRunMessage.phase = event.summary
          finishLocalRun('CANCELLED')
          break
        case 'RUN_ERROR':
          activeRunMessage.error = event.summary
          activeRunMessage.phase = '运行失败'
          finishLocalRun('ERROR')
          break
      }
      scrollToBottom()
    }

    const handleStreamError = (error) => {
      console.error('任务智能体事件流异常:', error)
      if (activeRunMessage && !['COMPLETED', 'CANCELLED', 'WAITING_HUMAN'].includes(activeRunMessage.status)) {
        activeRunMessage.status = 'ERROR'
        activeRunMessage.error = '运行事件流连接失败，请重新发起任务。'
        activeRunMessage.phase = '连接已中断'
      }
      finishLocalRun(activeRunMessage?.status || 'ERROR')
    }

    const openRunStream = (streamFactory) => {
      isLoading.value = true
      eventSource = streamFactory(handleRunEvent, handleStreamError, (reason) => {
        // 终态已由最后一条结构化事件更新，这里只清理浏览器连接引用。
        if (reason === 'HUMAN_INPUT_REQUIRED') {
          finishLocalRun('WAITING_HUMAN')
        } else if (activeRunMessage?.status === 'RUNNING') {
          finishLocalRun(reason === 'RUN_ERROR' ? 'ERROR' : activeRunMessage.status)
        }
        eventSource = null
      })
    }

    const sendMessage = () => {
      const userMessage = inputMessage.value.trim()
      if (!userMessage || isLoading.value) {
        return
      }

      inputMessage.value = ''
      messages.value.push({
        id: `user_${Date.now()}`,
        type: 'user',
        content: userMessage,
        time: new Date()
      })

      activeRunId = createRunId()
      activeRunMessage = createRunMessage(activeRunId)
      messages.value.push(activeRunMessage)
      askHumanQuestion.value = ''
      scrollToBottom()

      openRunStream((onEvent, onError, onClose) =>
        chatWithManus(activeRunId, userMessage, onEvent, onError, onClose)
      )
    }

    const replyToAskHuman = () => {
      const answer = askHumanInput.value.trim()
      if (!answer || !activeRunId || !activeRunMessage) {
        return
      }

      // 回复保存在同一张运行卡片里，强调这是暂停任务的继续，而不是一条新任务。
      activeRunMessage.humanReplies.push(answer)
      activeRunMessage.humanRepliesExpanded = true
      askHumanInput.value = ''
      askHumanQuestion.value = ''
      activeRunMessage.status = 'RUNNING'
      activeRunMessage.phase = '正在提交人工回复'

      openRunStream((onEvent, onError, onClose) =>
        resumeManusRun(activeRunId, answer, onEvent, onError, onClose)
      )
    }

    const stopCurrentRun = async () => {
      if (!activeRunId || !isLoading.value) {
        return
      }
      const runIdToCancel = activeRunId
      try {
        // 先请求服务端取消，等待 RUN_CANCELLED 事件；随后再关闭本地连接作为兜底。
        await cancelManusRun(runIdToCancel)
      } catch (error) {
        console.error('终止任务失败:', error)
        if (activeRunMessage) {
          activeRunMessage.error = '服务端终止请求失败，请稍后重试。'
        }
      } finally {
        eventSource?.close()
        if (activeRunMessage?.status === 'RUNNING') {
          activeRunMessage.phase = '运行已由用户终止'
          finishLocalRun('CANCELLED')
        }
      }
    }

    const closeCurrentConnection = () => {
      eventSource?.close()
      eventSource = null
    }

    const goBack = () => {
      // 离开页面时不能只关闭浏览器连接，否则后台 Agent 仍可能继续消耗模型额度。
      if (activeRunId && isLoading.value) {
        cancelManusRun(activeRunId).catch(() => {})
      }
      closeCurrentConnection()
      router.push('/')
    }

    const formatTime = (date) => new Date(date).toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit'
    })
    const formatJson = (value) => JSON.stringify(value, null, 2)
    const shortRunId = (runId) => runId.slice(-8).toUpperCase()
    const toolDisplayName = (name) => ({
      askHuman: '询问用户',
      confirmAction: '请求操作确认',
      selectOption: '请求用户选择',
      doTerminate: '完成任务',
      fileOperation: '文件操作',
      webSearch: '联网搜索',
      webScraping: '网页读取',
      resourceDownload: '资源下载',
      terminalOperation: '终端操作',
      pdfGeneration: '生成 PDF'
    }[name] || name || '工具')
    const statusText = (status) => ({
      RUNNING: '运行中',
      WAITING_HUMAN: '等待确认',
      COMPLETED: '已完成',
      CANCELLED: '已终止',
      ERROR: '失败'
    }[status] || status)

    onUnmounted(closeCurrentConnection)

    return {
      messages,
      inputMessage,
      isLoading,
      messagesContainer,
      askHumanQuestion,
      askHumanInput,
      sendMessage,
      replyToAskHuman,
      stopCurrentRun,
      goBack,
      formatTime,
      formatJson,
      shortRunId,
      toolDisplayName,
      isAllCollapsed,
      toggleAllDetails,
      statusText
    }
  }
}
</script>
