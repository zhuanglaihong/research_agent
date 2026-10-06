<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { scenarios } from '@/fixtures/research-demo'
import { conversations } from '@/fixtures/demo-conversations'
import report from '@/fixtures/feedback-report.json'
import stdout from '@/fixtures/feedback-stdout.txt?raw'
import RealExperimentCase from '@/components/RealExperimentCase.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ResearchWorkflowCanvas from '@/components/ResearchWorkflowCanvas.vue'
import ResearchTurnActivity from '@/components/ResearchTurnActivity.vue'
const selected = ref(3)
const scene = computed(() => scenarios[selected.value]!)
const story = computed(() => conversations[selected.value]!)
const turns = ref(0)
const playing = ref(false)
const automatic = ref(false)
const visibleTurns = computed(() => story.value.slice(0, turns.value))
const nextTurn = computed(() => story.value[turns.value])
const phase = computed(() =>
  playing.value ? 'running' : (visibleTurns.value[turns.value - 1]?.phase ?? 'ready'),
)
const completed = computed(() => visibleTurns.value[turns.value - 1]?.completed ?? 0)
const tab = ref('flow')
const wideWorkspace = ref(false)
const fileIndex = ref(0)
const modal = ref('')
const transcript = ref<HTMLElement>()
let timer: ReturnType<typeof setTimeout> | undefined
const file = computed(() => scene.value.files[fileIndex.value] ?? scene.value.files[0]!)
const loss = report.series.find((s) => s.name === 'loss')!
const points = loss.points
  .map((p) => String(40 + p.step * 25) + ',' + String(174 - p.value * 32))
  .join(' ')
const base = import.meta.env.BASE_URL
const caseNames = [
  'Transformer 阅读笔记',
  'Attention 方法验证计划',
  '已有科研仓库检查',
  'Ollama 生成并运行实验',
  '实验结果与下一步',
]
const labels: Record<string, string> = {
  ready: '选择案例开始',
  approval: '等待下一条演示',
  running: '正在展示回复',
  runApproval: '待演示运行审批',
  done: '案例回放完成',
}
function stop() {
  if (timer) clearTimeout(timer)
  playing.value = false
  automatic.value = false
}
function inspectActivity(target: string) {
  const index = scene.value.files.findIndex((entry) => entry.name === target)
  if (index >= 0) {
    fileIndex.value = index
    tab.value = 'files'
  } else tab.value = target
}
async function scroll() {
  await nextTick()
  const container = transcript.value
  const messages = container?.querySelectorAll<HTMLElement>('.story-turn')
  const newest = messages?.[messages.length - 1]
  if (container && newest) {
    const top =
      newest.getBoundingClientRect().top -
      container.getBoundingClientRect().top +
      container.scrollTop
    container.scrollTo({ top: Math.max(0, top - 12), behavior: 'smooth' })
  }
}
function playNext() {
  if (playing.value || !nextTurn.value) return
  if (timer) clearTimeout(timer)
  playing.value = true
  timer = setTimeout(() => {
    const turn = nextTurn.value!
    turns.value++
    tab.value = turn.tab
    if (selected.value === 3 && turns.value === 4)
      fileIndex.value = scene.value.files.findIndex((entry) => entry.name === 'review.md')
    playing.value = false
    scroll()
    if (automatic.value && nextTurn.value && turn.phase !== 'runApproval')
      timer = setTimeout(playNext, 2400)
    else automatic.value = false
  }, 450)
}
function choose(index: number) {
  stop()
  selected.value = index
  turns.value = 0
  tab.value = 'flow'
  fileIndex.value = 0
  playNext()
}
function autoplay() {
  if (automatic.value) {
    stop()
    return
  }
  automatic.value = true
  if (!playing.value) playNext()
}
function download() {
  const url = URL.createObjectURL(
    new Blob([file.value.content], { type: 'text/plain;charset=utf-8' }),
  )
  const a = document.createElement('a')
  a.href = url
  a.download = file.value.name
  a.click()
  URL.revokeObjectURL(url)
}
onMounted(() => playNext())
onUnmounted(stop)
</script>

