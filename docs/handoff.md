# 执行交接：research_agent

## 当前产品

个人本地科研编程 Agent。安装 JDK 21、Node.js 22 后无需登录、Redis、MySQL、Docker 或模型密钥即可运行固定 demo。真实代码生成支持 Ollama（本机验证 qwen3:8b）和 OpenAI 兼容工具模型；后者需 API Key。Java API 默认只监听 127.0.0.1。

## 已实现

创建项目 → 编辑文件记忆/笔记或订阅 arXiv 主题 → 自然语言提交任务 → 人工审批 → H2 后台队列 → LangChain4j 文件、知识检索、论文与实验观察工具 → SSE 事件 → 查看/下载成果。可选开启人工确认的 Python Runner；Agent 能读取项目论文、实验日志与指标并起草后续任务。另提供绑定项目的只读 MCP 工具端点。笔记检索是本地 BM25 词项匹配，尚无 Embedding/PgVector。代码不会自动覆盖原科研仓库。

代码位于 `src/main/java/org/researchagent/research` 和 `frontend/src/pages/research`；H2 迁移在 `src/main/resources/db/local/`。论文模块为 `research/paper`，Runner 为 `research/run`。旧平台用户、管理员、网站生成、截图、COS 和 Redis 源码已移除。

## 验证

1. 根目录 `./mvnw.cmd -B -ntp verify`：7 项测试通过，约 10 秒。
2. `frontend/npm run build`：类型检查及生产构建通过，主包体积有优化提示。
3. 根目录 `./scripts/smoke-demo.ps1 -EnableRunner -SyncPapers`：独立 jar 成功迁移 H2 v1–v3，命中笔记、输出 RETRIEVAL 事件、运行 Python 样例并同步 5 篇 arXiv 论文，约 8 秒。
4. GitHub Pages 已部署至 https://zhuanglaihong.github.io/research_agent/ ，公开页面的审批、事件、代码预览和实验曲线已通过浏览器验证。
5. Ollama live 模式已在 Windows 本机通过 `qwen3:8b` 实际调用 `listProjectFiles`、`writeArtifact`，任务为 `SUCCEEDED`；最新 GitHub Actions 的 Maven 验证、前端构建和 Pages 部署均通过（`38795d6`）。

## 下一步

1. 检查本地工作台的论文与实验模块交互，修正任何操作障碍。
2. 用有效模型密钥评测一个小型真实代码任务，再增加可复现案例和成本/耗时记录。
3. PDF 文本导入、分块 BM25、指标与 SVG、Digits 真实训练案例和本地只读 MCP 工具端点已实现；后续先做 Agent 新工具的集成回归和实验反馈任务草拟，再评估向量检索/高级绘图。
4. 检查剩余上游复用源码的授权；无认证 API 不可公网暴露。后端代码/文档修改尚在本地，公开发布前先处理源码授权与提交边界。

固定步骤不是自治科研规划；demo 是固定产物，不调用模型；live 用同步 LangChain4j 工具调用。Worker 最多两个生成任务，Runner 最多一个进程。Runner 默认关闭，且不是系统沙箱；没有自动调参或论文忠实复现保证。测试卡住超过 5 分钟先停止并审阅。
