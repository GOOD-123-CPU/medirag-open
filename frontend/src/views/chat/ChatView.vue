<template>
  <div class="chat-layout">
    <!-- ===== 左侧会话列表 ===== -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <h3>对话记录</h3>
        <el-button type="primary" size="small" :icon="Plus" @click="newConversation">
          新对话
        </el-button>
      </div>

      <div class="conv-list" v-loading="convLoading">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: currentConvId === conv.id }"
          @click="switchConversation(conv.id)"
        >
          <el-icon class="conv-icon"><ChatDotRound /></el-icon>
          <div class="conv-info">
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-meta">{{ formatRelativeTime(conv.lastActive) }} · {{ conv.messageCount }}条</div>
          </div>
          <div class="conv-actions">
            <el-button
              text
              size="small"
              class="conv-action"
              title="导出 Markdown"
              @click.stop="exportConversation(conv.id, conv.title)"
            >
              <el-icon><Download /></el-icon>
            </el-button>
            <el-button
              text
              size="small"
              class="conv-action conv-delete"
              title="删除会话"
              @click.stop="deleteConversation(conv.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>

        <div v-if="conversations.length === 0 && !convLoading" class="empty-conv">
          <el-icon size="32" color="#cbd5e1"><ChatLineSquare /></el-icon>
          <p>暂无对话记录</p>
        </div>
      </div>
    </aside>

    <!-- ===== 主聊天区域 ===== -->
    <div class="chat-main">
      <!-- 欢迎页 -->
      <div v-if="!currentConvId && messages.length === 0" class="welcome-screen">
        <div class="welcome-logo">
          <div class="logo-icon-lg">
            <el-icon size="48" color="#fff"><Plus /></el-icon>
          </div>
        </div>
        <h2 class="welcome-title">MediRAG 智能医疗问答</h2>
        <p class="welcome-desc">
          基于权威医学文献，为您提供有据可查的医疗健康知识解答
        </p>
        <div class="quick-questions">
          <div
            v-for="q in quickQuestions"
            :key="q"
            class="quick-q-item"
            @click="sendMessage(q)"
          >
            {{ q }}
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages-container" ref="messagesContainerRef">
        <div
          v-for="msg in messages"
          :key="msg.id || msg.tempId"
          class="message-wrapper"
          :class="msg.role"
        >
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="user-message">
            <div class="message-content user-content">{{ msg.content }}</div>
            <el-avatar :size="34" class="message-avatar user-avatar">
              {{ userStore.userInfo?.nickname?.charAt(0) }}
            </el-avatar>
          </div>

          <!-- AI 消息 -->
          <div v-else class="ai-message">
            <div class="ai-avatar">
              <el-icon size="20" color="#3b82f6"><Plus /></el-icon>
            </div>
            <div class="ai-content-wrapper">
              <!-- 消息内容（Markdown 渲染） -->
              <div
                class="message-content ai-content"
                :class="{ streaming: msg.isStreaming }"
                v-html="renderMarkdown(msg.content)"
              ></div>

              <!-- 流式状态指示 -->
              <div v-if="msg.isStreaming" class="streaming-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>

              <!-- 来源引用 -->
              <div v-if="msg.sources && msg.sources.length" class="sources-panel">
                <div class="sources-header" @click="msg.showSources = !msg.showSources">
                  <el-icon><Document /></el-icon>
                  <span>参考文献 ({{ msg.sources.length }})</span>
                  <el-icon class="toggle-icon" :class="{ rotated: msg.showSources }">
                    <ArrowDown />
                  </el-icon>
                </div>
                <transition name="fade">
                  <div v-if="msg.showSources" class="sources-list">
                    <div v-for="(src, i) in msg.sources" :key="i" class="source-item">
                      <span class="source-index">[{{ src.index }}]</span>
                      <div class="source-info">
                        <div class="source-name">{{ src.name }}</div>
                        <div class="source-excerpt">{{ src.content }}</div>
                        <div class="source-meta" v-if="src.chapter || src.pageNumber">
                          <span v-if="src.chapter">{{ src.chapter }}</span>
                          <span v-if="src.pageNumber"> · 第{{ src.pageNumber }}页</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </transition>
              </div>

              <!-- 操作栏 -->
              <div v-if="!msg.isStreaming && msg.role === 'assistant'" class="message-actions">
                <el-tooltip content="有用">
                  <el-button
                    text size="small"
                    :type="msg.feedback === 1 ? 'primary' : ''"
                    @click="submitFeedback(msg, 1)"
                  >
                    <el-icon><Promotion /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="没用">
                  <el-button
                    text size="small"
                    :type="msg.feedback === -1 ? 'danger' : ''"
                    @click="submitFeedback(msg, -1)"
                  >
                    <el-icon><SemiSelect /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="查看检索过程">
                  <el-button text size="small" @click="showRetrievalLog(msg)">
                    <el-icon><DataAnalysis /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="复制">
                  <el-button text size="small" @click="copyContent(msg.content)">
                    <el-icon><CopyDocument /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            :placeholder="isRecording ? '正在聆听，请说话...' : isStreaming ? '正在生成中，请稍候...' : '输入您的医疗健康问题，按 Enter 发送 (Shift+Enter 换行)'"
            :disabled="isStreaming"
            @keydown.enter.exact.prevent="handleEnterSend"
            class="chat-input"
          />
          <div class="input-actions">
            <el-tooltip content="快捷问题">
              <el-button text circle @click="quickPanelVisible = !quickPanelVisible">
                <el-icon><QuestionFilled /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip :content="isRecording ? '停止录音' : '语音输入'">
              <el-button
                text
                circle
                :class="{ 'voice-recording': isRecording }"
                :style="isRecording ? { color: '#ef4444' } : {}"
                :disabled="isStreaming"
                @click="toggleVoice"
              >
                <el-icon>
                  <svg v-if="!isRecording" viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em">
                    <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm-1-9c0-.55.45-1 1-1s1 .45 1 1v6c0 .55-.45 1-1 1s-1-.45-1-1V5zm6 6c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em">
                    <path d="M19 11h-1.7c0 .74-.16 1.43-.43 2.05l1.23 1.23c.56-.98.9-2.09.9-3.28zm-4.02.17c0-.06.02-.11.02-.17V5c0-1.66-1.34-3-3-3S9 3.34 9 5v.18l5.98 5.99zM4.27 3L3 4.27l6.01 6.01V11c0 1.66 1.34 3 3 3 .23 0 .44-.03.65-.08l1.66 1.66c-.71.33-1.5.52-2.31.52-2.76 0-5.3-2.1-5.3-5.1H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c.91-.13 1.77-.45 2.54-.9L19.73 21 21 19.73 4.27 3z"/>
                  </svg>
                </el-icon>
              </el-button>
            </el-tooltip>
            <el-button
              type="primary"
              :loading="isStreaming"
              :disabled="!inputText.trim()"
              circle
              @click="handleSend"
            >
              <el-icon><Position /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="input-hint">
          MediRAG 仅提供医疗健康知识科普，不替代专业医疗诊断。如有紧急情况请拨打 120。
        </div>
      </div>
    </div>

    <!-- ===== 检索过程可视化弹窗 ===== -->
    <el-dialog
      v-model="retrievalLogVisible"
      title="RAG 检索过程可视化"
      width="700px"
      class="retrieval-dialog"
    >
      <div v-if="currentRetrievalLog" class="rl-wrap">

        <!-- 流水线步骤条 -->
        <div class="rl-pipeline">
          <div class="rl-step">
            <div class="rl-step-icon blue">
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
            </div>
            <div class="rl-step-label">Query 改写</div>
          </div>
          <div class="rl-arrow">→</div>
          <div class="rl-step">
            <div class="rl-step-icon purple">
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
            </div>
            <div class="rl-step-label">多路召回</div>
          </div>
          <div class="rl-arrow">→</div>
          <div class="rl-step">
            <div class="rl-step-icon teal">
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M17 12h-5v5h5v-5zM16 1v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-1V1h-2zm3 18H5V8h14v11z"/></svg>
            </div>
            <div class="rl-step-label">RRF 融合</div>
          </div>
          <div class="rl-arrow">→</div>
          <div class="rl-step">
            <div class="rl-step-icon orange">
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z"/></svg>
            </div>
            <div class="rl-step-label">重排序</div>
          </div>
          <div class="rl-arrow">→</div>
          <div class="rl-step">
            <div class="rl-step-icon" :class="currentRetrievalLog.isFallback ? 'amber' : 'green'">
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>
            </div>
            <div class="rl-step-label">{{ currentRetrievalLog.isFallback ? '兜底响应' : '正常响应' }}</div>
          </div>
        </div>

        <!-- Query 改写对比 -->
        <div class="rl-section">
          <div class="rl-section-title">
            <span class="rl-badge blue">Step 1</span>Query 改写
          </div>
          <div class="rl-query-compare">
            <div class="rl-query-box">
              <div class="rl-query-tag">原始问题</div>
              <div class="rl-query-text">{{ currentRetrievalLog.originalQuery || '-' }}</div>
            </div>
            <div class="rl-query-arrow">
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/></svg>
            </div>
            <div class="rl-query-box rewritten">
              <div class="rl-query-tag blue">改写后查询</div>
              <div class="rl-query-text">{{ currentRetrievalLog.rewrittenQuery || '-' }}</div>
            </div>
          </div>
        </div>

        <!-- 多路召回 + RRF 指标 -->
        <div class="rl-section">
          <div class="rl-section-title">
            <span class="rl-badge purple">Step 2–3</span>多路召回 & RRF 融合
          </div>
          <div class="rl-metrics">
            <div class="rl-metric-card purple">
              <div class="rl-metric-num">{{ currentRetrievalLog.vectorResults ?? 0 }}</div>
              <div class="rl-metric-name">向量检索</div>
              <div class="rl-metric-sub">语义相似度</div>
            </div>
            <div class="rl-metric-plus">+</div>
            <div class="rl-metric-card blue">
              <div class="rl-metric-num">{{ currentRetrievalLog.bm25Results ?? 0 }}</div>
              <div class="rl-metric-name">BM25 检索</div>
              <div class="rl-metric-sub">关键词匹配</div>
            </div>
            <div class="rl-metric-plus">
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/></svg>
            </div>
            <div class="rl-metric-card teal">
              <div class="rl-metric-num">{{ currentRetrievalLog.rrfCount ?? 0 }}</div>
              <div class="rl-metric-name">RRF 融合</div>
              <div class="rl-metric-sub">互惠排名融合</div>
            </div>
          </div>
        </div>

        <!-- 重排序 & 结果 -->
        <div class="rl-section">
          <div class="rl-section-title">
            <span class="rl-badge orange">Step 4</span>Cross-Encoder 重排序
          </div>
          <div class="rl-rerank-row">
            <div class="rl-rerank-info">
              <span class="rl-rerank-num">{{ currentRetrievalLog.rerankTop ?? 5 }}</span>
              <span class="rl-rerank-desc">条最终上下文注入 Prompt</span>
            </div>
            <el-tag
              :type="currentRetrievalLog.isFallback ? 'warning' : 'success'"
              effect="light"
              size="large"
            >
              {{ currentRetrievalLog.isFallback ? '⚠️ 置信度低，触发兜底' : '✅ 检索命中，正常响应' }}
            </el-tag>
          </div>
        </div>

      </div>

      <div v-else class="rl-empty">暂无检索日志</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Download } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { chatApi, type Conversation, type Message, type SourceRef } from '@/api/chat'
