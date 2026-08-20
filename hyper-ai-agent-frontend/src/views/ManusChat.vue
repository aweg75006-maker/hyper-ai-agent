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
                <span :class="['run-status', `status-${message.status.toLowerCase()}`]">
                  <i></i>{{ statusText(message.status) }}
                </span>
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
                    <span>STEP {{ summary.step }}</span>
                    <p>{{ summary.text }}</p>
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
                    <div class="tool-call-meta">
                      <strong>{{ toolCall.name }}</strong>
                      <span>STEP {{ toolCall.step }}</span>
                    </div>
                    <pre>{{ formatJson(toolCall.data) }}</pre>
                    <div v-if="toolCall.result" class="tool-result">
                      <span>工具返回</span>
                      <pre>{{ formatJson(toolCall.result) }}</pre>
                    </div>
                  </div>
                </div>
                <p v-else class="section-empty">当前尚未产生工具调用。</p>
              </section>

              <div v-if="message.error" class="run-error" role="alert">
                <strong>执行错误</strong>
                <p>{{ message.error }}</p>
              </div>

              <div v-if="message.humanReplies.length" class="human-reply-history">
                <span>人工回复</span>
                <p v-for="(reply, index) in message.humanReplies" :key="index">{{ reply }}</p>
              </div>

              <footer class="agent-run-footer">
                <span>{{ formatTime(message.time) }}</span>
                <span>{{ message.events.length }} EVENTS</span>
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
      humanReplies: [],
      events: [],
      error: ''
    })

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
          activeRunMessage.summaries.push({ step: event.step, text: event.summary })
          activeRunMessage.phase = `第 ${event.step} 步分析完成`
          break
        case 'TOOL_CALL':
          activeRunMessage.toolCalls.push({
            key: event.data.id || `${event.step}-${activeRunMessage.toolCalls.length}`,
            step: event.step,
            name: event.data.name || 'unknown',
            data: event.data,
            result: null
          })
          activeRunMessage.phase = `正在调用 ${event.data.name || '工具'}`
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
          activeRunMessage.phase = `${event.data.name || '工具'} 执行完成`
          break
        }
        case 'HUMAN_INPUT_REQUIRED':
          activeRunMessage.status = 'WAITING_HUMAN'
          activeRunMessage.phase = '等待人工确认后继续'
          askHumanQuestion.value = event.data.question || event.summary
          finishLocalRun('WAITING_HUMAN')
          break
        case 'RUN_COMPLETED':
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
      statusText
    }
  }
}
</script>
