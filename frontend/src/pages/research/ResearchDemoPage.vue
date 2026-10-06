<template>
  <main class="demo-page">
    <section class="intro">
      <div>
        <p class="eyebrow">INTERACTIVE DEMO · NO BACKEND REQUIRED</p>
        <h1>从论文方法到代码与实验结果</h1>
        <p>用一个固定案例体验 research_agent 的论文导入、知识检索、人工确认、代码产物和实验指标。</p>
        <a-alert type="warning" show-icon message="公开演示使用固定样例：不调用大模型、不执行训练，也不会读取或保存你的文件。" />
      </div>
      <div class="intro-action">
        <a-button v-if="phase === 'ready'" type="primary" size="large" @click="createTask">创建示例任务</a-button>
        <a-button v-else @click="resetDemo">重新演示</a-button>
      </div>
    </section>

    <div class="demo-grid">
      <section class="context-column">
        <a-card title="科研项目" :bordered="false">
          <h3>图像分类基线复现</h3>
          <p>语言：Python · 目标：生成训练脚本样例并说明实验边界</p>
        </a-card>
        <a-card title="项目记忆" :bordered="false">
          <p>固定随机种子；验证集与训练集分开；报告 accuracy 和 loss；未经运行的结果要明确标注。</p>
          <small>本地模式保存为 .research_agent/memory.md</small>
        </a-card>
        <a-card title="文献笔记" :bordered="false">
          <a-tag color="blue">Early stopping 方法笔记</a-tag>
          <p>监控验证集 loss；若连续若干轮没有改善，停止训练并恢复最佳权重。</p>
          <small>本地模式可添加来源与正文，并按任务检索相关片段。</small>
        </a-card>
        <a-card title="论文方法 · 固定样例" :bordered="false">
          <a-tag color="purple">PDF 方法片段</a-tag>
          <p>使用独立验证集监控损失；训练脚本需要记录随机种子、每轮指标与早停条件。</p>
          <small>本地模式可上传文本型 PDF，审阅抽取片段后创建待确认的代码任务。</small>
        </a-card>
      </section>

      <section class="task-column">
        <a-card title="科研编程任务" :bordered="false">
          <p class="task-prompt">“生成一个 Python 训练脚本样例，加入早停、固定随机种子，并说明如何验证。”</p>
          <a-steps :current="step" size="small" class="steps">
            <a-step title="提交任务" /><a-step title="确认计划" /><a-step title="检索资料" /><a-step title="查看成果" />
          </a-steps>
          <div v-if="phase === 'ready'" class="hint">点击“创建示例任务”开始。</div>
          <div v-else-if="phase === 'approval'" class="approval">
            <h3>执行计划</h3>
            <ol><li>读取项目约束和相关文献笔记</li><li>生成独立的 Python 示例文件</li><li>总结运行方法与未验证假设</li></ol>
            <a-button type="primary" @click="approveTask">确认并生成样例</a-button>
          </div>
          <a-tabs v-else v-model:active-key="activeTab">
            <a-tab-pane key="events" tab="进度事件">
              <ol class="events"><li v-for="event in visibleEvents" :key="event">{{ event }}</li></ol>
              <a-spin v-if="phase === 'running'" tip="正在播放固定流程" />
            </a-tab-pane>
            <a-tab-pane key="result" tab="助手总结" :disabled="phase !== 'done'">
              <p>示例已生成：<strong>train.py</strong> 和 <strong>README.md</strong>。代码包含随机种子设置和基于验证集 loss 的早停逻辑。</p>
              <p>这是用于演示的预置代码，未运行训练，未验证任何论文结果；实际使用时需要核对数据划分、依赖版本和指标。</p>
            </a-tab-pane>
            <a-tab-pane key="files" tab="代码预览" :disabled="phase !== 'done'">
              <a-radio-group v-model:value="selectedFile" button-style="solid" class="file-switch">
                <a-radio-button value="train.py">train.py</a-radio-button><a-radio-button value="README.md">README.md</a-radio-button>
              </a-radio-group>
              <pre><code>{{ selectedFile === 'train.py' ? sampleCode : sampleReadme }}</code></pre>
            </a-tab-pane>
          </a-tabs>
        </a-card>
        <a-card v-if="phase === 'done'" title="实验助手 · 固定样例" :bordered="false" class="experiment-demo">
          <p>本地模式可人工确认运行 Python 脚本，实时查看日志和指标，并下载 SVG 曲线。</p>
          <a-button type="primary" @click="showMetrics = true">查看示例训练指标</a-button>
          <div v-if="showMetrics" class="sample-metrics">
            <strong>loss：1.0000 → 0.2500 · 观测最优 0.2500</strong>
            <svg viewBox="0 0 500 200" role="img" aria-label="固定样例 loss 曲线">
              <path d="M45 20 V165 H470" fill="none" stroke="#94a3b8" />
              <polyline points="45,30 145,80 245,116 345,140 445,158" fill="none" stroke="#2563eb" stroke-width="4" />
            </svg>
            <small>这组数值仅用于界面演示，没有实际训练，也不能作为论文结果。</small>
          </div>
        </a-card>
      </section>
    </div>
    <p class="local-link">想让 Agent 处理自己的科研项目？下载仓库并按 README 启动 Java 后端与 Vue 前端。静态演示不会连接本机 API。</p>
  </main>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'

