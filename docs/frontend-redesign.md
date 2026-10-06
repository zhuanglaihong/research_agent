# 对话 + 可视化科研工作台

## 节点画布

右侧流程已替换为 SVG 连线与 Vue 节点组成的可拖动画布，无新依赖。支持布局、平移、缩放、适应、节点详情、键盘移动和 localStorage 按场景保存；右侧可展开。连线表示固定流程依赖，移动位置不改变执行顺序。下一阶段再将版本化图定义、节点配置和后端执行器贯通。

参考 [Dify Workflow Studio](https://www.dify.ai/workflows) 的可视化节点编排形态。未复制 Dify 源码，不表示嵌入了 Dify 引擎。

## 设计参考

参考代表性产品的官方说明，不声称已调查用户好评排名：

- [Cursor Agents Window](https://cursor.com/changelog/3-0)：以 Agent 任务和项目为中心，保留工作上下文；借鉴项目侧栏与任务工作区。
- [Dify Step Run](https://docs.dify.ai/en/cloud/use-dify/debug/step-run)：按节点检查执行结果；借鉴步骤状态与产物检查器。
- [Attention Is All You Need](https://arxiv.org/abs/1706.03762)：论文预览的真实来源，未作为本项目成功复现案例。

## 当前交付与边界

三栏布局：左侧项目和五场景；中间任务对话、计划审批、工具事件和运行批准检查点；右侧流程、文件、运行摘要、真实指标及说明。小屏将检查器堆叠至对话下方，手机将导航改为横向。

真实证据为 examples/ollama-feedback-case 和 examples/digits-baseline。前端附带真实指标、脚本和审阅记录；流程回放计时不表示后台训练进度。预置任意消息回复不调用模型。实际工作台尚未迁移此布局，paper2code MCP、仓库导入及自动训练闭环没有因页面预览而实现。

## 验收

前端 npm run build：类型检查与生产构建通过，既有主包体积警告。浏览器确认任务提交、计划审批、运行确认、真实指标与论文预览无实验结果。1440×900 桌面断点与 390×844 手机断点分别检查，手机文档 scrollWidth 与 clientWidth 同为 375，无页面横向溢出。截图位于 screenshots/research-demo-desktop.jpg 和 research-demo-mobile.jpg。VITE_STATIC_DEMO=true、VITE_BASE_PATH=/research_agent/ 的 Pages 模式构建也已通过。本轮未发布远程。
