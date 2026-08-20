<template>
  <div class="chat-assistant-container">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <router-link to="/" class="sidebar-brand">
          <span class="sidebar-brand-mark">H</span>
          <span><strong>Hyper AI</strong><small>Agent Platform</small></span>
        </router-link>
        <div class="sidebar-section-title"><span>CHAT SERVICE</span><h3>多会话助手</h3></div>
        <button class="new-chat-btn" @click="createNewChat">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          新会话
        </button>
      </div>

      <div class="chat-history-list">
        <div
          v-for="chatId in chatHistoryIds"
          :key="chatId"
          :class="['chat-history-item', { active: currentChatId === chatId }]"
          @click="loadChatHistory(chatId)"
        >
          <div class="chat-history-title">会话 {{ chatId.slice(-6) }}</div>
          <button class="delete-chat-btn" @click.stop="deleteChat(chatId)">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 6H5H21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M19 6V20C19 20.5304 18.7893 21.0391 18.4142 21.4142C18.0391 21.7893 17.5304 22 17 22H7C6.46957 22 5.96086 21.7893 5.58579 21.4142C5.21071 21.0391 5 20.5304 5 20V6M8 6V4C8 3.46957 8.21071 2.96086 8.58579 2.58579C8.96086 2.21071 9.46957 2 10 2H14C14.5304 2 15.0391 2.21071 15.4142 2.58579C15.7893 2.96086 16 3.46957 16 4V6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 主聊天区域 -->
    <div class="chat-main">
      <div class="chat-header">
        <div class="page-heading"><span>GENERAL ASSISTANT</span><h2>智能对话</h2></div>
        <div class="header-meta">
          <span class="service-status"><i></i> 在线</span>
          <div class="chat-id">SESSION {{ currentChatId.slice(-8) }}</div>
        </div>
      </div>

      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome-message">
          <div class="welcome-icon assistant">CH</div>
          <span class="welcome-kicker">NEW CONVERSATION</span>
          <h3>今天想解决什么问题？</h3>
          <p>支持多轮上下文与历史记录，你可以从一个问题、一段内容或一个想法开始。</p>
          <div class="prompt-suggestions">
            <button type="button" @click="inputMessage = '解释一个我不熟悉的技术概念'">解释概念</button>
            <button type="button" @click="inputMessage = '帮我优化一段文字表达'">优化表达</button>
            <button type="button" @click="inputMessage = '给我一个问题分析框架'">分析问题</button>
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
            placeholder="输入问题，按 Enter 发送…"
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
        <p class="input-caption">AI 可能会产生不准确的信息，请核验重要内容。</p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { chatWithAssistant, getChatHistoryIds, getChatHistory, generateChatId } from '../utils/api'

export default {
  name: 'ChatAssistant',
  setup() {
    const chatHistoryIds = ref([])
    const currentChatId = ref('')
    const messages = ref([])
    const inputMessage = ref('')
    const isLoading = ref(false)
    const messagesContainer = ref(null)
    let eventSource = null
    let currentAiMessageIndex = null

    onMounted(async () => {
      // 初始化时加载聊天历史ID列表
      await loadChatHistoryIds()
      // 创建新会话
      createNewChat()
    })

    onUnmounted(() => {
      if (eventSource) {
        eventSource.close()
      }
    })

    const loadChatHistoryIds = async () => {
      chatHistoryIds.value = await getChatHistoryIds('chat')
    }

    const createNewChat = () => {
      const newChatId = generateChatId()
      currentChatId.value = newChatId
      messages.value = []
      // 添加到历史列表
      chatHistoryIds.value.unshift(newChatId)
    }

    const loadChatHistory = async (chatId) => {
      currentChatId.value = chatId
      const history = await getChatHistory('chat', chatId)

      // 转换历史记录格式
      messages.value = history.map(msg => ({
        type: msg.role === 'user' ? 'user' : 'ai',
        content: msg.content,
        time: new Date(msg.timestamp)
      }))

      nextTick(() => {
        scrollToBottom()
      })
    }

    const deleteChat = (chatId) => {
      // 从列表中删除
      chatHistoryIds.value = chatHistoryIds.value.filter(id => id !== chatId)
      // 如果删除的是当前会话，创建新会话
      if (currentChatId.value === chatId) {
        createNewChat()
      }
    }

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
      eventSource = chatWithAssistant(
        userMessage,
        currentChatId.value,
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
          // 重新加载历史列表，确保新会话已保存
          loadChatHistoryIds()
        }
      )
    }

    return {
      chatHistoryIds,
      currentChatId,
      messages,
      inputMessage,
      isLoading,
      messagesContainer,
      sendMessage,
      createNewChat,
      loadChatHistory,
      deleteChat,
      formatMessage,
      formatTime
    }
  }
}
</script>
