# research_agent 实施约定

## 项目目标

做一个 Java 主后端的个人本地科研编程 Agent。用户下载后直接启动，不注册登录；用自然语言创建或接管科研项目，委托论文方法实现、代码修改、实验运行/监控、结果分析与科研绘图。系统面向多语言科研仓库；Python 仅作为第一个运行样例。

## 必读顺序

1. `docs/handoff.md`
2. `docs/execution-plan.md`
3. `docs/reuse-map.md`
4. `docs/implementation-status.md`

## 技术决策

- 主后端：Java 21 + Spring Boot。
- 本地默认：单用户，无登录；嵌入式 H2 文件数据库保存项目、任务、事件和笔记。当前主链不依赖 MySQL/Redis。无认证 API 只供本机访问，不允许直接公网暴露。
- AI 集成：采用 LangChain4j；不添加 Spring AI 依赖。上游 `yu-ai-agent` 位于仓库外，仅作思路参考。
- LangGraph4j 是可选评估技术，不因 pom 中存在就视作主 Agent 已使用；先实现简单、持久化的任务步骤。
- 前端沿用 Vue 3/TypeScript 基线，改为科研项目、任务状态、代码差异、日志和实验结果；不恢复登录/注册/管理员导航。
- 科研命令由 Java Runner 调用。命令和参数分开保存，不把语言/命令硬编码为 Python；第一轮 Python/PyTorch，第二个轻量 Go 验证。
- 数据库中任务状态是事实来源。SSE 断线不能结束实验；长实验不能绑定 HTTP 请求线程。

## 复用边界

- 所有工作只在 `D:/project/Agent/research_agent`。两个源项目只读。
- 逐文件来源见 `docs/reuse-manifest.csv`，组件决策见 `docs/reuse-map.md`。
- 新科研功能放在 `org.researchagent.research`。
- 不让 Vue 项目生成、网站部署、网页截图进入科研主流程。
- 上游源码没有可见 LICENSE。不要移除作者署名，不添加声称覆盖上游代码的开源许可证；公开发布前先解决授权。

## 运行边界

- 只操作用户显式绑定的 workspace。规范化路径并验证根目录包含关系；拒绝路径穿越。
- 执行前展示命令、工作目录和预算；长任务有取消、超时、输出限制和幂等 run ID。
- 不从 LLM 原始文本拼 shell 命令。用 executable + arguments 调用，脚本解释器/容器作为明确配置。
- 保持用户数据和模型密钥在示例、日志、前端 bundle 和 Git 中之外。
- 未运行/未验证的功能标为“规划中”或“待验证”，不得写成已实现。

## 协作交付

- 每轮完成后更新 `docs/implementation-status.md`，记录已改文件、命令/验证结果、未完成项与阻塞。
- 新增 Maven 依赖需说明用途、版本来源与本地验证情况。
- 同步更新 README 快速启动和接口/数据契约。
- 源码复制授权仍待核对；此项阻止对外发布，不阻止当前本地开发。
