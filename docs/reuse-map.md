# 原型组件复用映射

> **2026-10-05 决策更新：** 以下表格记录早期多用户平台复用设想；当前实际产品是本地单用户 Agent。UserController/AuthInterceptor、Redis 会话/缓存、网站生成/部署等未进入运行主链，默认存储是 H2 文件库。组件真实状态以 `docs/implementation-status.md` 和当前入口代码为准。

符号含义：**保留**可先按原样启动；**适配**保留一部分设计/代码并改造；**参考**仅存快照，不参与构建；**移出**不进入科研主链路。

实际复制文件与 SHA256 见 [`reuse-manifest.csv`](reuse-manifest.csv)。本表说明目标用途；未列出的其余基线文件仍按用途复核，不能因已复制自动算作复用。

## yu-ai-code-mother 单体基线（246 文件）

| 路径/组件 | 决策 | research_agent 用途 | 后续动作 |
|---|---|---|---|
| `pom.xml`、`mvnw*`、`.mvn/` | 适配 | Spring Boot + LangChain4j 依赖起点 | 修改 artifact/name，审计 dev 包下自定义类 |
| `src/main/java/.../YuAiCodeMotherApplication.java` | 适配 | Spring Boot 入口、Mapper scan | 包名迁移计划、Bean 初始化与配置扫描验证 |
| `UserController`、`UserService*`、User mapper/entity | 保留/复核 | 用户登录、权限主体 | 检查密码、cookie、安全配置，保留最低限度 |
| `AppController`、`AppService*`、App mapper/entity | 适配 | 过渡项目 CRUD 与权限模式 | 新建 ResearchProject，不长期把任务塞入 App 字段 |
| `ChatHistoryController/Service*`、mapper、entity | 适配 | 对话历史 | 从 appId 归属适配至 projectId/taskId，检查用户权限 |
| `AppChatPage.vue`、`AppEditPage.vue`、`AppCard.vue`、`AppDetailModal.vue` | 适配 | 对话布局与表单/卡片组件参考 | 改成科研项目；移出可视化网站编辑器 |
| `BasicLayout.vue`、`GlobalHeader.vue`、`GlobalFooter.vue`、登录注册、Router、Pinia store | 保留/适配 | 产品导航、认证、状态管理 | 品牌改成 research_agent，增加项目/任务路由 |
| `MarkdownRenderer.vue`、request.ts、api 目录、SSE 页面逻辑 | 适配 | Markdown、请求与流式事件展示 | 事件要带 task/run/event id，刷新支持快照恢复 |
| `AiCodeGeneratorServiceFactory`、`AiCodeGeneratorService` | 适配 | LangChain4j 服务创建与记忆方式 | 新建 ResearchAgentService；上下文按 project/task 隔离 |
| `src/main/java/dev/langchain4j/**` 私有同包源码（manifest 8 文件） | 替换并从编译源排除 | 上游把自定义实现放在官方 `dev.langchain4j` FQCN 下，包含 TokenStream/StreamingChatModel 等 | 对照 POM 与调用方，改用官方 LangChain4j artifact/API；不把这些 fork 文件并入新科研模块 |
| `ToolManager`、`BaseTool`、File*Tool | 适配 | 工具注册与安全文件工具基础 | 全面加入 workspace 根目录校验、审计日志 |
| `AiCodeGeneratorFacade`、CodeParser/CodeFileSaver | 适配/移出 | 分阶段处理思路、保存器/解析器模式 | 生成流程改任务步骤；谨慎复用代码落盘逻辑 |
| `StreamHandlerExecutor` 与各 StreamHandler | 适配 | 流事件转前端 | 改成持久 task_event + SSE 订阅，不以流结束作为任务结束 |
| Prompt `src/main/resources/prompt/codegen-*` | 移出/适配 | 可参考按任务拆 prompt 的结构 | 新写科研规划、论文分析、代码、结果分析 prompts |
| `PromptSafetyInputGuardrail` | 适配 | 用户输入护栏参考 | 替换适用于科研项目，并做测试；不声称覆盖所有注入 |
| `RedisChatMemoryStoreConfig` | 保留/适配 | 会话记忆 | 重要任务状态落 MySQL；Redis 不作为唯一恢复源 |
| `RedisCacheManagerConfig`、RateLimit、AuthCheck/AOP | 保留/复核 | 缓存/限流/权限机制 | 对照任务接口安全及缓存失效行为 |
| `VueProjectBuilder`、`StaticResourceController`、截图、项目部署/下载 | 移出 | 网站生成专用 | 科研新流程不 build Vue 或作为静态网站部署 |
| MySQL Mapper XML、sql/create_table.sql | 适配 | schema 参考 | Flyway 迁移、research_project/task/run/event 表 |
| `CosManager`、COS config、封面/图片资源 | 移出/按需 | 无科研必要 | 论文或产物对象存储未来再评估 |
| Selenium、COS、Redisson 等 POM 依赖 | 复核/删除 | 无默认用途 | 依赖树和引用搜索后删未用依赖 |
| Actuator、Prometheus、grafana、prometheus.yml | 保留/适配 | 服务观测基线 | 增加队列、Runner 心跳、任务失败、模型调用指标 |
| `langgraph4j-*`、Studio 配置 | 可选/默认不接主链 | 有状态图参考 | 先实现 task step 持久化；有复杂分支需求再单独评估 |
| test、application profile、`.env*`、静态 HTML 演示 | 复核 | 测试/配置参考 | 清理含本机值/secret 的文件；本地测试需隔离 |

