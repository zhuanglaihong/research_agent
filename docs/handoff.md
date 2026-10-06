# 执行交接：research_agent

## 当前产品

个人本地科研编程 Agent。安装 JDK 21、Node.js 22 后无需登录、Redis、MySQL、Docker 或模型密钥即可运行固定 demo。真实代码生成需在根目录 `.env` 配置兼容 OpenAI 协议的工具模型。Java API 默认只监听 127.0.0.1。

## 已实现

创建项目 → 编辑文件记忆/笔记或订阅 arXiv 主题 → 自然语言提交任务 → 审批固定步骤 → H2 后台队列 → LangChain4j 文件工具生成独立产物 → SSE 阶段/检索事件 → 查看/下载成果。可选开启人工确认的 Python Runner，查看运行状态与日志，再草拟分析任务。笔记检索是本地词项匹配，尚无嵌入模型或 PgVector。代码不会自动覆盖原科研仓库。

代码位于 `src/main/java/org/researchagent/research` 和 `frontend/src/pages/research`；H2 迁移在 `src/main/resources/db/local/`。论文模块为 `research/paper`，Runner 为 `research/run`。旧平台用户、管理员、网站生成、截图、COS 和 Redis 源码已移除。

## 验证

1. 根目录 `./mvnw.cmd -B -ntp verify`：7 项测试通过，约 10 秒。
2. `frontend/npm run build`：类型检查及生产构建通过，主包体积有优化提示。
3. 根目录 `./scripts/smoke-demo.ps1 -EnableRunner -SyncPapers`：独立 jar 成功迁移 H2 v1–v3，命中笔记、输出 RETRIEVAL 事件、运行 Python 样例并同步 5 篇 arXiv 论文，约 8 秒。
4. GitHub Pages 已部署至 https://zhuanglaihong.github.io/research_agent/ ，公开页面的审批、事件、代码预览和实验曲线已通过浏览器验证。

## 下一步

1. 检查本地工作台的论文与实验模块交互，修正任何操作障碍。
2. 用有效模型密钥评测一个小型真实代码任务，再增加可复现案例和成本/耗时记录。
3. PDF 文本导入、分块 BM25、指标与 SVG、Digits 真实训练案例已完成；后续做自动调参、语义向量检索与 MCP。
4. GitHub 仓库与 Pages 已上线；剩余上游复用授权需核对。前端变更推送 main 自动更新 Pages，也支持手动运行；无认证 API 不可直接对公网暴露。

固定步骤不是自治科研规划；demo 是固定产物，不调用模型；live 用同步 LangChain4j 工具调用。Worker 最多两个生成任务，Runner 最多一个进程。Runner 默认关闭，且不是系统沙箱；没有自动调参或论文忠实复现保证。测试卡住超过 5 分钟先停止并审阅。