type Phase = 'ready' | 'approval' | 'running' | 'done'
const phase = ref<Phase>('ready')
const visibleCount = ref(0)
const activeTab = ref('events')
const selectedFile = ref('train.py')
const showMetrics = ref(false)
const timers: ReturnType<typeof setTimeout>[] = []
const events = [
  '任务已排队，等待后台 Worker 领取',
  '读取项目记忆：固定随机种子、独立验证集',
  '检索到 1 条笔记：Early stopping 方法笔记',
  '生成 train.py 与 README.md',
  '任务完成：请人工检查并运行代码',
]
const visibleEvents = computed(() => events.slice(0, visibleCount.value))
const step = computed(() => phase.value === 'ready' ? 0 : phase.value === 'approval' ? 1 : phase.value === 'done' ? 4 : visibleCount.value >= 3 ? 3 : 2)

const sampleCode = `import random
import torch

def set_seed(seed: int = 42) -> None:
    random.seed(seed)
    torch.manual_seed(seed)

def should_stop(validation_losses: list[float], patience: int = 3) -> bool:
    if len(validation_losses) <= patience:
        return False
    best_before_window = min(validation_losses[:-patience])
    return all(loss >= best_before_window for loss in validation_losses[-patience:])

# 演示片段：真实项目还需实现数据加载、训练循环与权重保存。`
const sampleReadme = `# 示例训练任务

1. 固定随机种子，并将训练集与验证集分开。
2. 每轮记录 validation loss 和 accuracy。
3. 验证损失连续 3 轮不改善时停止；实际训练还应保存和恢复最佳权重。

此页面是固定演示，没有执行代码或验证论文结论。`

const clearTimers = () => { timers.forEach(clearTimeout); timers.length = 0 }
const createTask = () => { phase.value = 'approval'; activeTab.value = 'events' }
const approveTask = () => {
  phase.value = 'running'
  visibleCount.value = 0
  events.forEach((_, index) => {
    timers.push(setTimeout(() => {
      visibleCount.value = index + 1
      if (index === events.length - 1) phase.value = 'done'
    }, 450 * (index + 1)))
  })
}
const resetDemo = () => {
  clearTimers()
  phase.value = 'ready'
  visibleCount.value = 0
  activeTab.value = 'events'
  selectedFile.value = 'train.py'
  showMetrics.value = false
}
onUnmounted(clearTimers)
</script>

<style scoped>
.demo-page { max-width: 1260px; margin: 0 auto; padding: 42px 24px 64px; }
.intro { display:flex; justify-content:space-between; gap:28px; align-items:flex-start; margin-bottom:26px; }
.intro > div:first-child { max-width:800px; }
.eyebrow { color:#1677ff; font-size:12px; font-weight:700; letter-spacing:.1em; }
h1 { color:#172033; font-size:34px; margin:8px 0 12px; }
.intro p { color:#667085; line-height:1.7; }
.intro :deep(.ant-alert) { margin-top:18px; }
.intro-action { flex-shrink:0; padding-top:38px; }
.demo-grid { display:grid; grid-template-columns:360px minmax(0,1fr); gap:20px; }
.context-column { display:grid; gap:16px; align-content:start; }
.context-column p { color:#475467; line-height:1.7; }
.context-column small { color:#98a2b3; }
.task-prompt { border-left:3px solid #1677ff; background:#f5f8ff; padding:16px; line-height:1.7; }
.steps { margin:26px 0; }
.hint { color:#667085; padding:28px 0; }
.approval { background:#f8fbff; border:1px solid #d6e4ff; border-radius:8px; padding:16px; }
.approval li { margin:8px 0; }
.events { padding-left:22px; min-height:190px; }
.events li { padding:7px 0; }
.file-switch { margin:8px 0 16px; }
pre { background:#f8fafc; padding:16px; overflow:auto; max-height:440px; }
.local-link { margin-top:24px; color:#667085; }
.experiment-demo { margin-top:20px; }
.sample-metrics { display:flex; flex-direction:column; gap:12px; margin-top:18px; }
.sample-metrics svg { width:100%; max-width:500px; background:#f8fafc; }
.sample-metrics small { color:#667085; }
@media(max-width:850px) { .intro { flex-direction:column; } .intro-action { padding:0; } .demo-grid { grid-template-columns:1fr; } }
</style>
