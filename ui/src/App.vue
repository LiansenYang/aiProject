<template>
  <div class="chat-container">
    <div class="messages" ref="messageContainer">
      <div
          v-for="message in messages"
          :key="message.id"
          :class="['message', message.isBot ? 'bot-message' : 'user-message']"
      >
        <div class="message-content">{{ message.content }}</div>
        <div class="message-time">{{ formatTime(message.timestamp) }}</div>
        <button
            v-if="message.isBot && message.status === MessageStatus.Error"
            @click="message.retry"
            class="retry-button"
        >
          重试
        </button>
      </div>
    </div>
    <div class="input-container">
      <input
          type="text"
          ref="inputRef"
          v-model="inputMessage"
          :disabled="isLoading"
          @keyup.enter="sendMessage"
          class="message-input"
          placeholder="请输入消息..."
      />
      <button @click="sendMessage" :disabled="isLoading" class="send-button">
        发送
      </button>
      <button v-if="isLoading" @click="stopGeneration" class="stop-button">
        停止
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, reactive, onMounted, onBeforeUnmount, nextTick} from 'vue'
import {fetchEventSource} from '@microsoft/fetch-event-source'

// 生成随机用户ID（示例：8位字母数字组合）
const generateUserId = () => {
  return Math.random().toString(36).substr(2, 8);
};

// 持久化存储用户ID
const userId = ref('');

enum MessageStatus {
  Streaming = 'streaming',
  Complete = 'complete',
  Error = 'error',
}

interface Message {
  id: string
  content: string
  isBot: boolean
  status?: MessageStatus
  timestamp: number
  retry?: () => Promise<void>
}

const messages = ref<Message[]>([])
const inputMessage = ref('')
const isLoading = ref(false)
const controller = ref<AbortController>()
const messageContainer = ref<HTMLElement>()
const inputRef = ref<HTMLInputElement>()

// 自动滚动控制
let autoScroll = true
let lastCharType: 'chinese' | 'english' | 'other' = 'other'

const scrollToBottom = () => {
  nextTick(() => {
    if (messageContainer.value && autoScroll) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

const handleScroll = () => {
  if (!messageContainer.value) return
  const {scrollTop, scrollHeight, clientHeight} = messageContainer.value
  autoScroll = scrollHeight - (scrollTop + clientHeight) < 50
}

// 字符类型检测
const getCharType = (char: string): 'chinese' | 'english' | 'other' => {
  if (/[\u4e00-\u9fa5\u3000-\u303F\uFF00-\uFFEF]/.test(char)) {
    return 'chinese'
  }
  if (/[a-zA-Z]/.test(char)) {
    return 'english'
  }
  return 'other'
}

// 智能空格处理核心逻辑
const processContent = (prev: string, newData: string): string => {
  if (prev.length === 0) return newData

  const lastChar = prev.slice(-1)
  const newFirstChar = newData[0] || ''

  const prevType = getCharType(lastChar)
  const newType = getCharType(newFirstChar)

  let processed = newData

  // 需要添加空格的情况
  const shouldAddSpace =
      (prevType === 'english' && newType === 'english') || // 英文接英文
      (prevType === 'chinese' && newType === 'english') || // 中文接英文
      (prevType === 'english' && newType === 'chinese' && !/[!?,.]$/.test(lastChar)) // 英文接中文（非标点结尾）

  // 需要删除空格的情况
  const shouldRemoveSpace =
      (prevType === 'chinese' && newType === 'chinese') || // 中文接中文
      (prevType === 'other' && /^[\u4e00-\u9fa5]/.test(newData)) // 特殊符号接中文

  if (shouldAddSpace && !lastChar.match(/\s/) && !newFirstChar.match(/\s/)) {
    processed = ' ' + processed
  } else if (shouldRemoveSpace) {
    processed = processed.replace(/^\s+/, '')
  }

  return processed
}

const sendChatRequest = async (content: string, botMessage: Message) => {
  controller.value = new AbortController()

  await fetchEventSource('http://localhost:8080/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'X-Content-Lang': 'zh-CN'
    },
    body: JSON.stringify({message: content,userId:userId.value}),
    signal: controller.value?.signal,
    openWhenHidden: true,

    onopen: async response => {
      if (!response.ok) throw new Error(`HTTP error ${response.status}`)
    },

    onmessage: event => {
      if (event.data === '[DONE]') {
        botMessage.status = MessageStatus.Complete
        return
      }

      const processedData = processContent(botMessage.content, event.data)
      botMessage.content += processedData
      botMessage.timestamp = Date.now()

      // 更新最后字符类型
      const lastChar = processedData.slice(-1)
      lastCharType = getCharType(lastChar)

      scrollToBottom()
    },

    onerror: err => {
      throw err
    }
  })
}

// 错误处理
const handleRequestError = (botMessage: Message, error: unknown) => {
  const errorMessage = error instanceof Error
      ? navigator.onLine
          ? error.message
          : '网络连接不可用'
      : '请求发生未知错误'

  botMessage.status = MessageStatus.Error
  botMessage.content = errorMessage
  botMessage.retry = createRetryHandler(botMessage.content)
}

// 主发送逻辑
const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return

  const userContent = inputMessage.value.trim()
  inputMessage.value = ''

  // 创建用户消息
  const userMessage = reactive<Message>({
    id: `user-${Date.now()}`,
    content: userContent,
    isBot: false,
    timestamp: Date.now()
  })
  messages.value.push(userMessage)

  // 创建机器人消息
  const botMessage = reactive<Message>({
    id: `bot-${Date.now()}`,
    content: '',
    isBot: true,
    status: MessageStatus.Streaming,
    timestamp: Date.now()
  })
  messages.value.push(botMessage)

  isLoading.value = true

  try {
    await sendChatRequest(userContent, botMessage)
  } catch (err) {
    handleRequestError(botMessage, err)
  } finally {
    isLoading.value = false
    nextTick(() => inputRef.value?.focus())
  }
}

// 停止生成
const stopGeneration = () => {
  controller.value?.abort()
  isLoading.value = false
}

// 生命周期
onMounted(() => {
  userId.value = localStorage.getItem('chatUserId') || generateUserId();
  localStorage.setItem('chatUserId', userId.value);
  messageContainer.value?.addEventListener('scroll', handleScroll)
  inputRef.value?.focus()
})

onBeforeUnmount(() => {
  messageContainer.value?.removeEventListener('scroll', handleScroll)
  controller.value?.abort()
})


const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString()
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  background-color: #f9f9f9;
}

.message {
  margin-bottom: 10px;
  display: flex;
  justify-content: space-between;
}

.user-message .message-content {
  background-color: #e6f7ff;
  border-radius: 10px;
  padding: 10px;
  max-width: 60%;
  align-self: flex-end;
}

.bot-message .message-content {
  background-color: #fffbe6;
  border-radius: 10px;
  padding: 10px;
  max-width: 60%;
}

.message-time {
  font-size: 12px;
  color: #888;
  margin-left: 10px;
  align-self: flex-end;
}

.input-container {
  display: flex;
  padding: 10px;
  border-top: 1px solid #ddd;
}

.message-input {
  flex: 1;
  padding: 10px;
  border: 1px solid #ccc;
  border-radius: 5px;
}

.send-button,
.stop-button,
.retry-button {
  margin-left: 10px;
  padding: 10px;
  border: none;
  border-radius: 5px;
  background-color: #4caf50;
  color: white;
  cursor: pointer;
}

.send-button:disabled,
.stop-button:disabled {
  background-color: #888;
}

.retry-button {
  background-color: #ff9800;
  cursor: pointer;
}
</style>
