# 简历表述草稿

项目名：`research_agent` — 本地科研编程 Agent 工作台。

- 基于 Java 21、Spring Boot 3.5、LangChain4j 和 Vue 3 实现本地科研编程工作台；支持自然语言任务、人工审批、文件 Tool Calling 与代码产物查看/下载。
- 设计 H2 持久化任务、事件与文献笔记，使用后台 Worker + SSE 展示任务阶段；关闭网页后任务继续，重启时标记中断。
- 实现每项目 Markdown 文件记忆与带来源的本地词项检索，将相关笔记片段注入 Agent 上下文，并在前端展示检索事件。
- 接入 arXiv Atom 主题订阅与每日定时同步，支持论文去重、摘要入库及论文到代码任务草拟；加入人工确认的 Python Runner，记录状态与日志并支持取消/超时。

2026-10-06 简历版边界：LangChain4j Agent 工具包含项目检索、论文列表和实验运行观察；任务内消息窗口保存在 H2，跨任务只带入最近三项成功任务的请求/结果摘要。新增绑定项目的只读 MCP Streamable HTTP 工具（笔记、论文、实验列表、运行指标/日志），完成 initialize/tools/list/tools/call 本机冒烟。真实模型代码生成与人工审批 Python Runner 有先前独立验证记录。前端 Demo 介绍五个产品场景；固定交互不连接 LLM，Digits 结果是人工编写脚本的训练快照。

另已在 Windows 本机以 Ollama `qwen3:8b` 完成 live Agent 验证：实际调用 `listProjectFiles` 和 `writeArtifact` 两个工具，产物写入隔离任务目录，任务状态 `SUCCEEDED`。无需 API Key；`LLM_PROVIDER=ollama` 与 OpenAI 兼容 API 可通过 `.env` 切换。GitHub Actions Maven `verify`、前端构建和最新 Pages 发布均通过（`38795d6`）。

可写：Java/Spring Boot/LangChain4j Agent Tool Calling、Ollama 本地模型实跑、带来源的本地 BM25 RAG、H2 持久任务消息和 SSE、人工审批的 Python Runner 与实验结果观察、只读 MCP 工具端点。不可写：PgVector/语义向量、多用户生产权限、论文方法复现成功、自动改代码/调参/重跑或执行沙箱。新 Agent 观察工具与 MCP 尚未通过完整集成回归测试，应按源码实现和 MCP 冒烟的证据范围表述。Pages commit `38795d6` 已部署成功。
