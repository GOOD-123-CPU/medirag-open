# 部署指南 / Deployment Guide

## 部署拓扑

```
                    ┌─────────────────────────────┐
   用户 → :80 ────→  │  frontend (Nginx)           │
                    │  - 静态资源 + SPA 回退       │
                    │  - /api 反代 → backend:8080  │
                    │  - SSE 流式优化              │
                    └──────────┬──────────────────┘
                               │
   :8080 ────→ backend (SpringBoot 3, Java 17)
                    │      │       │      │
              ┌─────┘      │       │      └──────┐
        mysql:3306   redis:6379  milvus:19530  minio:9000
        (业务数据)    (缓存)      (向量库)     (原始文件)
```

## 环境要求

- Docker 24+ 与 Docker Compose v2
- 2 核 4GB 内存最低配置（Milvus 较吃内存，建议 4GB+）
- 一个 OpenAI 兼容的 LLM API Key（DashScope / DeepSeek / OpenAI / vLLM 等）

## 首次部署

```bash
cp .env.example .env
vim .env    # 必改：DB_PASSWORD、MINIO_ACCESS_KEY/MINIO_SECRET_KEY、JWT_SECRET、LLM_API_KEY
docker compose up -d
docker compose logs -f backend   # 等待看到启动完成日志
```

后端首次启动会自动：
1. 执行 `sql/medirag.sql` 初始化库表与演示数据（由 MySQL 容器完成）
2. 校验 AI 配置并初始化 `sys_ai_config` 默认参数
3. 创建 MinIO 存储桶（若不存在）

## 启用重排序微服务（可选）

```bash
docker compose --profile full up -d
# 然后在 .env 中设置：
# RERANKER_API_URL=http://reranker:8001/rerank
# RERANKER_MODEL=bge-reranker-v2-m3
docker compose up -d backend   # 重启后端生效
```

## 导入知识库

登录管理员账号 → 知识库页面 → 上传文档（pdf/docx/doc/txt）→
选择科室分类 → 等待状态变为 `ready`。

批量导入脚本见 `scripts/import_kb_folder.py`（需本地 Python 环境与后端运行中）。

## 健康检查

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP",...}
docker compose ps
```

## 数据备份

```bash
docker exec medirag-mysql mysqldump -uroot -p"$DB_PASSWORD" medirag > backup.sql
docker run --rm -v medirag_mysql_data:/data -v "$PWD":/backup alpine \
    tar czf /backup/mysql_data.tgz /data
```

## 常见问题

| 现象 | 排查 |
|------|------|
| backend 反复重启 | `docker compose logs backend`；多数是 .env 缺必填项或 MySQL 未就绪 |
| 问答无引用来源 | 知识库无 ready 文档，或触发低置信度兜底（属正常安全行为） |
| 上传文档一直 processing | 检查 LLM Embedding 接口连通性；`EMBEDDING_ALLOW_PSEUDO_FALLBACK=false` 时失败会进入 failed |
| Milvus 内存不足 | 最低 4GB 内存；或改用 Milvus Lite（开发模式） |

## 生产加固清单

- [ ] 删除演示账号（admin/doctor1/user1）
- [ ] 全部默认口令已更换
- [ ] HTTPS 已启用（Nginx 层或独立网关）
- [ ] CORS_ALLOWED_ORIGINS 已收敛为实际域名
- [ ] LLM API Key 已设置服务商侧用量上限
- [ ] 数据库定期备份策略已配置
