# API 接口概览

> 认证方式：除白名单接口外，均需 `Authorization: Bearer <JWT>`。
> 统一响应体：`{ "code": 200, "message": "success", "data": ... }`

## 用户模块 `/api/user`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /login | 登录（返回 JWT） |
| POST | /register | 注册（默认普通用户） |
| POST | /forgot-password/{step} | 找回密码 |
| POST | /refresh-token | 刷新令牌 |
| GET  | /info | 当前用户信息 |
| PUT  | /update | 更新昵称/手机号/头像 |
| PUT  | /health-profile | 更新健康档案（问答时注入） |

## 智能问答 `/api/chat`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /stream?conversationId&message | **SSE 流式问答**（核心接口） |
| GET  | /conversations | 会话分页列表 |
| GET  | /history/{conversationId} | 会话历史消息 |
| GET  | /retrieval-log/{messageId} | 检索过程日志（可视化） |
| DELETE | /conversations/{id} | 删除会话 |
| POST | /feedback | 回答反馈（1/-1） |
| GET  | /export/{conversationId} | 导出会话 Markdown |

### SSE 事件类型（/stream）

| event | payload | 说明 |
|-------|---------|------|
| rewrite | {original, rewritten, fromCache?} | Query 改写结果 |
| retrieval | {vectorCount, bm25Count} | 双路召回数量 |
| rerank | {topK} | 重排序后数量 |
| start | {message} | 开始生成 |
| token | {content} | 流式 token（打字机） |
| done | {sources, retrievalLog, responseTime, ...} | 完成，含完整引用 |
| error | {message} | 失败 |

## 知识库 `/api/knowledge`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /page | 文档分页 |
| POST | /upload | 上传文档（multipart：file/category/description） |
| POST | /retry/{id} | 失败重试 |
| DELETE | /{id} | 删除 |

文档状态机：`uploading → processing → ready / failed`

## AI 配置 `/api/ai-config`（管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /groups | 配置分组及当前值 |
| PUT  | /group/{group} | 保存分组（热生效） |
| PUT  | /reset/{group} | 恢复默认 |

## 数据统计 `/api/stats`（管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /overview | 用户/会话/问答/知识库总量 |
| GET | /trend | 问答趋势 |
| GET | /hot-keywords | 热门关键词 |

## 重排序服务（reranker-service，端口 8001）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /health | 健康检查 |
| POST | /rerank | DashScope 兼容 rerank，见 reranker-service/README.md |
