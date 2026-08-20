import axios from 'axios'

const API_BASE_URL = 'http://localhost:8123/api'

// 管理接口仅在 local Profile + 环回地址下开放，前端不保存额外管理令牌。
const GATEWAY_ADMIN_BASE_URL = `${API_BASE_URL}/gateway/admin`

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
 * 建立任务智能体的结构化事件流。
 * 服务端会在完成、取消或等待人工输入后主动关闭当前这一段 SSE。
 */
function openManusStream(url, onEvent, onError, onClose) {
  const eventSource = new EventSource(url)
  let hasReceivedData = false
  let isHandled = false
  let expectedCloseReason = ''

  eventSource.onmessage = (event) => {
    if (event.data) {
      hasReceivedData = true
      try {
        const runEvent = JSON.parse(event.data)
        onEvent(runEvent)

        // EventSource 没有正常结束回调，记录终止事件后可在 onerror 中识别服务端正常关闭。
        if (['RUN_COMPLETED', 'RUN_CANCELLED', 'RUN_ERROR', 'HUMAN_INPUT_REQUIRED'].includes(runEvent.type)) {
          expectedCloseReason = runEvent.type
        }
      } catch (parseError) {
        isHandled = true
        eventSource.close()
        onError(new Error(`无法解析任务事件: ${parseError.message}`))
      }
    }
  }

  eventSource.onerror = (error) => {
    if (isHandled) {
      return
    }

    if (hasReceivedData && expectedCloseReason) {
      isHandled = true
      eventSource.close()
      onClose?.(expectedCloseReason)
      return
    }

    isHandled = true
    eventSource.close()
    onError?.(error)
  }

  return eventSource
}

/** 启动一条新的任务智能体运行。 */
export function chatWithManus(runId, message, onEvent, onError, onClose) {
  const url = `${API_BASE_URL}/ai/manus/chat?runId=${encodeURIComponent(runId)}&message=${encodeURIComponent(message)}`
  return openManusStream(url, onEvent, onError, onClose)
}

/** 把人工回答接回同一个运行中的 AskHuman 工具调用。 */
export function resumeManusRun(runId, answer, onEvent, onError, onClose) {
  const url = `${API_BASE_URL}/ai/manus/runs/${encodeURIComponent(runId)}/resume?answer=${encodeURIComponent(answer)}`
  return openManusStream(url, onEvent, onError, onClose)
}

/**
 * 主动终止服务端运行。
 * 前端关闭 EventSource 只是断开显示，这个接口才会真正中断后台工作线程。
 */
export async function cancelManusRun(runId) {
  const response = await axios.post(`${API_BASE_URL}/ai/manus/runs/${encodeURIComponent(runId)}/cancel`)
  return response.data
}

/**
 * 调用AI聊天助手接口（使用fetch API代替EventSource）
 * @param {string} message - 用户消息
 * @param {string} chatId - 聊天室ID
 * @param {Function} onMessage - 消息回调函数
 * @param {Function} onError - 错误回调函数
 * @param {Function} onClose - 正常关闭回调函数
 */
export function chatWithAssistant(message, chatId, onMessage, onError, onClose) {
  const url = `${API_BASE_URL}/ai/chat?prompt=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`

  let controller = new AbortController()
  let signal = controller.signal
  let isClosed = false

  fetch(url, {
    method: 'GET',
    signal: signal
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let hasReceivedData = false

    function read() {
      return reader.read().then(({ done, value }) => {
        if (done) {
          if (hasReceivedData && !isClosed) {
            isClosed = true
            if (onClose) {
              onClose()
            }
          }
          return
        }

        const chunk = decoder.decode(value, { stream: true })
        if (chunk) {
          hasReceivedData = true
          onMessage(chunk)
        }

        return read()
      })
    }

    return read()
  })
  .catch(error => {
    if (!isClosed && error.name !== 'AbortError') {
      isClosed = true
      console.error('Fetch Error:', error)
      if (onError) {
        onError(error)
      }
    }
  })

  // 返回一个对象，包含关闭方法
  return {
    close: () => {
      if (!isClosed) {
        isClosed = true
        controller.abort()
      }
    }
  }
}

/**
 * 获取聊天历史ID列表
 * @param {string} type - 聊天类型
 * @returns {Promise<Array<string>>} - 聊天ID列表
 */
export async function getChatHistoryIds(type) {
  try {
    const response = await axios.get(`${API_BASE_URL}/ai/history/${type}`)
    return response.data
  } catch (error) {
    console.error('Error fetching chat history IDs:', error)
    return []
  }
}

/**
 * 获取聊天历史记录
 * @param {string} type - 聊天类型
 * @param {string} chatId - 聊天ID
 * @returns {Promise<Array>} - 聊天历史记录
 */
export async function getChatHistory(type, chatId) {
  try {
    const response = await axios.get(`${API_BASE_URL}/ai/history/${type}/${chatId}`)
    return response.data
  } catch (error) {
    console.error('Error fetching chat history:', error)
    return []
  }
}

/**
 * 生成唯一的聊天室ID
 */
export function generateChatId() {
  return `chat_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`
}

/**
 * 生成运行中心通用的 ISO 时间范围。
 * @param {number} hours - 向前查询的小时数
 */
export function gatewayTimeRange(hours = 24) {
  const to = new Date()
  const from = new Date(to.getTime() - hours * 60 * 60 * 1000)
  return { from: from.toISOString(), to: to.toISOString() }
}