<template>
  <div class="research-shell">
    <aside class="rail">
      <a class="brand" href="#/demo"
        ><span class="brand-mark">r_</span
        ><span>research_agent<small>RESEARCH WORKSPACE</small></span></a
      >
      <button class="new-task" @click="choose(selected)">↻ 重新开始当前案例</button>
      <div class="rail-label">工作空间</div>
      <div class="project-name"><span class="project-dot"></span>{{ scene.project }}</div>
      <div class="rail-label scenario-label">选择一个案例</div>
      <nav class="scene-nav" aria-label="科研场景">
        <button
          v-for="(item, i) in scenarios"
          :key="item.short"
          :class="{ active: selected === i }"
          @click="choose(i)"
        >
          <span>{{ item.icon }}</span
          >{{ caseNames[i] }}<b v-if="selected === i">↗</b>
        </button>
      </nav>
      <div class="rail-evidence">
        <div class="rail-label">可核验的案例</div>
        <button @click="choose(3)">● Ollama 生成与运行 <span>实测</span></button>
        <button @click="modal = 'cases'">◈ Digits 三种子基线 <span>实测</span></button>
      </div>
      <div class="rail-bottom">
        <button @click="modal = 'models'">⚙ 模型与本地配置</button
        ><a href="https://github.com/zhuanglaihong/research_agent" target="_blank" rel="noopener"
          >GitHub ↗</a
        ><small>个人科研 · 无需登录</small>
      </div>
    </aside>
    <main class="workspace">
      <header class="workspace-header">
        <div><span class="breadcrumb">案例回放 / </span>{{ scene.short }}</div>
        <div class="header-actions">
          <span class="preview-label">固定会话回放</span
          ><button class="quiet" @click="choose(selected)">重置</button>
        </div>
      </header>
      <div class="demo-notice"><span>ⓘ</span> 会话回放：不调用实时模型，不在浏览器执行代码。</div>
      <div class="workspace-grid" :class="{ 'wide-workspace': wideWorkspace }">
        <section class="conversation" aria-label="科研对话">
          <div class="conversation-top">
            <span class="status-dot" :class="{ running: phase === 'running' }"></span
            >{{ labels[phase]
            }}<span class="case-badge">{{ scene.verified ? '本机实测记录' : '教学案例' }}</span>
          </div>
          <div ref="transcript" class="transcript" aria-live="polite">
            <div class="story-heading">
              <div class="eyebrow">RESEARCH IN ACTION</div>
              <h1>{{ caseNames[selected] }}</h1>
              <p>
                {{
                  scene.verified
                    ? '二次优化实验 · 脚本与指标来自本机运行记录'
                    : '方法与项目管理教学示例'
                }}
              </p>
            </div>
            <article v-for="(turn, i) in visibleTurns" :key="i" class="story-turn">
              <div class="user-message">
                <small>你 · {{ i + 1 }}</small>
                <p>{{ turn.user }}</p>
              </div>
              <div class="assistant-message">
                <div class="assistant-label">
                  <span>r_</span> research_agent <small>科研助手</small>
                </div>
                <ResearchTurnActivity
                  :scenario="selected"
                  :turn-index="i"
                  :summary="turn.reasoning"
                  @inspect="inspectActivity"
                />
                <MarkdownRenderer class="answer-markdown" :content="turn.answer" />
                <button class="inspect-turn" @click="tab = turn.tab">{{ turn.action }} ↗</button>
              </div>
            </article>
            <div v-if="playing" class="replay-loading"><span></span>正在展示回复…</div>
            <div v-if="phase === 'done'" class="story-complete">
              ✓ 案例已展示完毕。可继续查看右侧产物，或从左侧选择另一个案例。
            </div>
          </div>
          <div class="composer-area">
            <div class="story-controls">
              <span>会话进度 {{ turns }}/{{ story.length }}</span
              ><button
                v-if="nextTurn"
                class="quiet"
                :disabled="playing && !automatic"
                @click="autoplay"
              >
                {{ automatic ? '暂停演示' : '自动演示' }}</button
              ><button class="quiet" @click="choose(selected)">重新开始</button>
            </div>
            <div class="composer replay-composer">
              <div class="next-label">
                {{ nextTurn ? '下一步' : '案例回放已完成' }}
              </div>
              <p>
                {{
                  nextTurn?.user ??
                  '选择左侧另一个案例，继续探索科研助手。真实任务请启动本地工作台。'
                }}
              </p>
              <div>
                <span>无需密钥 · 不执行新实验</span
                ><button class="send-button" :disabled="playing || !nextTurn" @click="playNext">
                  {{ playing ? '展示中…' : nextTurn ? nextTurn.action + ' →' : '已完成 ✓' }}
                </button>
              </div>
            </div>
            <small class="composer-footnote">选择左侧案例，按步骤查看代码、流程与实验结果。</small>
          </div>
        </section>
        <aside class="inspector" aria-label="任务可视化管理">
          <div class="inspector-heading">
            <span>任务工作区</span
            ><button
              class="quiet"
              :aria-pressed="wideWorkspace"
              @click="wideWorkspace = !wideWorkspace"
            >
              {{ wideWorkspace ? '收起画布' : '展开画布' }}</button
            ><small>{{ scene.verified ? 'VERIFIED RECORD' : 'GUIDED EXAMPLE' }}</small>
          </div>
          <nav class="inspector-tabs">
            <button
              v-for="item in [
                { id: 'flow', name: '流程' },
                { id: 'files', name: '产物' },
                { id: 'logs', name: '日志' },
                { id: 'results', name: '结果' },
                { id: 'evidence', name: '说明' },
              ]"
              :key="item.id"
              :class="{ active: tab === item.id }"
              @click="tab = item.id"
            >
              {{ item.name }}
            </button>
          </nav>
          <div class="inspector-body">
            <template v-if="tab === 'flow'"
              ><div class="eyebrow">WORKFLOW</div>
              <h2>{{ scene.title }}</h2>
              <p class="muted">每一步都可追踪，关键执行由你确认。</p>
              <div class="progress-heading">
                <span>回放进度</span><b>{{ completed }}/{{ scene.steps.length }}</b>
              </div>
              <div class="progress-track">
                <span :style="{ width: (completed / scene.steps.length) * 100 + '%' }"></span>
              </div>
              <ResearchWorkflowCanvas
                :scenario-id="String(selected)"
                :steps="scene.steps"
                :tools="scene.tools"
                :completed="completed"
                :phase="phase"
                @inspect="tab = $event"
              />
              <div class="note-box">
                进度是演示步骤计数；真实后端通过任务事件和运行状态展示进度。
              </div>
            </template>
            <template v-if="tab === 'files'"
              ><div class="eyebrow">ARTIFACTS</div>
              <h2>可审阅的产物</h2>
              <p class="muted">
                {{ scene.verified ? '实验脚本、分析文档与审阅记录。' : '方法规格与项目检查文档。' }}
              </p>
              <div class="file-list">
                <button
                  v-for="(item, i) in scene.files"
                  :key="item.name"
                  :class="{ active: fileIndex === i }"
                  @click="fileIndex = i"
                >
                  ▤ {{ item.name }}
                </button>
              </div>
              <div class="file-preview">
                <div>{{ file.name }} <button @click="download">下载 ↓</button></div>
                <pre>{{ file.content }}</pre>
              </div></template
            >
            <template v-if="tab === 'logs'"
              ><div class="eyebrow">RUN RECORD</div>
              <h2>运行记录</h2>
              <template v-if="scene.verified"
                ><div class="run-summary">
                  <span class="success">SUCCEEDED</span><span>Run 1 · exit 0</span>
                </div>
                <pre class="terminal">python train.py
{{ stdout }}</pre>
                <p class="muted">Ollama / qwen3:8b · Java ProcessBuilder · 11 个指标观测点</p></template
              >
              <div v-else class="empty-state">
                这个案例展示运行前的项目检查。选择左侧 Ollama 案例，可查看实际运行摘要与指标。
              </div></template
            >
            <template v-if="tab === 'results'"
              ><div class="eyebrow">RESULTS & EVIDENCE</div>
              <h2>{{ scene.verified ? '实验指标与原始记录' : '方法与运行计划' }}</h2>
              <template v-if="scene.verified"
                ><div class="metric-grid">
                  <div><small>初始 loss</small><strong>4.0000</strong></div>
                  <div><small>最终 loss</small><strong>0.046117</strong></div>
                </div>
                <div class="chart-card">
                  <div>二次函数优化 <span>11 个真实点</span></div>
                  <svg viewBox="0 0 320 205" role="img" aria-label="真实 loss 从 4 降至 0.046">
                    <path d="M40 40V174H290" fill="none" stroke="#dadfd9" />
                    <path d="M40 110H290" stroke="#eef0ed" />
                    <polyline :points="points" fill="none" stroke="#5279ee" stroke-width="3" />
                    <circle
                      v-for="p in loss.points"
                      :key="p.step"
                      :cx="40 + p.step * 25"
                      :cy="174 - p.value * 32"
                      r="3"
                      fill="#5279ee"
                    />
                    <text x="8" y="48">4.0</text>
                    <text x="14" y="177">0</text>
                    <text x="40" y="198">step 0</text>
                    <text x="249" y="198">step 10</text>
                  </svg>
                </div>
                <div class="note-box">
                  二次函数优化实验的实际观测。分析时请结合脚本与原始指标核对。
                </div>
                <div class="evidence-links">
                  <a :href="base + 'cases/feedback/metrics-report.json'" target="_blank"
                    >原始指标 JSON ↗</a
                  ><a :href="base + 'cases/feedback/review.md'" target="_blank">人工审阅记录 ↗</a>
                </div></template
              >
              <div v-else class="empty-state">
                这个案例聚焦方法与运行计划。切换“产物”查看文档，或选择 Ollama
                实验案例查看真实训练曲线。
              </div></template
            >
            <template v-if="tab === 'evidence'"
              ><div class="eyebrow">PROJECT GUIDE</div>
              <h2>使用与案例说明</h2>
              <p>{{ scene.boundary }}</p>
              <div class="note-box">阅读方法文档、审阅代码、确认运行，再结合原始指标核对结论。</div>
              <a
                v-if="selected < 2"
                class="paper-link"
                href="https://arxiv.org/abs/1706.03762"
                target="_blank"
                rel="noopener"
                >参考论文：Attention Is All You Need ↗</a
              >
              <p class="muted">
                技术底座：Java 21 · Spring Boot · LangChain4j · Vue 3 · H2 · BM25 · SSE · 本机只读
                MCP。
              </p>
              <button class="quiet" @click="modal = 'models'">
                查看 API / Ollama 配置
              </button></template
            >
          </div>
          <div class="inspector-footer">任务 · 代码 · 运行 · 证据，在同一工作区</div>
        </aside>
      </div>
    </main>
    <div v-if="modal" class="modal-backdrop" @click.self="modal = ''">
      <section class="demo-modal" role="dialog" aria-modal="true" aria-label="案例与配置">
        <button class="modal-close" aria-label="关闭" @click="modal = ''">×</button
        ><RealExperimentCase v-if="modal === 'cases'" /><template v-else
          ><div class="eyebrow">LOCAL FIRST</div>
          <h2>模型由你选择，任务在本地运行。</h2>
          <p>先按 README 启动本地后端和前端。选择 live 模式后，编辑根目录 .env 并重启后端。</p>
          <h3>Ollama · 无 API Key</h3>
          <pre>
