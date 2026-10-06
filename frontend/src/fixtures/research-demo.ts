import train from './feedback-train.txt?raw'
import analysis from './feedback-analysis.md?raw'
export const scenarios = [
  {
    title: '追踪领域论文',
    short: '知识管理',
    icon: '01',
    project: '论文阅读空间',
    verified: false,
    prompt: '整理 Transformer 相关论文的方法与创新点，为我的项目保存带来源的阅读笔记。',
    description: '先检索已有文献，再整理方法与引用。阅读笔记按项目组织，并保留论文来源。',
    steps: ['检索项目文献', '读取来源片段', '整理阅读笔记', '核对引用'],
    tools: ['searchResearchNotes', 'listCollectedPapers', 'rememberProjectFinding', '引用核对'],
    files: [
      {
        name: 'reading-notes.md',
        content:
          '# Transformer 阅读笔记\n\n参考：Attention Is All You Need\nhttps://arxiv.org/abs/1706.03762\n\n研究问题：使用注意力机制构建序列转换模型。\n后续阅读：模型结构、训练设置与评测协议。',
      },
    ],
    boundary:
      '通过 arXiv 订阅、PDF 文本导入、项目笔记与 BM25 检索组织科研资料，保留来源与项目记忆。',
  },
  {
    title: '从论文创建实验',
    short: '论文到代码',
    icon: '02',
    project: 'Transformer 方法验证',
    verified: false,
    prompt:
      '根据 Attention Is All You Need，规划一个最小注意力模块实现，列出数据与验证条件后让我确认。',
    description:
      '明确论文方法、实现范围与验收条件，再生成代码。此案例展示带来源的方法规划与人工审阅。',
    steps: ['解析方法规格', '限定复现范围', '审阅代码计划', '确认验证条件'],
    tools: ['PDF 方法审阅', '方法范围审阅', '代码任务确认', '验证条件审阅'],
    files: [
      {
        name: 'method-spec.md',
        content:
          '# 方法规格（预览）\n\n论文：Attention Is All You Need，arXiv:1706.03762\n最小目标：scaled dot-product attention；检查张量形状与数值。\n\n完整翻译实验需要数据、分词、训练预算和原论文评测协议。\n本例展示方法规格，不包含 Transformer 训练结果。',
      },
    ],
    boundary: '导入论文方法片段，审阅范围与交付要求，再创建带来源的代码任务。',
  },
  {
    title: '接管已有科研仓库',
    short: '仓库与代码',
    icon: '03',
    project: '已有项目工作区',
    verified: false,
    prompt: '检查一个科研 GitHub 仓库的入口、依赖和数据要求，先给出运行计划，不直接执行安装。',
    description:
      '检查代码、环境与数据，并把修改整理成可审批的差异。这是规划交互，没有实际下载仓库。',
    steps: ['核对仓库来源', '检查项目入口', '整理依赖与数据', '审批运行方案'],
    tools: ['仓库来源核对', 'listProjectFiles', 'readProjectFile', '修改方案审阅'],
    files: [
      {
        name: 'repository-checklist.md',
        content:
          '# 仓库接管清单（预览）\n\n- 来源、许可与 commit\n- 启动入口和依赖锁定\n- 数据下载与路径配置\n- 命令、工作目录、时间预算\n- 修改 diff 与用户审批\n\n此网页没有执行 clone、安装依赖或运行仓库。',
      },
    ],
    boundary: '读取绑定工作区的项目文件，生成独立代码产物；运行前审阅环境、命令与数据条件。',
  },
  {
    title: '生成并观察一个实验',
    short: '实验监控',
    icon: '04',
    project: '二次优化 · 实测闭环',
    verified: true,
    prompt:
      '生成一个 Python 标准库梯度下降实验，经我确认后运行，再读取指标，分析结果并起草下一步任务。',
    description:
      '回放本机 qwen3:8b 的真实闭环记录：生成脚本 → 人工批准 → Java Runner → inspectExperiment → 分析草稿。',
    steps: ['检索方法笔记', '生成训练脚本', '确认实验运行', '读取真实指标', '整理后续任务'],
    tools: [
      'searchResearchNotes',
      'writeArtifact',
      'Java Runner · 人工批准',
      'inspectExperiment',
      '分析与后续任务草稿',
    ],
    files: [
      { name: 'train.py', content: train },
      { name: 'analysis.md', content: analysis },
      {
        name: 'review.md',
        content:
          '# 实测审阅\n\nRun 1：退出码 0。\nloss：4.0 → 0.04611686018427385。\n模型：本机 Ollama qwen3:8b。\n\n模型报告中的参数、文件清单和部分解释存在错误，需人工核对。\n后续学习率实验仅为草稿，没有自动执行。\n这是二次函数优化验证，不是论文复现或模型泛化评测。',
      },
    ],
    boundary: '回放本机 Ollama 生成、人工批准 Java Runner 执行和指标读取的实测记录。',
  },
  {
    title: '分析结果与下一步',
    short: '结果与报告',
    icon: '05',
    project: '实验分析空间',
    verified: true,
    prompt: '读取 Run 1 的日志与指标，解释观测结果并起草一个需要我确认的后续实验。',
    description: '用同一份真实指标说明结果，区分已观测事实与模型建议，不把建议当成已完成实验。',
    steps: ['读取实验指标', '核对数值趋势', '审阅分析结论', '起草后续任务'],
    tools: ['inspectExperiment', '基础数值统计', '人工审阅', '待确认任务草稿'],
    files: [
      {
        name: 'analysis.md',
        content:
          '# Run 1 实验分析\n\n11 个观测点，loss 从 4.0 降至 0.04611686。\n该实验验证最小生成、运行、指标读取链路。\n不代表真实数据集性能或论文复现成功。\n\n建议比较学习率 0.05；仅起草任务，未运行。',
      },
    ],
    boundary: '查看实际指标、基础统计和 SVG 曲线，由 Agent 读取结果并草拟需要确认的后续任务。',
  },
]
