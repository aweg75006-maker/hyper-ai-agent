<template>
  <div class="chat-pdf-container">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>ChatPDF</h3>
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
    
    <!-- 主区域 -->
    <div class="main-content">
      <div class="content-header">
        <h2>ChatPDF</h2>
        <div class="chat-id">会话ID: {{ currentChatId }}</div>
      </div>
      
      <div class="content-body">
        <!-- PDF预览区域 -->
        <div class="pdf-section">
          <div class="pdf-header">
            <h3>PDF文档</h3>
            <div class="pdf-info" v-if="pdfFile">
              <span class="pdf-filename">{{ pdfFile.name }}</span>
              <span class="pdf-page-info" v-if="pdfPage > 0">第 {{ pdfPage }} 页 / 共 {{ totalPages }} 页</span>
            </div>
          </div>
          
          <div 
            class="pdf-upload-area" 
            v-if="!pdfFile"
            @drop="handleFileDrop"
            @dragover.prevent
            @dragenter.prevent
          >
            <div class="upload-icon">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <p>拖拽PDF文件到此处，或</p>
            <input 
              type="file" 
              accept=".pdf" 
              @change="handleFileSelect"
              class="file-input"
              id="file-upload"
            />
            <label for="file-upload" class="upload-btn">
              选择文件
            </label>
            <p class="upload-hint">支持 PDF 格式，最大 50MB</p>
          </div>
          
          <div class="pdf-preview" v-else>
            <div v-if="totalPages === 0" class="pdf-loading">
              <div class="loading-spinner"></div>
              <p>正在加载PDF文档...</p>
            </div>
            <div v-else class="pdf-canvas-container" ref="pdfContainer">
              <canvas v-for="page in totalPages" :key="page" :ref="el => pdfCanvases[page-1] = el" class="pdf-canvas"></canvas>
            </div>
            <div class="pdf-controls" v-if="totalPages > 0">
              <button @click="goToPrevPage" :disabled="pdfPage <= 1" class="page-btn">上一页</button>
              <span class="page-info">第 {{ pdfPage }} 页 / 共 {{ totalPages }} 页</span>
              <button @click="goToNextPage" :disabled="pdfPage >= totalPages" class="page-btn">下一页</button>
            </div>
          </div>
        </div>

        <!-- 聊天区域 -->
        <div class="chat-section">
          <div class="chat-messages" ref="messagesContainer">
            <div v-if="messages.length === 0" class="welcome-message">
              <p>👋 您好！上传PDF文档后，我可以帮您分析和回答相关问题。</p>
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
                placeholder="输入您的问题..."
                class="chat-input"
                :disabled="isLoading || !pdfFile"
              />
              <button
                @click="sendMessage"
                class="send-btn"
                :disabled="!inputMessage.trim() || isLoading || !pdfFile"
              >
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { chatWithPdf, uploadPdf, getChatHistoryIds, getChatHistory, generateChatId } from '../utils/api'
import * as pdfjsLib from 'pdfjs-dist'