import { welcomePrompts } from '@/constants/examplePrompts'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

// ===================== 状态 =====================
const conversations = ref<Conversation[]>([])
const convLoading = ref(false)
const currentConvId = ref<number | null>(null)
const messages = ref<any[]>([])
const messagesContainerRef = ref<HTMLElement>()
const inputText = ref('')
const isStreaming = ref(false)
const quickPanelVisible = ref(false)

// ===================== 语音录入状态 =====================
const isRecording = ref(false)
let mediaStream: MediaStream | null = null
let audioContext: AudioContext | null = null
let scriptProcessor: ScriptProcessorNode | null = null
let speechWs: WebSocket | null = null
/** 已确认的最终识别文本（与后端 confirmedText 同步） */
let voiceConfirmedText = ''

// 检索日志弹窗
const retrievalLogVisible = ref(false)
const currentRetrievalLog = ref<any>(null)

// ===================== 快捷问题 =====================
const quickQuestions = welcomePrompts

// ===================== 生命周期 =====================
onMounted(async () => {
  await loadConversations()
  const idParam = route.params.id
  if (idParam) {
    await switchConversation(Number(idParam))
  }
})

// ===================== 会话管理 =====================
const loadConversations = async () => {
  convLoading.value = true
  try {
    const result = await chatApi.listConversations({ size: 50 })
    conversations.value = result.records || []
  } finally {
    convLoading.value = false
  }
}