RESEARCH_AI_MODE=live
LLM_PROVIDER=ollama
LLM_BASE_URL=http://localhost:11434/v1
LLM_MODEL=qwen3:8b</pre
          >
          <p>先运行 ollama list 查看模型，使用已下载且支持工具调用的模型名称。</p>
          <h3>OpenAI 兼容 API</h3>
          <pre>
RESEARCH_AI_MODE=live
LLM_PROVIDER=openai-compatible
LLM_BASE_URL=你的兼容接口地址
LLM_MODEL=支持工具调用的模型
LLM_API_KEY=仅保存在本地</pre
          >
          <p>该配置说明不会连接模型。服务器和容器需配置能访问的 Ollama 地址。</p>
          <a
            href="https://github.com/zhuanglaihong/research_agent#readme"
            target="_blank"
            rel="noopener"
            >打开 README 配置教程 ↗</a
          ></template
        >
      </section>
    </div>
  </div>
</template>

<style scoped>
.answer-markdown {
  font-size: 14px;
  line-height: 1.95;
  color: #526783;
  margin: 18px 0;
}
.answer-markdown :deep(h2) {
  font-size: 19px;
  line-height: 1.6;
  font-weight: 650;
  color: #273f67;
  margin: 22px 0 12px;
}
.answer-markdown :deep(h3) {
  font-size: 15px;
  color: #38547e;
  margin: 22px 0 10px;
  font-weight: 600;
}
.answer-markdown :deep(p) {
  margin: 12px 0;
  line-height: 1.95;
}
.answer-markdown :deep(ul),
.answer-markdown :deep(ol) {
  padding-left: 23px;
  margin: 14px 0;
}
.answer-markdown :deep(li) {
  padding-left: 3px;
  margin: 8px 0;
}
.answer-markdown :deep(strong) {
  color: #344e78;
  font-weight: 600;
}
.answer-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 18px 0;
  font-size: 12px;
}
.answer-markdown :deep(th),
.answer-markdown :deep(td) {
  text-align: left;
  padding: 10px 12px;
  border: 1px solid #e0e8f4;
  vertical-align: top;
}
.answer-markdown :deep(th) {
  background: #eff4fc;
  font-weight: 600;
  color: #5b759e;
}
.answer-markdown :deep(blockquote) {
  border-left: 3px solid #7e9cec;
  background: #f1f5fd;
  padding: 3px 14px;
  margin: 16px 0;
  color: #617fae;
}
.answer-markdown :deep(a) {
  color: #5279dd;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.answer-markdown :deep(pre) {
  font-size: 12px;
  line-height: 1.8;
  border: 1px solid #e1e9f5;
}
</style>

