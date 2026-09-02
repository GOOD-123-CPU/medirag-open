import request from '@/utils/request'

export interface Conversation {
  id: number
  title: string
  messageCount: number
  lastActive: string
  createTime: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  sources: SourceRef[] | null
  retrievalLog: any
  feedback: number
  isFallback: number
  createTime: string
}

export interface SourceRef {
  index: number
  name: string
  chapter: string
  pageNumber: number
  content: string
  score: number
}

export const chatApi = {
  // 获取会话列表
  listConversations: (params = {}): Promise<any> =>
    request.get('/chat/conversations', { params }),

  // 获取消息历史
  getHistory: (conversationId: number, params = {}): Promise<any> =>
    request.get(`/chat/history/${conversationId}`, { params }),

  // 获取检索日志
  getRetrievalLog: (messageId: number): Promise<any> =>
    request.get(`/chat/retrieval-log/${messageId}`),

  // 删除会话
  deleteConversation: (conversationId: number): Promise<void> =>
    request.delete(`/chat/conversations/${conversationId}`),

  // 提交反馈
  submitFeedback: (messageId: number, rating: number): Promise<void> =>
    request.post('/chat/feedback', { messageId, rating })
}