/** 获取 Gateway 运行概览。 */
export async function getGatewayOverview(range) {
  const response = await axios.get(`${GATEWAY_ADMIN_BASE_URL}/observability/overview`, { params: range })
  return response.data
}

/** 获取按小时或按天聚合的请求趋势。 */
export async function getGatewaySeries(range, bucket = 'HOUR') {
  const response = await axios.get(`${GATEWAY_ADMIN_BASE_URL}/observability/series`, {
    params: { ...range, bucket }
  })
  return response.data
}

/** 获取模型或路由维度的用量统计。 */
export async function getGatewayDimensions(range, groupBy = 'MODEL') {
  const response = await axios.get(`${GATEWAY_ADMIN_BASE_URL}/observability/dimensions`, {
    params: { ...range, groupBy }
  })
  return response.data
}

/** 获取最近审计事件，审计记录不包含 Prompt 和模型回复。 */
export async function getGatewayAuditEvents(range, limit = 30, eventType = '') {
  const params = { ...range, limit }
  if (eventType) {
    params.eventType = eventType
  }
  const response = await axios.get(`${GATEWAY_ADMIN_BASE_URL}/observability/audit-events`, { params })
  return response.data
}

/** 根据 traceId 下钻一次请求的完整治理事件。 */
export async function getGatewayTrace(traceId) {
  const response = await axios.get(`${GATEWAY_ADMIN_BASE_URL}/observability/trace`, {
    params: { traceId }
  })
  return response.data
}

/** 获取 Provider、模型和路由配置。 */
export async function getGatewayProviders() {
  return (await axios.get(`${GATEWAY_ADMIN_BASE_URL}/providers`)).data
}

export async function getGatewayModels() {
  return (await axios.get(`${GATEWAY_ADMIN_BASE_URL}/models`)).data
}

export async function getGatewayRoutes() {
  return (await axios.get(`${GATEWAY_ADMIN_BASE_URL}/routes`)).data
}

/** 创建或更新一个模型注册项。 */
export async function saveGatewayModel(model) {
  return (await axios.post(`${GATEWAY_ADMIN_BASE_URL}/models`, model)).data
}

/** 显式启停模型，避免前端自行拼装部分更新造成字段丢失。 */
export async function setGatewayModelEnabled(modelKey, enabled) {
  const action = enabled ? 'enable' : 'disable'
  return (await axios.post(`${GATEWAY_ADMIN_BASE_URL}/models/${encodeURIComponent(modelKey)}/${action}`)).data
}

/** 创建或更新路由；后端会校验模型引用并原子刷新注册表快照。 */
export async function saveGatewayRoute(route, exists) {
  const encodedKey = encodeURIComponent(route.routeKey)
  const response = exists
    ? await axios.put(`${GATEWAY_ADMIN_BASE_URL}/routes/${encodedKey}`, route)
    : await axios.post(`${GATEWAY_ADMIN_BASE_URL}/routes`, route)
  return response.data
}

/** 只读模拟路由，不会调用真实模型，也不会消耗 Token。 */
export async function simulateGatewayRoute(routeKey, payload) {
  return (await axios.post(
    `${GATEWAY_ADMIN_BASE_URL}/routes/${encodeURIComponent(routeKey)}/simulate`,
    payload
  )).data
}

/**
 * 调用PDF聊天接口
 * @param {string} message - 用户消息
 * @param {string} chatId - 聊天室ID
 * @param {Function} onMessage - 消息回调函数
 * @param {Function} onError - 错误回调函数
 * @param {Function} onClose - 正常关闭回调函数
 */
export function chatWithPdf(message, chatId, onMessage, onError, onClose) {
  const url = `${API_BASE_URL}/ai/pdf/chat?prompt=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`

  let controller = new AbortController()
  let signal = controller.signal
  let isClosed = false

  fetch(url, {
    method: 'GET',
    signal: signal
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let hasReceivedData = false

    function read() {
      return reader.read().then(({ done, value }) => {
        if (done) {
          if (hasReceivedData && !isClosed) {
            isClosed = true
            if (onClose) {
              onClose()
            }
          }
          return
        }

        const chunk = decoder.decode(value, { stream: true })
        if (chunk) {
          hasReceivedData = true
          onMessage(chunk)
        }

        return read()
      })
    }

    return read()
  })
  .catch(error => {
    if (!isClosed && error.name !== 'AbortError') {
      isClosed = true
      console.error('Fetch Error:', error)
      if (onError) {
        onError(error)
      }
    }
  })

  // 返回一个对象，包含关闭方法
  return {
    close: () => {
      if (!isClosed) {
        isClosed = true
        controller.abort()
      }
    }
  }
}

/**
 * 上传PDF文件
 * @param {string} chatId - 聊天室ID
 * @param {File} file - PDF文件
 * @returns {Promise} - 上传结果
 */
export async function uploadPdf(chatId, file) {
  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await axios.post(`${API_BASE_URL}/ai/pdf/upload/${chatId}`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    console.log('Upload response:', response)
    return response.data
  } catch (error) {
    console.error('Error uploading PDF:', error)
    throw error
  }
}

/**
 * 下载PDF文件
 * @param {string} chatId - 聊天室ID
 */
export function downloadPdf(chatId) {
  const url = `${API_BASE_URL}/ai/pdf/file/${chatId}`
  window.open(url, '_blank')
}
