# research_agent 当前架构

本地单用户应用：Vue 3 工作台调用 Java 21 / Spring Boot 3 API。H2 文件数据库存项目、任务、事件、文献笔记、论文和实验记录；工作区存项目记忆、生成产物及日志。无登录、Redis/MySQL 服务。

| 模块 | 路径 | 当前职责 |
| --- | --- | --- |
| 项目与工作区 | `research/project`、`research/workspace` | 项目 CRUD、目录边界与符号链接检查 |
| 文件记忆与笔记 | `research/knowledge` | Markdown 记忆、H2 笔记、词项检索、带来源片段 |
| 任务 | `research/task` | 审批、H2 队列、后台 Worker、事件回放与 SSE、产物下载 |
| Agent | `research/agent` | LangChain4j Prompt、文件 Tool Calling、笔记检索工具；demo 固定示例 |
| 论文 | `research/paper` | arXiv Atom 主题同步、去重、摘要入库；每天 08:00 定时检查 |
| 实验 | `research/run` | 人工确认的 Python 运行、超时/取消、日志与状态；默认关闭 |
| 界面 | `frontend/src/pages/research` | 本地工作台和独立静态演示 |

任务流程：`WAITING_APPROVAL → QUEUED → RUNNING → SUCCEEDED/FAILED`，可取消。Worker 最多并行两项生成任务；模型调用不绑在浏览器连接上。服务重启将 RUNNING 标为 INTERRUPTED。事件写入 H2，SSE 客户端可从游标读取历史。阶段 UI 不是模型 token 或训练进度百分比。

Agent 只读取限定工作区内文件，并把生成代码写进 `.research_agent/tasks/<taskId>/`；不自动修改原项目。项目记忆与检索到的笔记片段会进入任务提示词。笔记不是可信指令，系统提示词要求把它们当资料并标明来源。当前只有本地词项 RAG，不支持 PDF 解析或向量检索。arXiv 摘要由固定源同步并加入笔记；没有模型创新点提炼。

Runner 是另一个明确审批的流程：用户选中生成的 `.py` 文件，查看解释器和工作目录，再确认启动。Java ProcessBuilder 不经 shell；单进程和 1 MB 日志上限，默认关闭。时限由 `RESEARCH_RUN_MAX_MINUTES` 设置，范围 1–1440 分钟，默认 5 分钟。服务重启会终止执行并将记录标为中断。它仍能执行任意 Python 代码，没有文件系统或网络隔离，不能用于不可信代码或公网访客。

公开 GitHub 仓库通过固定 `git clone --depth 1` 参数导入项目工作区，URL 限制为 `https://github.com/{owner}/{repo}`，不经 shell，最多运行 2 分钟；导入记录保存 URL、相对路径和 HEAD commit。论文任务提示 Agent 先查看已导入仓库，生成内容仍写入独立任务目录，不会直接改动仓库。

默认后端只监听 `127.0.0.1`。无认证的工作区 API 不能直接公网部署；GitHub Pages 使用独立的固定样例静态演示。Compose 只是可选的本机打包方式，当前环境未验证。