const switchConversation = async (convId: number) => {
  currentConvId.value = convId
  const result = await chatApi.getHistory(convId, { size: 100 })
  messages.value = (result.records || []).map((m: Message) => ({
    ...m,
    sources: m.sources ? JSON.parse(m.sources as any) : null,
    retrievalLog: m.retrievalLog ? JSON.parse(m.retrievalLog as any) : null,
    showSources: false,
    isStreaming: false
  }))
  await scrollToBottom()
}

const newConversation = () => {
  currentConvId.value = null
  messages.value = []
  inputText.value = ''
}

const deleteConversation = async (convId: number) => {
  await ElMessageBox.confirm('确认删除该对话？', '提示', { type: 'warning' })
  await chatApi.deleteConversation(convId)
  if (currentConvId.value === convId) {
    newConversation()
  }
  await loadConversations()
  ElMessage.success('已删除')
}

const exportConversation = async (convId: number, title: string) => {
  const token = localStorage.getItem('token')
  const res = await fetch(`/api/chat/export/${convId}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!res.ok) { ElMessage.error('导出失败'); return }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${title || 'medirag-' + convId}.md`
  a.click()
  URL.revokeObjectURL(url)
}

// ===================== 发送消息 =====================
const handleEnterSend = (e: KeyboardEvent) => {
  if (!e.shiftKey) handleSend()
}

const handleSend = () => sendMessage(inputText.value)

const sendMessage = async (text: string) => {
  const content = text.trim()
  if (!content || isStreaming.value) return

  inputText.value = ''
  isStreaming.value = true

  // 添加用户消息
  messages.value.push({
    tempId: Date.now(),
    role: 'user',
    content,
    isStreaming: false
  })

  // 添加 AI 消息占位
  const aiMsg = reactive({
    tempId: Date.now() + 1,
    role: 'assistant',
    content: '',
    sources: null as SourceRef[] | null,
    showSources: false,
    isStreaming: true,
    feedback: 0
  })
  messages.value.push(aiMsg)
  await scrollToBottom()

  // 建立 SSE 连接
  const params = new URLSearchParams({ message: content })
  if (currentConvId.value) params.append('conversationId', String(currentConvId.value))

  const token = localStorage.getItem('token')
  const streamUrl = token
    ? `/api/chat/stream?${params}&token=${encodeURIComponent(token)}`
    : `/api/chat/stream?${params}`
  const eventSource = new EventSource(streamUrl)

  eventSource.addEventListener('token', (e) => {
    const data = JSON.parse(e.data)
    aiMsg.content += data.content
    scrollToBottom()
  })

  eventSource.addEventListener('done', (e) => {
    const data = JSON.parse(e.data)
    aiMsg.sources = data.sources || null
    aiMsg.retrievalLog = data.retrievalLog || null
    aiMsg.isStreaming = false
    isStreaming.value = false
    currentConvId.value = data.conversationId
    eventSource.close()
    loadConversations()
    scrollToBottom()
  })

  eventSource.addEventListener('error', () => {
    aiMsg.isStreaming = false
    aiMsg.content = aiMsg.content || '抱歉，生成失败，请重试。'
    isStreaming.value = false
    eventSource.close()
  })

  eventSource.onerror = () => {
    aiMsg.isStreaming = false
    isStreaming.value = false
    eventSource.close()
  }
}

// ===================== 工具函数 =====================
const renderMarkdown = (content: string) => {
  if (!content) return ''
  return marked.parse(content) as string
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
  }
}

