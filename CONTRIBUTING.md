# 贡献指南 / Contributing

感谢你对 MediRAG 的关注！欢迎通过以下方式参与贡献。

Thanks for your interest in contributing to MediRAG!

## 提交 Issue

- **Bug 报告**：请附上复现步骤、期望行为、实际行为、环境信息（OS/JDK/Node/Docker 版本）
- **功能建议**：请说明使用场景与预期收益
- **安全问题**：请勿公开提交，见 [SECURITY.md](SECURITY.md)

## 提交 PR

1. Fork 仓库并从 `main` 创建特性分支：`git checkout -b feat/your-feature`
2. 提交前请确保：
   - 后端 `mvn verify` 通过（CI 会自动执行）
   - 前端 `npm run build` 通过
   - 新增/修改的配置项已同步更新 `.env.example` 与 `docs/`
   - **不提交任何密钥、个人数据或业务数据**
3. Commit message 建议：
   - `feat: 新增 xxx` / `fix: 修复 xxx` / `docs: 文档 xxx` / `refactor: 重构 xxx` / `chore: 杂项 xxx`
4. PR 描述请说明改动动机、方案与验证方式

## 代码规范

- Java：遵循项目现有风格（Lombok + Spring Boot 3 惯例），UTF-8 编码
- 前端：TypeScript 优先，组件放 `frontend/src/views` / `components`
- 所有文本文件统一 UTF-8（无 BOM）
- 涉及 RAG 链路改动请同步更新 `docs/rag-pipeline.md`

## 医疗内容红线

- 不得让系统输出未经知识库支撑的诊断结论或处方建议
- 新增医疗相关功能必须保留/增强安全兜底（紧急提示、置信度阈值）
- 示例数据必须是虚构演示数据或可溯源的公开文献元数据（PMID/DOI）