// 设置PDF.js工作器，使用本地的worker文件
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('../../node_modules/pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).href

export default {
  name: 'ChatPDF',
  setup() {
    const chatHistoryIds = ref([])
    const currentChatId = ref('')
    const messages = ref([])
    const inputMessage = ref('')
    const isLoading = ref(false)
    const messagesContainer = ref(null)
    const pdfFile = ref(null)
    const pdfUrl = ref('')
    const pdfPage = ref(0)
    const totalPages = ref(0)
    const pdfContainer = ref(null)
    const pdfCanvases = ref([])
    let eventSource = null
    let currentAiMessageIndex = null
    let pdfDocument = null

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
      chatHistoryIds.value = await getChatHistoryIds('pdf')
    }

    const createNewChat = () => {
      const newChatId = generateChatId()
      currentChatId.value = newChatId
      messages.value = []
      pdfFile.value = null
      pdfUrl.value = ''
      pdfPage.value = 0
      totalPages.value = 0
      pdfDocument = null
      // 添加到历史列表
      chatHistoryIds.value.unshift(newChatId)
    }

    const loadChatHistory = async (chatId) => {
      currentChatId.value = chatId
      const history = await getChatHistory('pdf', chatId)
      
      // 转换历史记录格式
      messages.value = history.map(msg => ({
        type: msg.role === 'user' ? 'user' : 'ai',
        content: msg.content,
        time: new Date(msg.timestamp)
      }))
      
      // 加载对应会话的PDF
      pdfUrl.value = `http://localhost:8123/api/ai/pdf/file/${currentChatId.value}`
      console.log('Loading PDF for chatId:', chatId, 'PDF URL:', pdfUrl.value)
      
      // 延迟尝试加载PDF，确保有足够时间
      setTimeout(async () => {
        try {
          await loadPdf()
        } catch (error) {
          console.error('Error loading PDF for chat history:', error)
          // 即使PDF加载失败，也继续显示聊天历史
          // 重置状态，避免一直显示加载
          totalPages.value = 0
          pdfPage.value = 0
          pdfDocument = null
        }
      }, 1000)
      
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

    const handleFileSelect = (event) => {
      const file = event.target.files[0]
      if (file) {
        processFile(file)
      }
    }

    const handleFileDrop = (event) => {
      const file = event.dataTransfer.files[0]
      if (file) {
        processFile(file)
      }
    }

    const processFile = async (file) => {
      if (!file.type.includes('pdf')) {
        alert('请上传PDF文件！')
        return
      }
      
      if (file.size > 50 * 1024 * 1024) {
        alert('文件大小不能超过50MB！')
        return
      }
      
      try {
        // 上传文件
        console.log('Starting to upload file:', file.name, 'size:', file.size)
        const result = await uploadPdf(currentChatId.value, file)
        console.log('Upload result:', result)
        
        // 处理后端返回的各种格式
        if (result === 'ok' || result.code === 200 || result === true || !result.code || result.msg === 'ok') {
          pdfFile.value = file
          // 设置PDF预览URL
          pdfUrl.value = `http://localhost:8123/api/ai/pdf/file/${currentChatId.value}`
          console.log('PDF URL:', pdfUrl.value)
          
          // 测试PDF URL是否可访问
          setTimeout(async () => {
            try {
              // 使用GET请求代替HEAD请求，确保测试更加可靠
              const testResponse = await fetch(pdfUrl.value, { 
                method: 'GET',
                headers: {
                  'Range': 'bytes=0-1023' // 只请求文件的前1024字节，减少网络流量
                }
              })
              console.log('PDF URL test status:', testResponse.status)
              if (testResponse.ok || testResponse.status === 206) { // 206 Partial Content也是成功
                console.log('PDF URL is accessible, starting to load PDF')
                await loadPdf()
              } else {
                console.error('PDF URL is not accessible:', testResponse.status)
                alert('PDF文件尚未准备就绪，请稍后重试！')
                // 重置状态
                totalPages.value = 0
                pdfPage.value = 0
                pdfDocument = null
              }
            } catch (error) {
              console.error('Error testing PDF URL:', error)
              alert('无法连接到PDF服务器，请检查网络连接！')
              // 重置状态
              totalPages.value = 0
              pdfPage.value = 0
              pdfDocument = null
            }
          }, 3000)
        } else {
          alert('上传失败：' + (result.msg || '未知错误'))
        }
      } catch (error) {
        console.error('上传错误:', error)
        alert('上传失败，请重试！')
      }
    }

    const loadPdf = async () => {
      try {
        console.log('Starting to load PDF from:', pdfUrl.value)
        
        // 重置PDF状态
        totalPages.value = 0
        pdfPage.value = 0
        pdfDocument = null
        
        // 测试URL是否可访问
        try {
          // 使用GET请求代替HEAD请求，确保测试更加可靠
          const response = await fetch(pdfUrl.value, { 
            method: 'GET',
            headers: {
              'Range': 'bytes=0-1023' // 只请求文件的前1024字节，减少网络流量
            },
            signal: AbortSignal.timeout(10000) // 添加10秒超时
          })
          console.log('PDF URL status:', response.status)
          
          if (!response.ok && response.status !== 206) { // 206 Partial Content也是成功
            throw new Error(`PDF URL returned status: ${response.status}`)
          }
        } catch (fetchError) {
          console.error('Fetch error:', fetchError)
          throw new Error('无法连接到PDF服务器，请检查网络连接')
        }
        
        // 从URL加载PDF
        const loadingTask = pdfjsLib.getDocument({
          url: pdfUrl.value,
          cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@4.4.168/cmaps/',
          cMapPacked: true,
          disableFontFace: true,
          renderInteractiveForms: false
        })
        
        // 添加超时处理
        const timeoutPromise = new Promise((_, reject) => {
          setTimeout(() => reject(new Error('PDF加载超时')), 30000)
        })
        
        pdfDocument = await Promise.race([loadingTask.promise, timeoutPromise])
        totalPages.value = pdfDocument.numPages
        pdfPage.value = 1
        
        console.log('PDF loaded successfully. Total pages:', totalPages.value)
        
        // 渲染第一页
        await renderPage(pdfPage.value)
        
        // 标记PDF文件已加载
        if (pdfFile.value && typeof pdfFile.value.name === 'string' && !pdfFile.value.name.includes('(')) {
          pdfFile.value = { name: `${pdfFile.value.name} (${totalPages.value} pages)` }
        }
      } catch (error) {
        console.error('Error loading PDF:', error)
        // 重置状态，避免一直显示加载
        totalPages.value = 0
        pdfPage.value = 0
        pdfDocument = null
        // 显示错误信息
        alert('加载PDF失败: ' + error.message)
      }
    }

    const renderPage = async (pageNum) => {
      try {
        console.log('Rendering page:', pageNum)
        
        const page = await pdfDocument.getPage(pageNum)
        const viewport = page.getViewport({ scale: 1.0 })
        
        // 等待DOM更新，确保canvas元素已创建
        await nextTick()
        
        // 设置canvas尺寸
        const canvas = pdfCanvases.value[pageNum - 1]
        if (!canvas) {
          console.error('Canvas element not found for page:', pageNum)
          return
        }
        
        const context = canvas.getContext('2d')
        canvas.height = viewport.height
        canvas.width = viewport.width
        
        // 渲染页面
        const renderContext = {
          canvasContext: context,
          viewport: viewport
        }
        
        console.log('Rendering page', pageNum, 'to canvas with dimensions:', canvas.width, 'x', canvas.height)
        await page.render(renderContext).promise
        
        console.log('Page', pageNum, 'rendered successfully')
        
        // 滚动到当前页面
        if (pdfContainer.value) {
          pdfContainer.value.scrollTop = (pageNum - 1) * viewport.height
        }
      } catch (error) {
        console.error('Error rendering page:', error)
      }
    }

    const goToPrevPage = async () => {
      if (pdfPage.value > 1) {
        pdfPage.value--
        await renderPage(pdfPage.value)
      }
    }

    const goToNextPage = async () => {
      if (pdfPage.value < totalPages.value) {
        pdfPage.value++
        await renderPage(pdfPage.value)
      }
    }

    const sendMessage = () => {
      if (!inputMessage.value.trim() || isLoading.value || !pdfFile.value) {
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

      // 调用PDF聊天接口
      eventSource = chatWithPdf(
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
      pdfFile,
      pdfUrl,
      pdfPage,
      totalPages,
      pdfContainer,
      pdfCanvases,
      sendMessage,
      createNewChat,
      loadChatHistory,
      deleteChat,
      formatMessage,
      formatTime,
      handleFileSelect,
      handleFileDrop,
      loadPdf,
      renderPage,
      goToPrevPage,
      goToNextPage
    }
  }
}
</script>

<style scoped>
.chat-pdf-container {
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

/* 主内容区域 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.content-header {
  background: white;
  padding: 20px 30px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.content-header h2 {
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

.content-body {
  flex: 1;
  display: flex;
  padding: 20px;
  gap: 20px;
  overflow: hidden;
}

/* PDF区域 */
.pdf-section {
  flex: 1;
  min-width: 400px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pdf-header {
  padding: 15px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pdf-header h3 {
  margin: 0;
  font-size: 1.1em;
  font-weight: 600;
  color: #333;
}

.pdf-info {
  display: flex;
  align-items: center;
  gap: 15px;
  font-size: 0.9em;
  color: #666;
}

.pdf-filename {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pdf-page-info {
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 10px;
}

.pdf-upload-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed #e0e0e0;
  border-radius: 8px;
  margin: 20px;
  padding: 40px;
  text-align: center;
  color: #666;
  cursor: pointer;
  transition: all 0.3s;
}

.pdf-upload-area:hover {
  border-color: #667eea;
  background: #f8f9ff;
}

.upload-icon {
  margin-bottom: 20px;
}

.upload-icon svg {
  width: 60px;
  height: 60px;
  color: #667eea;
}

.pdf-upload-area p {
  margin: 10px 0;
  font-size: 1em;
}

.file-input {
  display: none;
}

.upload-btn {
  display: inline-block;
  padding: 10px 20px;
  border: 1px solid #667eea;
  border-radius: 8px;
  background: white;
  color: #667eea;
  font-size: 0.9em;
  cursor: pointer;
  transition: all 0.3s;
  margin: 10px 0;
}

.upload-btn:hover {
  background: #667eea;
  color: white;
}

.upload-hint {
  font-size: 0.8em;
  color: #999;
  margin-top: 10px;
}

.pdf-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.pdf-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #f9f9f9;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.pdf-loading p {
  color: #666;
  font-size: 16px;
  margin: 0;
}

.pdf-canvas-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f5f5;
}

.pdf-canvas {
  display: block;
  margin: 0 auto 20px;
  border: 1px solid #e0e0e0;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
  background: white;
}

.pdf-controls {
  padding: 15px 20px;
  border-top: 1px solid #e0e0e0;
  background: white;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid #667eea;
  border-radius: 6px;
  background: white;
  color: #667eea;
  font-size: 0.9em;
  cursor: pointer;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  background: #667eea;
  color: white;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  border-color: #e0e0e0;
  color: #999;
}

.page-info {
  font-size: 0.9em;
  color: #666;
  font-weight: 500;
}

/* 聊天区域 */
.chat-section {
  flex: 1;
  min-width: 400px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  font-size: 1em;
  background: #f9f9f9;
  border-radius: 8px;
  margin: 20px auto;
  max-width: 400px;
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
  max-width: 80%;
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
  background: #f0f0f0;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  word-wrap: break-word;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-bubble {
  background: #f9f9f9;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-text {
  line-height: 1.5;
  white-space: pre-wrap;
  font-size: 0.95em;
}

.message-time {
  font-size: 0.7em;
  opacity: 0.7;
  margin-top: 6px;
  text-align: right;
}

.typing-indicator {
  display: flex;
  gap: 6px;
  padding: 8px 0;
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
    transform: translateY(-8px);
  }
}

.chat-input-container {
  padding: 15px 20px;
  border-top: 1px solid #e0e0e0;
  background: white;
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
  font-size: 0.95em;
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
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, opacity 0.2s, box-shadow 0.3s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 3px 12px rgba(102, 126, 234, 0.4);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.send-btn svg {
  width: 20px;
  height: 20px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .content-body {
    flex-direction: column;
  }
  
  .pdf-section,
  .chat-section {
    min-width: auto;
  }
  
  .pdf-section {
    max-height: 50vh;
  }
}

@media (max-width: 768px) {
  .sidebar {
    width: 240px;
  }
  
  .content-header {
    padding: 15px 20px;
  }
  
  .content-body {
    padding: 10px;
    gap: 10px;
  }
  
  .pdf-header {
    padding: 10px 15px;
  }
  
  .chat-messages {
    padding: 15px;
  }
  
  .chat-input-container {
    padding: 10px 15px;
  }
}
</style>