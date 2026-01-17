import axios from 'axios'

const API_BASE_URL = 'http://localhost:8123/api'

/**
 * 调用心理咨询SSE接口
 * @param {string} message - 用户消息
 * @param {string} chatId - 聊天室ID
 * @param {Function} onMessage - 消息回调函数
 * @param {Function} onError - 错误回调函数
 * @param {Function} onClose - 正常关闭回调函数
 */
export function chatWithPsyAppSse(message, chatId, onMessage, onError, onClose) {
  const url = `${API_BASE_URL}/ai/psy_app/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`
  
  const eventSource = new EventSource(url)
  let hasReceivedData = false
  let isHandled = false
  
  eventSource.onmessage = (event) => {
    if (event.data) {
      hasReceivedData = true
      onMessage(event.data)
    }
  }
  
  eventSource.onerror = (error) => {
    // 确保只处理一次
    if (isHandled) {
      return
    }
    
    // 如果已经接收到数据，认为连接是正常关闭的
    // EventSource 在正常关闭时也会触发 onerror 事件
    if (hasReceivedData) {
      isHandled = true
      // 延迟关闭，确保所有数据都已接收
      setTimeout(() => {
        eventSource.close()
        if (onClose) {
          onClose()
        }
      }, 100)
      return
    }
    
    // 如果没有接收到任何数据就出错，才是真正的错误
    isHandled = true
    console.error('SSE Error:', error)
    eventSource.close()
    if (onError) {
      onError(error)
    }
  }
  
  return eventSource
}

/**
 * 调用智能体SSE接口
 * @param {string} message - 用户消息
 * @param {Function} onMessage - 消息回调函数
 * @param {Function} onError - 错误回调函数
 * @param {Function} onClose - 正常关闭回调函数
 */
export function chatWithManus(message, onMessage, onError, onClose) {
  const url = `${API_BASE_URL}/ai/manus/chat?message=${encodeURIComponent(message)}`
  
  const eventSource = new EventSource(url)
  let hasReceivedData = false
  let isHandled = false
  
  eventSource.onmessage = (event) => {
    if (event.data) {
      hasReceivedData = true
      onMessage(event.data)
    }
  }
  
  eventSource.onerror = (error) => {
    // 确保只处理一次
    if (isHandled) {
      return
    }
    
    // 如果已经接收到数据，认为连接是正常关闭的
    // EventSource 在正常关闭时也会触发 onerror 事件
    if (hasReceivedData) {
      isHandled = true
      // 延迟关闭，确保所有数据都已接收
      setTimeout(() => {
        eventSource.close()
        if (onClose) {
          onClose()
        }
      }, 100)
      return
    }
    
    // 如果没有接收到任何数据就出错，才是真正的错误
    isHandled = true
    console.error('SSE Error:', error)
    eventSource.close()
    if (onError) {
      onError(error)
    }
  }
  
  return eventSource
}

/**
 * 生成唯一的聊天室ID
 */
export function generateChatId() {
  return `chat_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`
}
