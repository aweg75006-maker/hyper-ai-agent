<template>
  <div class="chat-pdf-container">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <router-link to="/" class="sidebar-brand">
          <span class="sidebar-brand-mark">H</span>
          <span><strong>Hyper AI</strong><small>Agent Platform</small></span>
        </router-link>
        <div class="sidebar-section-title"><span>KNOWLEDGE BASE</span><h3>文档问答</h3></div>
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
        <div class="page-heading"><span>DOCUMENT INTELLIGENCE</span><h2>文档知识问答</h2></div>
        <div class="header-meta">
          <span class="service-status"><i></i> RAG Ready</span>
          <div class="chat-id">SESSION {{ currentChatId.slice(-8) }}</div>
        </div>
      </div>

      <div class="content-body">
        <!-- PDF预览区域 -->
        <div class="pdf-section">
          <div class="pdf-header">
            <div><span class="panel-kicker">SOURCE DOCUMENT</span><h3>PDF 文档</h3></div>
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
            <h4>添加知识文档</h4>
            <p>拖拽 PDF 文件到这里，或从本地选择</p>
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
              <div class="welcome-icon pdf">KB</div>
              <span class="welcome-kicker">GROUNDED ANSWERS</span>
              <h3>基于文档内容进行问答</h3>
              <p>上传 PDF 后即可提问。回答将结合检索到的文档上下文生成。</p>
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
                placeholder="上传文档后输入问题…"
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
            <p class="input-caption">回答基于当前文档检索结果生成。</p>
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
