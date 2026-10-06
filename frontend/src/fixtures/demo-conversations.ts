export type DemoTurn = {
  user: string
  answer: string
  reasoning: string
  action: string
  tab: string
  completed: number
  phase: string
}
// Scripted product demonstrations, edited from evidence or marked as previews.
// They are not verbatim LLM transcripts or live model responses.
export const conversations: DemoTurn[][] = [
  [
    {
      user: '帮我建立 Transformer 论文的阅读笔记，先给我一个有出处的方法概览。',
      answer:
        '这个示例以 Attention Is All You Need 为阅读对象。论文围绕注意力机制构建序列转换模型。右侧提供带论文链接的预置笔记；当前没有在线搜索，也没有生成最新论文列表。',
      reasoning:
        '先确定来源，再区分论文事实、理解笔记和待验证问题。每天收集文章需要本地服务持续运行。',
      action: '阅读方法概览',
      tab: 'files',
      completed: 2,
      phase: 'approval',
    },
    {
      user: '哪些内容应该放进我的项目知识库？',
      answer:
        '建议保存研究问题、核心方法、训练设置和评价协议，并保留出处。本地后端已有 PDF 文本导入、分块 BM25 检索和文件记忆。这里的阅读笔记是预置示例。',
      reasoning: '知识笔记关联到具体项目；未来回答应给出来源片段，没有材料时明确缺失。',
      action: '查看知识库组织方式',
      tab: 'flow',
      completed: 3,
      phase: 'approval',
    },
    {
      user: '总结这个例子实际演示了什么。',
      answer:
        '阅读笔记保留论文来源，可以按项目保存和检索。本例展示阅读对话、知识管理步骤与可下载笔记；在本地工作台设置 arXiv 主题即可订阅领域论文。',
      reasoning: '预置交互和后端已实现能力分别说明，不将回放当作一次检索。',
      action: '核对来源与实现范围',
      tab: 'evidence',
      completed: 4,
      phase: 'done',
    },
  ],
  [
    {
      user: '根据 Attention Is All You Need，为我制定一个最小代码验证计划。',
      answer:
        '先限定为 scaled dot-product attention 的最小实现：检查 Q、K、V 张量形状，验证 softmax 输出与数值。完整翻译实验还需要数据、分词、训练预算和评价协议。右侧是方法规格预览。',
      reasoning: '先收窄复现范围，避免把一个模块测试等同于整篇论文复现。',
      action: '查看论文方法计划',
      tab: 'files',
      completed: 1,
      phase: 'approval',
    },
    {
      user: '如果我确认，助手后面应该怎么执行？',
      answer:
        '在本地工作台导入 PDF，审阅方法片段和科研约束，再确认代码任务。Agent 结合项目文件与资料生成独立产物，供你检查代码和运行条件。本例右侧展示方法审阅步骤。',
      reasoning: '每个节点需要实际工具和输入输出契约；MCP 只是工具接口。',
      action: '展开论文到代码流程',
      tab: 'flow',
      completed: 3,
      phase: 'approval',
    },
    {
      user: '为什么先做一个最小方法验证？',
      answer:
        '先验证模块形状和数值，可以明确实现范围，再决定完整实验的数据与预算。本例聚焦方法规划；切换左侧 Ollama 案例，可查看真实代码生成、运行和指标记录。',
      reasoning: '当前论文案例明确标为流程预览，不能作为论文复现成果。',
      action: '查看方法验证范围',
      tab: 'results',
      completed: 4,
      phase: 'done',
    },
  ],
  [
    {
      user: '接管一个已有科研仓库，先告诉我运行前要检查什么。',
      answer:
        '先核对仓库来源、许可和 commit，再定位训练入口、依赖、数据路径与评价协议。右侧是仓库检查清单；当前没有下载具体 GitHub 仓库。',
      reasoning: '让运行方案有明确的代码版本和环境，才能复现实验。',
      action: '查看仓库接管清单',
      tab: 'files',
      completed: 1,
      phase: 'approval',
    },
    {
      user: '我不想助手直接改坏原项目，可以怎么做？',
      answer:
        '生成代码保存在独立任务产物中，方便你先审阅和下载，原项目文件保持完整。代码修改和实验运行分开确认，让每一步都有可检查的产物与记录。',
      reasoning: '把代码生成、修改应用与进程运行分成可审阅步骤。',
      action: '查看修改与审批流程',
      tab: 'flow',
      completed: 3,
      phase: 'approval',
    },
    {
      user: '检查完之后会自动跑训练吗？',
      answer:
        '目标是先展示命令、工作目录和预算，经确认后再运行。本地已有人工批准的短时 Python Runner；这个仓库预览没有安装依赖或执行训练，因此没有运行日志。',
      reasoning: '只有真实 Runner 状态和日志才能作为运行证据。',
      action: '查看运行记录',
      tab: 'logs',
      completed: 4,
      phase: 'done',
    },
  ],
  [
    {
      user: '生成一个 Python 标准库梯度下降实验，先让我审阅代码。',
      answer:
        '本机 qwen3:8b 已生成 train.py：最小化 (weight − 2)²，初值 0，学习率 0.1，更新 10 次。脚本保存 11 个指标点。你可以在右侧查看或下载真实脚本。',
      reasoning:
        '这是一个无需数据下载的小实验，用于验证生成 → 运行 → 指标读取链路。页面对话为根据实测记录整理的固定脚本。',
      action: '查看生成的代码',
      tab: 'files',
      completed: 2,
      phase: 'runApproval',
    },
    {
      user: '我已审阅脚本，批准运行。展示这次运行记录。',
      answer:
        '真实本机 Run 1 已由人工批准的 Java Runner 执行，退出码为 0。右侧显示运行摘要。运行记录保留了状态、退出码和数值文件，可与脚本对应核对。',
      reasoning: '运行与模型生成分开审批。记录来自已有本机实验，网页不访问你的电脑。',
      action: '批准运行并查看记录',
      tab: 'logs',
      completed: 3,
      phase: 'approval',
    },
    {
      user: '读取实验指标，看看 loss 有没有下降。',
      answer:
        '11 个真实观测点中，loss 从 4.0 降至 0.04611686018427385。右侧曲线直接读取已保存的 JSON；你可以打开原始数据核对。该数值不代表真实数据集的泛化性能。',
      reasoning: '先读数值再解释结果，不从曲线外推论文复现或模型效果。',
      action: '读取指标与曲线',
      tab: 'results',
      completed: 4,
      phase: 'approval',
    },
    {
      user: '基于结果给我下一步建议，但先不要执行。',
      answer:
        '可以比较学习率 0.05 与 0.1，但当前 train.py 没有命令行参数解析，需要先修改常量或补充参数支持。模型原报告中的部分命令和解释存在错误，右侧保存了人工审阅记录。后续实验仍是草稿。',
      reasoning:
        'Agent 能通过 inspectExperiment 观察实验，但工具成功不保证所有解释正确；建议仍需你确认。',
      action: '审阅分析与下一步',
      tab: 'files',
      completed: 5,
      phase: 'done',
    },
  ],
  [
    {
      user: '分析 Run 1，区分已经发生的事实和下一步建议。',
      answer:
        '事实：Run 1 退出码 0；11 个观测点，loss 为 4.0 → 0.04611686。建议：比较其他学习率。建议没有自动执行。这是左侧 Ollama 案例的同一次运行。',
      reasoning: '复用同一份指标，避免把不同视图当作多次独立实验。',
      action: '查看结果依据',
      tab: 'results',
      completed: 2,
      phase: 'approval',
    },
    {
      user: '如果直接执行报告里的 --lr 0.05，参数会改变吗？',
      answer:
        '不会。脚本没有参数解析，报告里的命令需要纠正。正确下一步应先修改代码常量或实现参数解析，再审阅修改、批准新的运行。右侧分析文件是预置说明，不是新生成报告。',
      reasoning: '对模型结论核对脚本和原始运行证据，再决定是否修改和重跑。',
      action: '查看分析与代码审阅',
      tab: 'files',
      completed: 3,
      phase: 'approval',
    },
    {
      user: '这个助手目前能帮我盯多久的实验？',
      answer:
        '本地 Runner 适合短时验证：每次运行预算 5 分钟，支持日志、取消和指标读取。你可以先用小实验验证代码，再根据数据和计算预算制定下一项任务。',
      reasoning: '持续实验管理需要独立运行状态与心跳，不能通过一直调用模型实现。',
      action: '查看实验运行设置',
      tab: 'evidence',
      completed: 4,
      phase: 'done',
    },
  ],
]