const copyContent = async (content: string) => {
  await navigator.clipboard.writeText(content)
  ElMessage.success('已复制')
}

const submitFeedback = async (msg: any, rating: number) => {
  if (!msg.id) return
  await chatApi.submitFeedback(msg.id, rating)
  msg.feedback = rating
  ElMessage.success(rating === 1 ? '感谢您的反馈！' : '已记录，我们会持续改进')
}

const showRetrievalLog = (msg: any) => {
  currentRetrievalLog.value = msg.retrievalLog
  retrievalLogVisible.value = true
}

// ===================== 语音录入 =====================

const toggleVoice = async () => {
  if (isRecording.value) {
    stopVoiceRecording()
  } else {
    await startVoiceRecording()
  }
}

const startVoiceRecording = async () => {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch {
    ElMessage.error('无法访问麦克风，请检查浏览器权限设置')
    return
  }

  // 以 16000Hz 采样（DashScope Paraformer 要求）
  audioContext = new AudioContext({ sampleRate: 16000 })
  const source = audioContext.createMediaStreamSource(mediaStream)
  // ScriptProcessorNode 每次处理 4096 个样本
  scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1)

  const token = localStorage.getItem('token')
  const wsUrl = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/api/speech/ws?token=${token}`
  speechWs = new WebSocket(wsUrl)
  speechWs.binaryType = 'arraybuffer'

  speechWs.onopen = () => {
    isRecording.value = true
    voiceConfirmedText = ''
    speechWs?.send('START')
  }

  speechWs.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data as string)
      if (data.type === 'transcript' && data.text != null) {
        // 实时更新输入框（实时显示中间结果）
        inputText.value = data.text
        if (data.isFinal) {
          voiceConfirmedText = data.text
        }
      } else if (data.type === 'finished') {
        inputText.value = data.text || voiceConfirmedText
        isRecording.value = false
      } else if (data.type === 'error') {
        ElMessage.error(data.message || '语音识别出错')
        isRecording.value = false
      }
    } catch {
      // 忽略非 JSON 消息
    }
  }

  speechWs.onclose = () => {
    isRecording.value = false
  }

  speechWs.onerror = () => {
    ElMessage.error('语音识别服务连接失败')
    isRecording.value = false
  }

  // 音频处理：Float32 → Int16 PCM → 发送给后端
  scriptProcessor.onaudioprocess = (e: AudioProcessingEvent) => {
    if (speechWs?.readyState !== WebSocket.OPEN) return
    const float32 = e.inputBuffer.getChannelData(0)
    const int16 = new Int16Array(float32.length)
    for (let i = 0; i < float32.length; i++) {
      const s = Math.max(-1, Math.min(1, float32[i]))
      int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
    }
    speechWs.send(int16.buffer)
  }

  source.connect(scriptProcessor)
  // 连接到 destination 防止 Chrome 自动垃圾回收（静音输出）
  scriptProcessor.connect(audioContext.destination)
}

const stopVoiceRecording = () => {
  if (speechWs?.readyState === WebSocket.OPEN) {
    speechWs.send('STOP')
    // 短暂等待 finish-task 结果后关闭
    setTimeout(() => speechWs?.close(), 800)
  }
  scriptProcessor?.disconnect()
  audioContext?.close().catch(() => {})
  mediaStream?.getTracks().forEach(t => t.stop())
  scriptProcessor = null
  audioContext = null
  mediaStream = null
  isRecording.value = false
}

const formatRelativeTime = (dateStr: string) => {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return Math.floor(diff / 86400000) + '天前'
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - var(--header-height) - 48px);
  gap: 0;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--card-shadow);
}

/* ===== 左侧会话列表 ===== */
.chat-sidebar {
  width: 260px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f1f5f9;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.sidebar-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: var(--transition);
  position: relative;
}

.conv-item:hover { background: #f8fafc; }
.conv-item.active { background: rgba(37,99,235,0.08); }

.conv-icon { color: #94a3b8; flex-shrink: 0; }
.conv-item.active .conv-icon { color: #3b82f6; }

.conv-info { flex: 1; min-width: 0; }
.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-meta { font-size: 11px; color: #94a3b8; margin-top: 2px; }

.conv-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}
.conv-item:hover .conv-actions { opacity: 1; }

.conv-action { padding: 4px; }
.conv-delete { color: #ef4444; }

.empty-conv {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #94a3b8;
  font-size: 13px;
}

/* ===== 主聊天区 ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fafbfc;
}

/* 欢迎屏 */
.welcome-screen {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  gap: 16px;
}

.welcome-logo { margin-bottom: 8px; }

.logo-icon-lg {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 32px rgba(37,99,235,0.3);
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
}

.welcome-desc {
  font-size: 15px;
  color: #64748b;
  text-align: center;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 16px;
  max-width: 600px;
}

.quick-q-item {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: var(--transition);
}
.quick-q-item:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: rgba(59,130,246,0.04);
}

/* 消息列表 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-wrapper { width: 100%; }

/* 用户消息 */
.user-message {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 10px;
}

.user-content {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  border-radius: 16px 4px 16px 16px;
  max-width: 70%;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.user-avatar {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
  flex-shrink: 0;
  font-weight: 700;
  color: #fff;
}

/* AI 消息 */
.ai-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 85%;
}

.ai-avatar {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-content-wrapper { flex: 1; min-width: 0; }

.ai-content {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 4px 16px 16px 16px;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.8;
  color: #334155;
  word-break: break-word;
}

.ai-content :deep(h1), .ai-content :deep(h2), .ai-content :deep(h3) {
  margin: 12px 0 8px;
  color: #1e293b;
}
.ai-content :deep(ul), .ai-content :deep(ol) { padding-left: 20px; margin: 8px 0; }
.ai-content :deep(p) { margin: 6px 0; }
.ai-content :deep(strong) { color: #1e293b; }
.ai-content :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.ai-content :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  padding-left: 12px;
  color: #64748b;
  margin: 8px 0;
}

/* 流式光标 */
.streaming-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 18px;
}
.dot {
  width: 6px;
  height: 6px;
  background: #94a3b8;
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; } 40% { transform: scale(1.2); opacity: 1; } }

/* 来源引用 */
.sources-panel {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #f8fafc;
  cursor: pointer;
  font-size: 13px;
  color: #64748b;
  transition: background 0.2s;
}
.sources-header:hover { background: #f1f5f9; }

.toggle-icon { margin-left: auto; transition: transform 0.3s; }
.toggle-icon.rotated { transform: rotate(180deg); }

.sources-list { padding: 8px; display: flex; flex-direction: column; gap: 6px; }

.source-item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.source-index {
  font-size: 12px;
  color: #3b82f6;
  font-weight: 600;
  flex-shrink: 0;
  min-width: 24px;
}

.source-name { font-size: 13px; font-weight: 600; color: #1e293b; }
.source-excerpt { font-size: 12px; color: #64748b; margin-top: 2px; line-height: 1.5; }
.source-meta { font-size: 11px; color: #94a3b8; margin-top: 2px; }

/* 消息操作 */
.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}
.ai-message:hover .message-actions { opacity: 1; }

/* 输入区域 */
.input-area {
  padding: 16px 20px;
  border-top: 1px solid #f1f5f9;
  background: #fff;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: #f8fafc;
  border: 1.5px solid #e2e8f0;
  border-radius: 14px;
  padding: 8px 10px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: #3b82f6; }

.chat-input :deep(.el-textarea__inner) {
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: 0 8px !important;
  resize: none !important;
  font-size: 14px !important;
  color: #1e293b !important;
}

.input-actions { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }

.input-hint {
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 8px;
}

/* 语音录音按钮动效 */
.voice-recording {
  animation: voice-pulse 1.2s ease-in-out infinite;
}

@keyframes voice-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
  50%       { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
}

/* ===== 检索过程可视化弹窗 ===== */
.rl-wrap { display: flex; flex-direction: column; gap: 20px; padding: 4px 0; }

/* 流水线步骤条 */
.rl-pipeline {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #f8fafc;
  border-radius: 12px;
  padding: 16px 12px;
}
.rl-step { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.rl-step-icon {
  width: 36px; height: 36px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.rl-step-icon.blue   { background: #3b82f6; }
.rl-step-icon.purple { background: #7c3aed; }
.rl-step-icon.teal   { background: #0891b2; }
.rl-step-icon.orange { background: #f59e0b; }
.rl-step-icon.green  { background: #10b981; }
.rl-step-icon.amber  { background: #f59e0b; }
.rl-step-label { font-size: 11px; color: #64748b; font-weight: 500; white-space: nowrap; }
.rl-arrow { font-size: 16px; color: #cbd5e1; flex-shrink: 0; }

/* 分区 */
.rl-section { border: 1px solid #f1f5f9; border-radius: 12px; padding: 16px 18px; }
.rl-section-title {
  font-size: 13px; font-weight: 600; color: #475569;
  display: flex; align-items: center; gap: 8px; margin-bottom: 14px;
}
.rl-badge {
  font-size: 10px; font-weight: 700; padding: 2px 7px;
  border-radius: 20px; color: #fff; letter-spacing: 0.3px;
}
.rl-badge.blue   { background: #3b82f6; }
.rl-badge.purple { background: #7c3aed; }
.rl-badge.orange { background: #f59e0b; }

/* Query 改写对比 */
.rl-query-compare {
  display: flex; align-items: center; gap: 12px;
}
.rl-query-box {
  flex: 1; background: #f8fafc; border-radius: 10px; padding: 12px 14px;
  border-left: 3px solid #e2e8f0;
}
.rl-query-box.rewritten { border-left-color: #3b82f6; background: rgba(59,130,246,0.04); }
.rl-query-tag {
  font-size: 10px; font-weight: 600; color: #94a3b8;
  text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px;
}
.rl-query-tag.blue { color: #3b82f6; }
.rl-query-text { font-size: 13px; color: #334155; line-height: 1.5; }
.rl-query-arrow { color: #cbd5e1; flex-shrink: 0; }

/* 召回指标卡 */
.rl-metrics {
  display: flex; align-items: center; justify-content: center; gap: 12px;
}
.rl-metric-card {
  flex: 1; border-radius: 12px; padding: 16px 12px; text-align: center; border-top: 3px solid;
}
.rl-metric-card.purple { border-color: #7c3aed; background: rgba(124,58,237,0.05); }
.rl-metric-card.blue   { border-color: #3b82f6; background: rgba(59,130,246,0.05); }
.rl-metric-card.teal   { border-color: #0891b2; background: rgba(8,145,178,0.05); }
.rl-metric-num {
  font-size: 32px; font-weight: 800; color: #1e293b; line-height: 1;
}
.rl-metric-name { font-size: 13px; font-weight: 600; color: #475569; margin-top: 6px; }
.rl-metric-sub  { font-size: 11px; color: #94a3b8; margin-top: 3px; }
.rl-metric-plus { font-size: 20px; color: #cbd5e1; flex-shrink: 0; }

/* 重排序行 */
.rl-rerank-row {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
}
.rl-rerank-info { display: flex; align-items: baseline; gap: 8px; }
.rl-rerank-num  { font-size: 40px; font-weight: 800; color: #f59e0b; line-height: 1; }
.rl-rerank-desc { font-size: 14px; color: #64748b; }

.rl-empty { text-align: center; color: #cbd5e1; padding: 40px 0; font-size: 14px; }
</style>
