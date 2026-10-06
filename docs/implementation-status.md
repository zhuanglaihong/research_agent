# 实施状态：个人本地版

## 最新前端修订：对话与可视化管理（2026-10-06）

- 已替换静态 ResearchDemoPage 为项目/场景侧栏、中间对话与计划审批、右侧流程/产物/运行摘要/真实指标/实现说明；提供回放暂停和运行确认检查点。任意追加消息明确提示未调用模型。
- 新增 research-demo.ts 五场景及 Ollama 实测指标、脚本、人工审阅下载。Attention Is All You Need 是论文预览的参考，不是已复现论文；仓库场景没有 clone 或安装依赖。
- 默认演示 qwen3:8b 生成的二次函数优化 Run 1，loss 4.0 → 0.04611686018427385；保留 Digits 人工基线。网页图从真实 JSON 渲染，不伪造实验数值。
- 前端 npm run build 类型检查和生产构建通过；保留既有主包 776 KB 警告。本轮未改后端，后端 9 项测试结论来自上一轮验收。
- 新布局仅用于 /demo，本地真实工作台仍是原任务交互，尚未完成持续对话 UI 接入。本轮未更新公共 Pages。完整目标仍未完成，不能改称完整自治科研产品。
- 浏览器已验证发送、计划确认、脚本运行确认和真实指标结果；论文预览明确无训练结果。桌面/手机断点检查通过，无手机页面横向溢出；Pages 配置构建通过，截图见 docs/screenshots。

更新时间：2026-10-06。当前代码以 Java 21 + Spring Boot 3.5.4 为后端，Vue 3 为前端；无账号系统，默认 H2 文件库与 `demo` 模式。

## 最新验收：本地闭环已实跑（优先于下文早期验证状态）

阶段源码已本地提交 ca0a019。现完成 Ollama生成→人工审批Java Runner→实际指标→Agent inspectExperiment→分析/下一步草稿；loss为4.0→0.04611686018427385，运行退出码0。服务重启后任务可查询；直接检查独立H2文件库确认四个任务的消息窗口已保存。最终mvnw verify构建成功，9项测试通过，包括新增MCP项目隔离与协议/Origin边界测试。

案例和验收记录见 docs/chain-validation.md、examples/ollama-feedback-case。一次分析任务失败根因未定位；成功报告仍有命令参数、文件清单和解释错误，保存人工审阅。当前可表述为已实测本地首版闭环，不承诺稳定自治科研。

仅补齐Compose的LLM_PROVIDER环境变量传递，未进行Docker实跑（本机无Docker CLI）；当前Java镜像不包含Python和Ollama，实验室共享部署仍待后续实现。此轮源码提交保存在本地Git。

## 2026-10-06 简历版最小交付

用户将本轮范围收窄为简历版必要架构与网页 Demo；公网后端、实验室服务器、多用户功能和高级绘图延期。GitHub Pages 只展示静态交互，不执行访问者的代码。

- 已增加 V6 消息存储迁移和 JdbcChatMemoryStore，live 任务的模型/工具消息窗口写入 H2；不同任务使用独立 memory ID，不把工具结果混到其他任务。
- 新任务上下文读取同项目最近三项已完成任务的请求和结果，按时间排序并限制长度。不是完整跨任务共享工具消息，也不表示自动恢复中断步骤。
- 新增只读科研工具，供 LangChain4j Agent 读取论文列表、项目实验、日志与指标，并校验运行归属；实验结果可指导后续任务，但模型只草拟、不直接执行修改或重跑。
- 增加绑定项目的 MCP Streamable HTTP 工具端点：读取笔记、论文、实验列表和指定实验结果；只监听回环地址，Origin 白名单，不暴露代码执行和文件写入。完成实际服务启动及 initialize→tools/list→tools/call 冒烟验证；支持协议版本 2025-03-26，无会话模式。
- Demo 增加五场景交互导览、MCP 工具清单及项目/任务记忆说明；保留静态任务交互和真实训练快照的区别。
- 后端 `mvn -DskipTests package` 编译打包通过；独立 Spring Boot 服务成功启动并应用 H2 V6 迁移。前端 `npm run build` 和类型检查通过；浏览器验证五场景切换。构建有既存 775 KB 主 chunk 警告。
- 最近一次 8 项测试通过发生在加入 MCP/实验观察工具之前；没有把它记作此后代码的回归测试。
- Ollama 模型配置已实现：`LLM_PROVIDER=ollama`、本地 `/v1` endpoint、空 API Key；本机 `qwen3:8b` 完成真实 Agent Tool Calling（读取项目文件、写入隔离任务产物，任务状态 `SUCCEEDED`）。本机 `mvnw.cmd -DskipTests package` 与前端 `npm run build` 通过；GitHub Actions 在 commit `38795d6` 执行 `mvnw verify` 和前端构建均成功。
- README 已补 Windows 逐步配置教程：用 `ollama list` 查看已安装模型、检查本机服务、编辑 `.env`、重启后端、提交验证任务；记录本机已有 `qwen3:8b`、`deepseek-coder:6.7b`、`bge-large:335m`，并解释服务器/容器网络地址和常见故障。
- Ollama/API 部署方式已加到 README 和静态 Demo。Demo 前端更新已推送 commit `1e2a0ad`。
- Pages 旧 deploy 曾停在 `waiting`；更新并推送 `cancel-in-progress: true` 后，最新工作流 `37445474843` deploy 成功，`37445474741` 构建与测试成功。公开页面 HTTP 200，已检查当前 Demo JS 包含 Ollama/API 配置卡片。
- 尚未完成：对新工具的集成回归测试；表单持续对话；向量 RAG；代码 diff/审批应用；Runner 的系统级隔离、长时运行与 Agent 自动反馈闭环。当前可写首版 Agent 和只读 MCP，不宣称完整自治科研。

