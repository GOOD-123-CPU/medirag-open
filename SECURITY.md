# 安全政策 / Security Policy

## 支持的版本

| 版本 | 支持状态 |
|------|----------|
| main 分支最新 | ✅ 支持 |
| 其他历史提交 | ❌ 不支持 |

## 报告漏洞

如果你发现安全漏洞，请**不要**创建公开 Issue，而是：

1. 发送邮件至项目维护者（仓库主页可见的邮箱），标题以 `[MediRAG Security]` 开头
2. 描述漏洞细节、复现方式与影响范围
3. 我们会在 72 小时内确认收悉，并在修复后致谢（如你愿意）

## 已知安全设计说明

- **密钥管理**：所有密钥通过环境变量注入，`.env` 已被 `.gitignore` 排除；
  CI 内置密钥扫描（`.github/workflows/ci.yml`），发现硬编码密钥会阻断合并
- **JWT**：启动时校验密钥强度（≥32 字节，拒绝占位值），HS256 签名
- **CORS**：默认仅允许本地开发来源，通过 `CORS_ALLOWED_ORIGINS` 配置；
  配置为 `*` 时自动禁用凭据，避免不安全组合
- **Actuator**：仅暴露 `/actuator/health`，不暴露 env/metrics 等敏感端点
- **上传文件**：默认 100MB 上限，类型限制 pdf/docx/doc/txt

## 部署安全建议

- 使用强随机 `JWT_SECRET`（`openssl rand -base64 48`）
- 更改 MySQL/MinIO 默认口令，删除演示账号
- 不要将后端 8080 端口直接暴露公网，建议置于 Nginx/网关之后并启用 HTTPS
- 生产环境为 MySQL/Redis/Milvus 配置网络隔离（仅容器网络内可达）
- 定期轮换 LLM API Key 并设置服务商侧用量告警
