<template>
  <div class="chat-assistant-container">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>AI聊天助手</h3>
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
        <h2>AI聊天助手</h2>
        <div class="chat-id">会话ID: {{ currentChatId }}</div>
      </div>
      
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome-message">
          <p>👋 您好！我是AI聊天助手，很高兴为您服务。有什么我可以帮助您的吗？</p>
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
        <div class="chat-input-wrapper">
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

<style scoped>
.chat-assistant-container {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
}

/* 侧边栏样式 */
.sidebar {
  width: 280px;
  background: white;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
}

.sidebar-header h3 {
  margin: 0 0 15px 0;
  font-size: 1.2em;
  font-weight: 600;
  color: #333;
}

.new-chat-btn {
  width: 100%;
  padding: 10px 15px;
  border: 1px solid #667eea;
  border-radius: 8px;
  background: white;
  color: #667eea;
  font-size: 0.9em;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s;
}

.new-chat-btn:hover {
  background: #667eea;
  color: white;
}

.new-chat-btn svg {
  width: 16px;
  height: 16px;
}

.chat-history-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.chat-history-item {
  padding: 12px 15px;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all 0.3s;
}

.chat-history-item:hover {
  background: #f0f0f0;
}

.chat-history-item.active {
  background: #e8eaf6;
  border-left: 3px solid #667eea;
}

.chat-history-title {
  font-size: 0.9em;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-chat-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.3s;
}

.delete-chat-btn:hover {
  background: #ffebee;
  color: #f44336;
}

.delete-chat-btn svg {
  width: 16px;
  height: 16px;
}

/* 主聊天区域样式 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.chat-header {
  background: white;
  padding: 20px 30px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-header h2 {
  margin: 0;
  font-size: 1.3em;
  font-weight: 600;
  color: #333;
}

.chat-id {
  font-size: 0.85em;
  color: #666;
  background: #f0f0f0;
  padding: 4px 12px;
  border-radius: 12px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.welcome-message {
  text-align: center;
  padding: 60px 30px;
  color: #666;
  font-size: 1.1em;
  background: white;
  border-radius: 12px;
  margin: 20px auto;
  max-width: 600px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
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
  gap: 15px;
  max-width: 70%;
}

.message.user .message-content {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
  background: #f0f0f0;
}

.message-bubble {
  padding: 16px 20px;
  border-radius: 18px;
  word-wrap: break-word;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-bubble {
  background: white;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-text {
  line-height: 1.6;
  white-space: pre-wrap;
  font-size: 1em;
}

.message-time {
  font-size: 0.75em;
  opacity: 0.7;
  margin-top: 8px;
  text-align: right;
}

.typing-indicator {
  display: flex;
  gap: 8px;
  padding: 10px 0;
}

.typing-indicator span {
  width: 10px;
  height: 10px;
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
  padding: 20px 30px;
  background: white;
  border-top: 1px solid #e0e0e0;
}

.chat-input-wrapper {
  display: flex;
  gap: 15px;
  align-items: center;
  max-width: 800px;
  margin: 0 auto;
}

.chat-input {
  flex: 1;
  padding: 16px 20px;
  border: 1px solid #e0e0e0;
  border-radius: 28px;
  font-size: 1em;
  outline: none;
  transition: all 0.3s;
  background: #f9f9f9;
}

.chat-input:focus {
  border-color: #667eea;
  background: white;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.chat-input:disabled {
  background: #f0f0f0;
  cursor: not-allowed;
}

.send-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, opacity 0.2s, box-shadow 0.3s;
  box-shadow: 0 2px 10px rgba(102, 126, 234, 0.3);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.send-btn svg {
  width: 24px;
  height: 24px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    width: 240px;
  }
  
  .chat-messages {
    padding: 20px;
  }
  
  .message-content {
    max-width: 85%;
  }
  
  .chat-input-wrapper {
    padding: 0 10px;
  }
}
</style>