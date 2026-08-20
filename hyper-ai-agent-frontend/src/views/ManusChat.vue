<template>
  <div class="chat-container">
    <div class="chat-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M19 12H5M5 12l6-6m-6 6l6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <div class="page-heading">
        <span>AUTONOMOUS EXECUTION</span>
        <h2>任务智能体</h2>
      </div>
      <div class="header-meta">
        <span class="service-status"><i></i> Agent Ready</span>
      </div>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="welcome-message">
        <div class="welcome-icon agent">AG</div>
        <span class="welcome-kicker">REACT AGENT</span>
        <h3>把目标交给智能体执行</h3>
        <p>描述你想完成的任务。智能体会拆解步骤、选择工具并执行，在关键节点向你确认。</p>
        <div class="prompt-suggestions">
          <button type="button" @click="inputMessage = '帮我制定一份本周的学习计划'">制定计划</button>
          <button type="button" @click="inputMessage = '分析一个复杂问题并给出执行步骤'">分析任务</button>
          <button type="button" @click="inputMessage = '帮我整理一份行动清单'">行动清单</button>
        </div>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['message', msg.type]"
      >
        <div class="message-content">
          <div class="message-avatar">
            <span v-if="msg.type === 'user'">ME</span>
            <span v-else>AI</span>
          </div>
          <div class="message-bubble">
            <div class="message-text" v-html="formatMessage(msg.content)"></div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="message ai">
        <div class="message-content">
          <div class="message-avatar">AI</div>
          <div class="message-bubble">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-container">
      <!-- AskHuman 交互框 -->
      <div v-if="askHumanQuestion" class="ask-human-container">
        <div class="ask-human-question">
          <p>需要人工确认</p>
          <div class="question-text" v-html="formatMessage(askHumanQuestion)"></div>
        </div>
        <div class="ask-human-input-wrapper">
          <input
            v-model="askHumanInput"
            @keyup.enter="replyToAskHuman"
            placeholder="输入确认信息…"
            class="ask-human-input"
          />
          <button
            @click="replyToAskHuman"
            class="ask-human-send-btn"
            :disabled="!askHumanInput.trim()"
          >
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 普通聊天输入框 -->
      <div v-else class="chat-input-wrapper">
        <input
          v-model="inputMessage"
          @keyup.enter="sendMessage"
          placeholder="描述一个需要规划和执行的任务…"
          class="chat-input"
          :disabled="isLoading"
        />
        <button
          @click="sendMessage"
          class="send-btn"
          :disabled="!inputMessage.trim() || isLoading"
        >
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
      <p v-if="!askHumanQuestion" class="input-caption">Agent 最多执行有限步骤，关键决策会请求人工确认。</p>
    </div>
  </div>
</template>

<script>
import { ref, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { chatWithManus } from '../utils/api'

export default {
  name: 'ManusChat',
  setup() {
    const router = useRouter()
    const messages = ref([])
    const inputMessage = ref('')
    const isLoading = ref(false)
    const messagesContainer = ref(null)
    const askHumanQuestion = ref('') // 存储 askHuman 的问题
    const askHumanInput = ref('') // 存储用户的回复
    let eventSource = null
    let currentAiMessageIndex = null

    onUnmounted(() => {
      if (eventSource) {
        eventSource.close()
      }
    })

    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
        }
      })
    }

    const formatMessage = (text) => {
      return text.replace(/\n/g, '<br>')
    }

    const formatTime = (date) => {
      return new Date(date).toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit'
      })
    }

    const sendMessage = () => {
      if (!inputMessage.value.trim() || isLoading.value) {
        return
      }

      const userMessage = inputMessage.value.trim()
      inputMessage.value = ''

      // 添加用户消息
      messages.value.push({
        type: 'user',
        content: userMessage,
        time: new Date()
      })

      scrollToBottom()

      // 添加AI消息占位符
      currentAiMessageIndex = messages.value.length
      messages.value.push({
        type: 'ai',
        content: '',
        time: new Date()
      })

      isLoading.value = true
      scrollToBottom()

      // 调用SSE接口
      eventSource = chatWithManus(
        userMessage,
        (data) => {
          // 累积接收到的数据
          if (messages.value[currentAiMessageIndex]) {
            messages.value[currentAiMessageIndex].content += data
            scrollToBottom()

            // 检查是否需要用户输入
            if (data.includes('[需要用户输入]')) {
              // 提取问题内容
              const questionMatch = data.match(/\[需要用户输入\]\s*(.*)/s)
              if (questionMatch) {
                askHumanQuestion.value = questionMatch[1]
                isLoading.value = false
              }
            }
          }
        },
        (error) => {
          // 真正的错误处理
          console.error('Error:', error)
          isLoading.value = false
          if (messages.value[currentAiMessageIndex]) {
            messages.value[currentAiMessageIndex].content += '\n\n[连接错误，请重试]'
          }
          eventSource = null
        },
        () => {
          // 正常关闭处理
          isLoading.value = false
          eventSource = null
        }
      )
    }

    // 回复 askHuman 问题
    const replyToAskHuman = () => {
      if (!askHumanInput.value.trim()) {
        return
      }

      const userReply = askHumanInput.value.trim()
      askHumanInput.value = ''
      askHumanQuestion.value = ''

      // 将用户的回复作为新消息发送
      isLoading.value = true

      // 添加用户消息
      messages.value.push({
        type: 'user',
        content: userReply,
        time: new Date()
      })

      scrollToBottom()

      // 添加AI消息占位符
      currentAiMessageIndex = messages.value.length
      messages.value.push({
        type: 'ai',
        content: '',
        time: new Date()
      })

      scrollToBottom()

      // 继续调用SSE接口
      eventSource = chatWithManus(
        userReply,
        (data) => {
          if (messages.value[currentAiMessageIndex]) {
            messages.value[currentAiMessageIndex].content += data
            scrollToBottom()

            // 检查是否需要用户输入
            if (data.includes('[需要用户输入]')) {
              const questionMatch = data.match(/\[需要用户输入\]\s*(.*)/s)
              if (questionMatch) {
                askHumanQuestion.value = questionMatch[1]
                isLoading.value = false
              }
            }
          }
        },
        (error) => {
          console.error('Error:', error)
          isLoading.value = false
          if (messages.value[currentAiMessageIndex]) {
            messages.value[currentAiMessageIndex].content += '\n\n[连接错误，请重试]'
          }
          eventSource = null
        },
        () => {
          isLoading.value = false
          eventSource = null
        }
      )
    }

    const goBack = () => {
      if (eventSource) {
        eventSource.close()
      }
      router.push('/')
    }

    return {
      messages,
      inputMessage,
      isLoading,
      messagesContainer,
      askHumanQuestion,
      askHumanInput,
      sendMessage,
      replyToAskHuman,
      goBack,
      formatMessage,
      formatTime
    }
  }
}
</script>
