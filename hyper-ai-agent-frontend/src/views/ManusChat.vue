<template>
  <div class="chat-container">
    <div class="chat-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M19 12H5M5 12l6-6m-6 6l6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <h2>AI 超级智能体</h2>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="welcome-message">
        <p>👋 您好！我是AI超级智能体，我可以帮您解决各种问题。有什么我可以帮助您的吗？</p>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        :class="['message', msg.type]"
      >
        <div class="message-content">
          <div class="message-avatar">
            <span v-if="msg.type === 'user'">👤</span>
            <span v-else>🤖</span>
          </div>
          <div class="message-bubble">
            <div class="message-text" v-html="formatMessage(msg.content)"></div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="message ai">
        <div class="message-content">
          <div class="message-avatar">🤖</div>
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
          <p>🤖 AI 需要您的帮助：</p>
          <div class="question-text" v-html="formatMessage(askHumanQuestion)"></div>
        </div>
        <div class="ask-human-input-wrapper">
          <input
            v-model="askHumanInput"
            @keyup.enter="replyToAskHuman"
            placeholder="请输入您的回复..."
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
          placeholder="输入您的消息..."
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

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f5f5;
}

.chat-header {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  padding: 15px 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.back-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.back-btn svg {
  width: 20px;
  height: 20px;
}

.chat-header h2 {
  flex: 1;
  font-size: 1.3em;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.welcome-message {
  text-align: center;
  padding: 40px 20px;
  color: #666;
  font-size: 1.1em;
}

.message {
  display: flex;
  margin-bottom: 10px;
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

.message-content {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  max-width: 70%;
}

.message.user .message-content {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 18px;
  word-wrap: break-word;
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-bubble {
  background: white;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.message-text {
  line-height: 1.5;
  white-space: pre-wrap;
}

.message-time {
  font-size: 0.75em;
  opacity: 0.7;
  margin-top: 5px;
}

.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 5px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #999;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-10px);
  }
}

.chat-input-container {
  padding: 15px 20px;
  background: white;
  border-top: 1px solid #e0e0e0;
}

.chat-input-wrapper {
  display: flex;
  gap: 10px;
  align-items: center;
}

.chat-input {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 24px;
  font-size: 1em;
  outline: none;
  transition: border-color 0.3s;
}

.chat-input:focus {
  border-color: #f5576c;
}

.chat-input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.send-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, opacity 0.2s;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn svg {
  width: 20px;
  height: 20px;
}

/* AskHuman 样式 */
.ask-human-container {
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 12px;
  padding: 15px;
  margin-bottom: 10px;
}

.ask-human-question {
  margin-bottom: 12px;
}

.ask-human-question p {
  margin: 0 0 8px 0;
  font-weight: 600;
  color: #856404;
}

.question-text {
  background: white;
  padding: 12px;
  border-radius: 8px;
  border-left: 3px solid #ffc107;
  color: #333;
  line-height: 1.6;
  white-space: pre-wrap;
}

.ask-human-input-wrapper {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ask-human-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #ffc107;
  border-radius: 24px;
  font-size: 1em;
  outline: none;
  transition: border-color 0.3s;
}

.ask-human-input:focus {
  border-color: #f5576c;
}

.ask-human-send-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #ffc107 0%, #ff9800 100%);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, opacity 0.2s;
}

.ask-human-send-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.ask-human-send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ask-human-send-btn svg {
  width: 20px;
  height: 20px;
}
</style>