<style scoped>
.research-shell {
  --ink: #26352f;
  --muted: #78837c;
  --line: #e4e8e2;
  --accent: #bb6449;
  display: grid;
  grid-template-columns: 228px minmax(0, 1fr);
  min-height: 100dvh;
  background: #fcfdfb;
  color: var(--ink);
  font-size: 14px;
  font-family: Inter, 'Segoe UI', 'Microsoft YaHei', sans-serif;
}
button,
a,
textarea {
  font: inherit;
}
button {
  cursor: pointer;
}
button:disabled {
  cursor: default;
  opacity: 0.45;
}
a {
  color: inherit;
  text-decoration: none;
}
button:focus-visible,
a:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}
button {
  transition: background 0.15s;
}
button:hover {
  filter: brightness(0.97);
}
.rail {
  padding: 27px 17px;
  background: #f3f5f0;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.5px;
}
.brand-mark {
  display: grid;
  place-items: center;
  background: var(--ink);
  color: white;
  border-radius: 12px;
  width: 39px;
  height: 39px;
  font-size: 23px;
}
.brand small {
  display: block;
  font-size: 8px;
  letter-spacing: 1.2px;
  color: var(--muted);
  margin-top: 5px;
}
.new-task {
  background: #fff;
  border: 1px solid #d6ddd4;
  border-radius: 9px;
  padding: 12px;
  margin: 20px 0 9px;
  text-align: left;
  font-weight: 600;
}
.rail-label {
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 1px;
  text-transform: uppercase;
  margin: 6px 8px;
}
.project-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 11px 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #f9faf7;
}
.project-dot {
  width: 7px;
  height: 7px;
  background: #59826d;
  border-radius: 50%;
}
.scenario-label {
  margin-top: 20px;
}
.scene-nav {
  display: grid;
  gap: 5px;
}
.scene-nav button {
  border: 0;
  background: transparent;
  padding: 13px 11px;
  text-align: left;
  border-radius: 8px;
  display: flex;
  gap: 13px;
  font-size: 13px;
  align-items: center;
  color: #68736c;
}
.scene-nav button > span {
  font-family: monospace;
  font-size: 11px;
  opacity: 0.6;
}
.scene-nav button.active {
  background: #e8eee4;
  color: #304c3b;
  font-weight: 650;
}
.scene-nav b {
  margin-left: auto;
  font-weight: 400;
}
.rail-evidence {
  margin-top: 28px;
}
.rail-evidence button {
  border: 0;
  background: none;
  display: block;
  width: 100%;
  padding: 10px 8px;
  text-align: left;
  font-size: 11px;
}
.rail-evidence button span {
  float: right;
  color: #66816f;
  font-size: 10px;
}
.rail-bottom {
  margin-top: auto;
  padding-top: 40px;
  display: grid;
  gap: 16px;
  font-size: 12px;
  color: #6d7870;
}
.rail-bottom button {
  background: none;
  border: 0;
  text-align: left;
  padding: 0;
  color: inherit;
}
.rail-bottom small {
  font-size: 10px;
  color: #939b94;
}
.workspace {
  min-width: 0;
}
.workspace-header {
  height: 67px;
  padding: 0 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.breadcrumb {
  color: #939b94;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}
.preview-label {
  padding: 5px 9px;
  border-radius: 5px;
  background: #f1ede3;
  font-size: 11px;
  color: #8b7853;
}
.quiet {
  background: white;
  border: 1px solid var(--line);
  border-radius: 7px;
  padding: 7px 11px;
  font-size: 12px;
}
.demo-notice {
  min-height: 38px;
  padding: 10px 25px;
  font-size: 11px;
  color: #7d775e;
  background: #fbf8ef;
  border-bottom: 1px solid #eee8db;
  display: flex;
  align-items: center;
  gap: 8px;
}
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) 355px;
  height: calc(100dvh - 105px);
  min-height: 680px;
}
.conversation {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.conversation-top {
  padding: 20px 30px;
  font-size: 11px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-dot {
  height: 6px;
  width: 6px;
  border-radius: 50%;
  background: #95a68c;
}
.status-dot.running {
  background: var(--accent);
}
.case-badge {
  margin-left: auto;
  font-size: 10px;
  padding: 4px 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
}
.transcript {
  flex: 1;
  overflow: auto;
  padding: 0 34px 25px;
  scrollbar-width: thin;
}
.welcome {
  max-width: 480px;
  margin: 4vh auto 0;
}
.welcome-symbol {
  color: var(--accent);
  font-size: 35px;
  font-weight: 650;
  margin-bottom: 25px;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 1.2px;
  color: #8b958d;
  margin-bottom: 12px;
}
.welcome h1 {
  font-size: 40px;
  line-height: 1.4;
  letter-spacing: -1.6px;
  font-weight: 600;
  margin: 0 0 15px;
}
.welcome > p {
  font-size: 14px;
  line-height: 1.9;
  color: var(--muted);
  max-width: 390px;
}
.prompt-card {
  display: block;
  width: 100%;
  background: #fff;
  border: 1px solid #e1e6de;
  text-align: left;
  border-radius: 12px;
  padding: 22px;
  margin-top: 32px;
  box-shadow: 0 3px 12px #26352f03;
}
.prompt-card > span {
  font-weight: 600;
  font-size: 13px;
}
.prompt-card p {
  font-size: 12px;
  color: #7c877e;
  line-height: 1.8;
  margin: 11px 0 16px;
}
.prompt-card b {
  font-size: 11px;
  font-weight: 500;
  color: var(--accent);
}
.welcome-hint {
  font-size: 10px;
  color: #a0a79e;
  margin-top: 18px;
}
.composer-area {
  padding: 16px 30px 18px;
  background: linear-gradient(transparent, #fcfdfb 15%);
}
.composer {
  border: 1px solid #dce1d8;
  border-radius: 12px;
  background: white;
  padding: 13px 14px;
  box-shadow: 0 4px 20px #24342904;
}
.composer textarea {
  resize: none;
  border: 0;
  outline: 0;
  width: 100%;
  background: transparent;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink);
}
.composer textarea::placeholder {
  color: #a1a99f;
}
.composer > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
}
.composer > div > span {
  font-size: 10px;
  color: #8f988d;
}
.composer small {
  font-size: 9px;
}
.send-button {
  background: var(--ink);
  color: #fff;
  border: 0;
  border-radius: 7px;
  padding: 8px 13px;
  font-size: 11px;
}
.composer-footnote {
  display: block;
  text-align: center;
  font-size: 9px;
  color: #a1a99f;
  margin-top: 10px;
}
.inspector {
  min-width: 0;
  border-left: 1px solid var(--line);
  background: #f9faf7;
  display: flex;
  flex-direction: column;
}
.inspector-heading {
  padding: 24px 23px 17px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
}
.inspector-heading small {
  font-size: 8px;
  letter-spacing: 0.5px;
  color: #96a08f;
  font-weight: 400;
}
.inspector-tabs {
  display: flex;
  padding: 0 15px;
  border-bottom: 1px solid var(--line);
}
.inspector-tabs button {
  flex: 1;
  background: none;
  border: 0;
  padding: 12px 0;
  font-size: 12px;
  color: #8b9588;
  border-bottom: 2px solid transparent;
}
.inspector-tabs button.active {
  color: var(--ink);
  border-bottom-color: var(--accent);
}
.inspector-body {
  padding: 26px 22px;
  overflow: auto;
  flex: 1;
  scrollbar-width: thin;
}
.inspector-body h2 {
  font-size: 19px;
  font-weight: 600;
  line-height: 1.5;
  margin: 0 0 12px;
  letter-spacing: -0.4px;
}
.inspector-body p {
  font-size: 12px;
  line-height: 1.9;
}
.muted {
  color: var(--muted);
}
.progress-heading {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--muted);
  margin: 28px 0 11px;
}
.progress-heading b {
  color: var(--ink);
  font-weight: 500;
}
.progress-track {
  height: 4px;
  border-radius: 4px;
  background: #e7ece3;
  overflow: hidden;
}
.progress-track span {
  display: block;
  height: 100%;
  background: #62806c;
  transition: width 0.3s;
}
.step-list {
  list-style: none;
  padding: 0;
  margin: 25px 0;
}
.step-list li {
  display: flex;
  gap: 14px;
  align-items: center;
  position: relative;
  padding: 0 0 28px;
}
.step-list li:not(:last-child):after {
  content: '';
  position: absolute;
  left: 14px;
  top: 31px;
  height: 22px;
  border-left: 1px solid #dfe4dc;
}
.step-list li > span {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  border: 1px solid #dde3d9;
  border-radius: 50%;
  font-family: monospace;
  font-size: 10px;
  color: #a3ad9d;
  flex-shrink: 0;
}
.step-list li.complete > span {
  background: #e6eee4;
  border-color: #e6eee4;
  color: #51735a;
}
.step-list li.current > span {
  border-color: var(--accent);
  color: var(--accent);
}
.step-list strong {
  font-size: 12px;
  font-weight: 500;
}
.step-list small {
  display: block;
  color: #9da696;
  font-size: 10px;
  margin-top: 5px;
}
.note-box {
  padding: 15px;
  background: #eff2eb;
  border: 1px solid #e6eadf;
  border-radius: 8px;
  font-size: 11px;
  line-height: 1.9;
  color: #78846f;
}
.inspector-footer {
  font-size: 9px;
  text-align: center;
  padding: 15px;
  border-top: 1px solid var(--line);
  color: #a0a897;
}
.user-message {
  background: #f0f3ec;
  border: 1px solid #e8ece3;
  border-radius: 12px;
  padding: 13px 18px;
  margin: 12px 0 26px 35px;
}
.user-message small {
  font-size: 10px;
  color: #8a9782;
}
.user-message p {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.8;
}
.assistant-message {
  margin-bottom: 23px;
}
.assistant-message > p {
  font-size: 13px;
  line-height: 1.9;
  color: #66725f;
}
.assistant-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
}
.assistant-label > span {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: var(--ink);
  color: #fff;
}
.assistant-label small {
  margin-left: auto;
  font-size: 9px;
  font-weight: 400;
  color: #939d8c;
}
.plan-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: white;
  margin: 17px 0;
  padding: 17px 19px;
}
.card-heading {
  font-size: 12px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
}
.card-heading span {
  font-size: 10px;
  color: #98a090;
  font-weight: 400;
}
.plan-card ol {
  padding-left: 20px;
  color: #73806b;
  font-size: 12px;
  line-height: 2.3;
  margin: 13px 0;
}
.card-actions {
  border-top: 1px solid var(--line);
  padding-top: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.card-actions small {
  font-size: 9px;
  color: #a0a892;
}
.primary {
  border: 0;
  border-radius: 7px;
  background: var(--accent);
  color: white;
  padding: 10px 13px;
  font-size: 12px;
}
.tool-stream {
  margin: 20px 0;
}
.tool-stream > div {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 0;
  font-size: 10px;
  border-bottom: 1px solid #edf0e8;
}
.tool-stream code {
  font-size: 10px;
  word-break: break-word;
}
.tool-stream > div > span:last-child {
  margin-left: auto;
  color: #9ba48f;
}
.tool-check {
  color: #568160;
}
.approval-card {
  border: 1px solid #e4d4bf;
  background: #fffaf2;
  border-radius: 11px;
  padding: 20px;
  margin: 20px 0;
}
.approval-card h3,
.completion-card h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 12px 0;
}
.approval-card code {
  font-size: 12px;
}
.approval-card p,
.completion-card p {
  font-size: 12px;
  line-height: 1.9;
  color: #7e856f;
}
.completion-card {
  border-top: 1px solid var(--line);
  padding-top: 20px;
}
.completion-card > span {
  font-size: 11px;
  color: #548064;
}
.file-list {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin: 18px 0 12px;
}
.file-list button {
  font-size: 10px;
  padding: 8px 9px;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: #89957b;
}
.file-list button.active {
  background: #e9eee4;
  color: #425c3a;
}
.file-preview {
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.file-preview > div {
  padding: 10px 12px;
  background: #eff2ea;
  font-family: monospace;
  font-size: 10px;
  display: flex;
  justify-content: space-between;
}
.file-preview button {
  background: transparent;
  border: 0;
  font-size: 10px;
  color: #697b57;
}
.file-preview pre {
  padding: 13px;
  font-size: 11px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 450px;
  overflow: auto;
  margin: 0;
}
.run-summary {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  padding: 14px 0;
}
.success {
  color: #427955;
}
.terminal {
  background: #28352e;
  color: #cdddc8;
  border-radius: 8px;
  padding: 17px;
  font-size: 11px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.metric-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 22px 0;
}
.metric-grid > div {
  border: 1px solid var(--line);
  background: white;
  border-radius: 9px;
  padding: 16px 12px;
}
.metric-grid small {
  display: block;
  color: #8a957d;
  font-size: 10px;
}
.metric-grid strong {
  display: block;
  font-size: 21px;
  font-weight: 500;
  margin-top: 10px;
  letter-spacing: -0.8px;
}
.chart-card {
  border: 1px solid var(--line);
  border-radius: 9px;
  background: white;
  padding: 14px 12px;
  margin-bottom: 16px;
}
.chart-card > div {
  font-size: 11px;
  display: flex;
  justify-content: space-between;
}
.chart-card span {
  color: #97a18b;
  font-size: 9px;
}
.chart-card svg {
  width: 100%;
  height: auto;
  margin-top: 12px;
}
.chart-card text {
  font-size: 10px;
  fill: #9aa38e;
}
.evidence-links {
  display: grid;
  gap: 12px;
  margin-top: 20px;
  font-size: 11px;
  color: #678153;
}
.empty-state {
  padding: 26px 17px;
  background: #eef2e9;
  border: 1px dashed #d5ddca;
  border-radius: 10px;
  font-size: 12px;
  line-height: 2;
  color: #849071;
  margin-top: 20px;
}
.paper-link {
  display: block;
  font-size: 12px;
  color: #657f50;
  margin: 20px 0;
}
.playback-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 10px;
  color: #8a967b;
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: #24312670;
  display: grid;
  place-items: center;
  padding: 25px;
  backdrop-filter: blur(4px);
}
.demo-modal {
  background: #fcfdf9;
  border-radius: 16px;
  max-width: 850px;
  width: 100%;
  max-height: 90dvh;
  overflow: auto;
  padding: 35px;
  position: relative;
}
.modal-close {
  position: absolute;
  right: 13px;
  top: 9px;
  font-size: 25px;
  border: 0;
  background: none;
  color: #8a9680;
}
.demo-modal h2 {
  font-size: 23px;
  margin: 20px 0;
}
.demo-modal h3 {
  font-size: 14px;
  margin-top: 24px;
}
.demo-modal p {
  font-size: 13px;
  line-height: 1.9;
  color: #7a866f;
}
.demo-modal pre {
  padding: 17px;
  background: #edf1e7;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.8;
  overflow: auto;
}
.demo-modal > a {
  font-size: 13px;
  color: #527b45;
}
@media (min-width: 1500px) {
  .workspace-grid {
    grid-template-columns: minmax(400px, 1fr) 410px;
  }
  .transcript {
    padding-left: max(34px, calc((100% - 700px) / 2));
    padding-right: max(34px, calc((100% - 700px) / 2));
  }
  .composer-area {
    padding-left: max(30px, calc((100% - 700px) / 2));
    padding-right: max(30px, calc((100% - 700px) / 2));
  }
}
@media (max-width: 1180px) {
  .research-shell {
    grid-template-columns: 195px minmax(0, 1fr);
  }
  .rail {
    padding: 25px 12px;
  }
  .brand {
    font-size: 14px;
  }
  .workspace-grid {
    grid-template-columns: minmax(320px, 1fr) 300px;
  }
  .transcript {
    padding: 0 22px 20px;
  }
  .composer-area {
    padding: 15px 20px;
  }
  .welcome h1 {
    font-size: 34px;
  }
  .inspector-body {
    padding: 24px 18px;
  }
}
@media (max-width: 960px) {
  .workspace-grid {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }
  .conversation {
    min-height: 700px;
    height: calc(100dvh - 105px);
  }
  .inspector {
    border-top: 1px solid var(--line);
    border-left: 0;
  }
  .inspector-body {
    min-height: 370px;
  }
  .rail-evidence button {
    font-size: 10px;
  }
  .welcome {
    margin-top: 4vh;
  }
  .inspector-heading {
    padding-top: 20px;
  }
}
@media (max-width: 600px) {
  .research-shell {
    display: block;
  }
  .rail {
    padding: 15px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
    gap: 10px;
  }
  .brand {
    font-size: 15px;
  }
  .new-task,
  .rail-label,
  .project-name,
  .rail-evidence,
  .rail-bottom {
    display: none;
  }
  .scene-nav {
    display: flex;
    overflow: auto;
    gap: 4px;
  }
  .scene-nav button {
    white-space: nowrap;
    padding: 10px;
    font-size: 11px;
  }
  .scene-nav button > span,
  .scene-nav b {
    display: none;
  }
  .workspace-header {
    height: 52px;
    padding: 0 17px;
    font-size: 11px;
  }
  .demo-notice {
    padding: 10px 17px;
    line-height: 1.7;
  }
  .conversation {
    height: 760px;
    min-height: 0;
  }
  .conversation-top {
    padding: 18px;
  }
  .welcome {
    margin-top: 18px;
  }
  .welcome h1 {
    font-size: 33px;
  }
  .transcript {
    padding: 0 19px 20px;
  }
  .welcome-symbol {
    margin-bottom: 15px;
  }
  .prompt-card {
    margin-top: 20px;
    padding: 18px;
  }
  .composer-area {
    padding: 12px 17px;
  }
  .user-message {
    margin-left: 15px;
  }
  .assistant-label small {
    font-size: 8px;
  }
  .tool-stream > div > span:last-child {
    display: none;
  }
  .demo-modal {
    padding: 30px 20px;
  }
  .modal-backdrop {
    padding: 12px;
  }
  .inspector-body {
    padding: 23px;
  }
}
@media (prefers-reduced-motion: reduce) {
  * {
    transition: none !important;
    scroll-behavior: auto !important;
  }
}
.workspace-grid.wide-workspace {
  grid-template-columns: minmax(340px, 1fr) minmax(500px, 52%);
}
@media (max-width: 1180px) {
  .workspace-grid.wide-workspace {
    grid-template-columns: minmax(300px, 1fr) minmax(380px, 52%);
  }
}
@media (max-width: 960px) {
  .workspace-grid.wide-workspace {
    grid-template-columns: 1fr;
  }
  .inspector-heading .quiet {
    display: none;
  }
}
</style>
<style scoped>
.research-shell {
  --ink: #17243d;
  --muted: #68788f;
  --line: #e3e9f2;
  --accent: #4666f3;
  background: #fafbfe;
  color: var(--ink);
}
.conversation,
.inspector,
.transcript {
  min-height: 0;
}
.composer-area {
  flex-shrink: 0;
}
.workspace-grid {
  overflow: hidden;
}
.rail {
  background: #f3f6fc;
  border-color: #e1e7f1;
}
.brand-mark,
.assistant-label > span {
  background: #253757;
}
.brand small,
.rail-label,
.rail-bottom {
  color: #7988a2;
}
.new-task {
  border-color: #d8e1ef;
  color: #33466b;
}
.project-name {
  background: #fff;
  border-color: #e0e7f2;
  color: #5b6e8e;
}
.project-dot {
  background: #5374ec;
}
.scene-nav button {
  color: #6f7f99;
  font-size: 12px;
  padding: 13px 10px;
  gap: 8px;
}
.scene-nav button.active {
  background: #e5ecff;
  color: #385be0;
}
.rail-evidence button span {
  color: #4b76b8;
}
.workspace-header {
  background: #fff;
}
.preview-label {
  background: #edf1ff;
  color: #5371c4;
}
.demo-notice {
  background: #f2f6fe;
  border-color: #e1e9f7;
  color: #7185a6;
}
.status-dot {
  background: #5e83d3;
}
.inspector {
  background: #f7f9fd;
  border-color: #e3e9f2;
}
.inspector-heading small {
  color: #8796b0;
}
.inspector-tabs button {
  color: #8391a9;
}
.inspector-tabs button.active {
  color: #4160d8;
  border-color: #4666f3;
}
.inspector-body .eyebrow {
  color: #778eb4;
}
.inspector-body p,
.muted {
  color: #71819c;
}
.progress-track {
  background: #e5ebf7;
}
.progress-track span {
  background: #6183e6;
}
.note-box {
  background: #edf2fb;
  border-color: #e1e9f6;
  color: #6a80a5;
}
.inspector-footer {
  color: #94a1b7;
}
.user-message {
  background: #ecf1ff;
  border-color: #e4eaff;
}
.user-message small {
  color: #7587b3;
}
.assistant-message > p {
  color: #526783;
  font-size: 14px;
  line-height: 2;
}
.composer-area {
  background: linear-gradient(transparent, #fafbfe 15%);
}
.composer {
  border-color: #d9e3f2;
  background: #fff;
}
.send-button {
  background: #4666f3;
  color: #fff;
  font-size: 12px;
}
.primary {
  background: #4666f3;
}
.quiet {
  border-color: #dfe6f2;
  color: #667b9d;
}
.file-list button.active {
  background: #e8efff;
  color: #4d6bc1;
}
.file-list button {
  color: #7a8eaf;
  border-color: #dee7f5;
}
.file-preview > div {
  background: #eef3fc;
  color: #6480aa;
}
.file-preview button {
  color: #5778c1;
}
.terminal {
  background: #1e2c45;
  color: #cfddf5;
}
.metric-grid small {
  color: #8191ae;
}
.chart-card span {
  color: #8b9cba;
}
.evidence-links,
.paper-link {
  color: #5274d6;
}
.empty-state {
  background: #eef3fc;
  border-color: #dce5f4;
  color: #7b8ead;
}
.demo-modal {
  background: #fff;
}
.demo-modal pre {
  background: #f0f4fc;
}
.demo-modal p {
  color: #6a7f9f;
}
.demo-modal > a {
  color: #5274d6;
}
.story-heading {
  margin: 22px 0 35px;
  border-bottom: 1px solid #e8edf7;
  padding-bottom: 24px;
}
.story-heading h1 {
  font-size: 26px;
  font-weight: 650;
  color: #203354;
  letter-spacing: -0.5px;
  margin: 10px 0;
}
.story-heading p {
  font-size: 11px;
  color: #8696af;
  line-height: 1.8;
}
.story-heading .eyebrow {
  color: #738cbe;
}
.story-turn {
  margin-bottom: 32px;
}
.story-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.story-controls > span {
  margin-right: auto;
  font-size: 11px;
  color: #8797b2;
}
.replay-composer p {
  font-size: 13px;
  line-height: 1.9;
  color: #567098;
  margin: 10px 0 16px;
}
.next-label {
  font-size: 10px;
  color: #91a0ba;
}
.replay-composer > div:last-child {
  gap: 8px;
  flex-wrap: wrap;
}
.execution-summary {
  background: #f4f7fd;
  border: 1px solid #e6ecf7;
  border-radius: 8px;
  margin: 14px 0;
  color: #738aad;
  font-size: 12px;
}
.execution-summary summary {
  padding: 11px 13px;
  cursor: pointer;
}
.execution-summary p {
  padding: 0 13px 12px;
  margin: 0;
  line-height: 1.9;
}
.inspect-turn {
  background: white;
  border: 1px solid #dce5f6;
  border-radius: 7px;
  padding: 8px 12px;
  color: #5a78c9;
  font-size: 11px;
  cursor: pointer;
}
.story-complete {
  border-top: 1px solid #e2eafa;
  padding: 18px 0;
  font-size: 11px;
  color: #708dc4;
}
.replay-loading {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #7b92bf;
  font-size: 12px;
  padding: 18px 0;
}
.replay-loading span {
  width: 7px;
  height: 7px;
  background: #6081f0;
  border-radius: 50%;
}
.rail-evidence {
  margin-top: 18px;
}
.composer-footnote {
  color: #99a8bf;
}
.user-message p {
  font-size: 14px;
  line-height: 1.9;
}
@media (max-width: 600px) {
  .story-heading h1 {
    font-size: 23px;
  }
  .scene-nav button {
    font-size: 11px;
  }
  .story-controls .quiet {
    font-size: 10px;
  }
  .replay-composer .send-button {
    width: 100%;
  }
}
</style>