## yu-ai-agent 参考快照（28 文件，不编译）

| 路径/组件 | 决策 | 参考内容 | 适配方向 |
|---|---|---|---|
| `agent/BaseAgent.java` | 参考 | step loop、max steps、生命周期 | 用数据库持久任务步骤与后台 Runner 事件取代单次循环 |
| `agent/ReActAgent.java`、`ToolCallAgent.java` | 参考 | think/act、显式工具结果回灌 | 用 LangChain4j Tool + ResearchTaskOrchestrator 实现 |
| `agent/YuManus.java`、agent/model/AgentState | 参考 | system/next step prompt、状态枚举 | 改科研角色和持久化状态 |
| `tools/FileOperationTool.java` | 参考 | 文件调用签名和 Hutool 操作 | 以 workspace sandbox 重写/加强路径检查 |
| `tools/WebSearchTool.java`、`WebScrapingTool.java`、`ResourceDownloadTool.java` | 适配参考 | 检索、抓取与资源获取 | 定时论文源连接器；明确 allowlist、来源和去重 |
| `TerminalOperationTool.java` | 仅作为需求提示，不可复制直接开放 | 可执行任意 cmd 的简单实现 | 改成 Java Runner，命令白名单/用户批准/预算/审计 |
| `PDFGenerationTool.java`、`TerminateTool.java` | 适配参考 | 报告产物、任务结束语义 | 报告绑定 run；结束来自状态机，不依赖 LLM 调用终止函数 |
| `rag/LoveAppVectorStoreConfig.java` | 参考 | embedding + vector store 初始化 | LangChain4j embedding/pgvector，不混入 Spring AI Bean |
| `LoveAppDocumentLoader.java`、`MyTokenTextSplitter.java`、`MyKeywordEnricher.java` | 参考/择需 | Markdown解析、分块、元数据关键词 | 处理论文/笔记；附页码/段落/来源和项目权限 metadata |
| `QueryRewriter.java`、RAG Advisor factories/context augmenter | 参考 | 查询重写、检索、上下文组装 | 用 LangChain4j retriever/query transformer 适配 |
| `PgVectorVectorStoreConfig.java`、Cloud advisor | 参考，不直接启用 | PGVector 和云知识库思路 | 一个知识后端；先确认 embedding dimensions/模型与权限过滤 |
| `advisor/*`、`chatmemory/FileBasedChatMemory.java` | 参考 | 日志、prompt 等提示扩展、文件记忆 | 保留安全脱敏的调用跟踪；任务状态仍落数据库 |
| `pom.xml`、mcp-server 配置 | 参考 | Spring AI/MCP 接入示例 | 如需 MCP 使用 LangChain4j 集成；不导入这份 POM/Bean |

## Paper2Code 外部项目

Paper2Code/PaperCoder 不是两个本地底座之一。通过 `PaperToCodeProvider` 接口适配，先审许可证、依赖、API 成本、输入格式、模型配置、输出目录和隔离方案。Agent 的 Java API 不直接暴露 shell。不可运行时用 LangChain4j 的 planning/analysis/generation 步骤作为自有实现。
