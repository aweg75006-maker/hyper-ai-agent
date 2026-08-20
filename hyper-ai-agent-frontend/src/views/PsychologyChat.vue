<template>
  <div class="chat-container">
    <div class="chat-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M19 12H5M5 12l6-6m-6 6l6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <div class="page-heading">
        <span>CONVERSATIONAL CARE</span>
        <h2>心理咨询助手</h2>
      </div>
      <div class="header-meta">
        <span class="service-status"><i></i> 在线</span>
        <div class="chat-id">SESSION {{ chatId.slice(-8) }}</div>
      </div>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="welcome-message">
        <div class="welcome-icon">PS</div>
        <span class="welcome-kicker">PRIVATE CONVERSATION</span>
        <h3>我们可以从任何感受聊起</h3>
        <p>这里是一段安全、专注的对话。你可以描述最近困扰你的事情，助手会耐心倾听并给出支持。</p>
        <div class="prompt-suggestions">
          <button type="button" @click="inputMessage = '最近总是感到焦虑，我该怎么缓解？'">缓解焦虑</button>
          <button type="button" @click="inputMessage = '我最近的睡眠状态不太好'">改善睡眠</button>
          <button type="button" @click="inputMessage = '我想梳理一下最近的情绪'">梳理情绪</button>
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
      <div class="chat-input-wrapper">
        <input
          v-model="inputMessage"
          @keyup.enter="sendMessage"
          placeholder="描述你的感受或正在经历的事情…"
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
      <p class="input-caption">内容由 AI 生成，仅用于交流与支持，不能替代专业诊断。</p>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { chatWithPsyAppSse, generateChatId } from '../utils/api'

export default {
  name: 'PsychologyChat',
  setup() {
    const router = useRouter()
    const messages = ref([])
    const inputMessage = ref('')
    const isLoading = ref(false)
    const chatId = ref('')
    const messagesContainer = ref(null)
    let eventSource = null
    let currentAiMessageIndex = null

    onMounted(() => {
      chatId.value = generateChatId()
    })

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
      eventSource = chatWithPsyAppSse(
        userMessage,
        chatId.value,
        (data) => {
          // 累积接收到的数据
          if (messages.value[currentAiMessageIndex]) {
            messages.value[currentAiMessageIndex].content += data
            scrollToBottom()
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
      chatId,
      messagesContainer,
      sendMessage,
      goBack,
      formatMessage,
      formatTime
    }
  }
}
</script>