| 模块 | 状态 | 实际能力 |
| --- | --- | --- |
| 项目与任务 | 已实现首版 | 创建本地项目、任务审批/取消、后台 Worker、持久化 SSE 事件、查看与下载产物 |
| Agent | 已实现首版 | LangChain4j、受限文件与实验观察工具、生成独立代码产物；live 模式支持 Ollama 和 OpenAI 兼容模型 |
| 文件记忆 | 已实现首版 | 每项目 `.research_agent/memory.md`；UI/API 编辑，任务提示词读取，Agent 可追加 |
| 科研知识库 RAG | 已实现本地首版 | H2 文档与分块持久化、约 900 字符重叠分块、项目内 BM25 检索、片段引用、删除、旧文档启动回填；无需 API 密钥 |
| 前端进度 | 已实现首版 | 审批、排队、检索、生成阶段及事件流；阶段不是精确模型百分比 |
| 实验助手 | 已实现首版、默认关闭 | 人工确认生成脚本后后台执行；5 分钟超时、1 MB 日志上限、取消；读取 JSONL/CSV 数值指标、摘要、SVG 曲线，供后续任务草拟；无系统沙箱 |
| 真实实验案例 | 已在 CPU 与 Java Runner 实际运行 | Digits 分类基线，三随机种子，96.39% 测试准确率；网页呈现记录快照，代码人工编写 |
| arXiv 论文订阅 | 已实现首版 | 英文主题、手动或每日 08:00 同步最新 5 篇、去重并把原始摘要加入项目检索 |
| 论文/实验后续任务 | 已实现首版 | 从论文摘要草拟代码任务、从运行日志草拟分析/绘图脚本任务，需人工确认 |
| PDF 论文到代码 | 已实现文本型 PDF 导入、方法片段审阅和待确认代码任务 | 公式/图表未解析，扫描件需 OCR，demo 模式只生成固定样例 |
| 自动调参、创新点提炼、向量嵌入/PgVector、论文级绘图 | 未实现 | 当前 SVG 是基础指标曲线；不会自主修改参数或重启实验 |
| MCP | 已实现本机首版 | Streamable HTTP JSON-RPC，只读读取项目笔记/论文/实验；单独完成 initialize、发现、检索冒烟，未做完整外部客户端互操作测试 |
| 静态网页演示 | 已更新部署 | commit `38795d6` 对应 Pages deploy 成功；公开页面与 JS 资源均已验证 |
| 部署 | 本机直接运行 | 服务默认只绑定 127.0.0.1；Compose 是可选方案，未在本机验证 |

## 本轮改动

- 清除旧平台的登录、管理员、网站生成、截图、COS、Redis 等源文件和依赖；保留科研流程所需模块，包名改为 `org.researchagent`。
- 新增项目 Markdown 文件记忆、手动文献笔记、H2 分块 BM25 检索和任务检索事件。arXiv 摘要同步与手动 PDF 导入均可进入当前项目知识库；当前不使用 PgVector。
- 工作台新增记忆与笔记编辑、手动检索及任务阶段显示。创建/批准任务仍由用户控制。
- 新增 `research/run` 的可选 Python Runner，以及 `research/paper` 的 arXiv Atom 同步；前端增加运行日志和论文列表。
- 新增浏览器专用 `/#/demo` 与手动触发的 Pages 工作流；访问者可检查交互，但不能操作真实工作区。
- `.env.example` 默认 demo；无 API 密钥也能验证完整任务链路。生成代码不会被自动运行或修改原仓库。

## 验证

- `mvnw.cmd -B -ntp test`：7 项科研测试通过，覆盖无登录项目、路径边界、后台 demo、模拟工具模型、PDF 导入、分块检索的项目隔离和删除。
- `frontend/npm run build`：Vue 类型检查与生产构建通过；Vite 提示主包体积较大，属于优化项。
- `scripts/smoke-demo.ps1` 独立进程烟测通过：H2 v1–v5 迁移、创建项目、记忆读写、检索事件与 2 个 demo 文件；此前 Runner 和 arXiv 同步也通过烟测。
- 静态演示已部署至 https://zhuanglaihong.github.io/research_agent/ ，Pages 工作流成功，公开页面 HTTP 200；浏览器验证创建、审批、事件、代码预览和固定指标曲线。
- 真实模型小型代码生成与执行链路已通过，见 live-validation.md；论文复现质量、Docker、macOS/Linux 实机尚未验证。
- `scripts/smoke-demo.ps1 -RealCase`：真实 sklearn/Matplotlib 训练经 Java Runner 执行成功，指标与 SVG 接口通过；修正清空进程环境后 Matplotlib 配置目录不可用的问题。

## 发布边界

- 单实例 Worker 最多同时处理两个生成任务；服务重启会将 RUNNING 标记为 INTERRUPTED。
- Python Runner 单次最多 5 分钟，默认关闭；只限制执行入口、时长和日志，不提供进程/文件/网络隔离。不能把它当作安全沙箱。
- 无认证 API 只面向可信本机。公网 demo 需要访客隔离或只读化，不可原样暴露。
- 本地两个上游仓库未见 LICENSE；需核对剩余复用源码的授权。仓库与静态交互 Demo 均已发布。
- 用户要求任何卡住超过 5 分钟的测试先停止并审阅；不要用未验证结果填充简历。
