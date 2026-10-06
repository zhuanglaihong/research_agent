<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref } from 'vue'
import { scenarios } from '@/fixtures/research-demo'
import report from '@/fixtures/feedback-report.json'
import RealExperimentCase from '@/components/RealExperimentCase.vue'

const selected = ref(3)
const scene = computed(() => scenarios[selected.value]!)
const phase = ref('ready')
const draft = ref('')
const request = ref('')
const completed = ref(0)
const tab = ref('flow')
const fileIndex = ref(0)
const modal = ref('')
const followups = ref<string[]>([])
const transcript = ref<HTMLElement>()
let timer: ReturnType<typeof setTimeout> | undefined
const file = computed(() => scene.value.files[fileIndex.value] ?? scene.value.files[0]!)
const loss = report.series.find(s => s.name === 'loss')!
const points = loss.points.map(p => `${40 + p.step * 25},${174 - p.value * 32}`).join(' ')
const base = import.meta.env.BASE_URL
const labels: Record<string, string> = { ready: '等待任务', approval: '等待计划确认', running: '流程回放中', runApproval: '等待运行确认', done: '回放完成', paused: '回放已暂停' }
function stop() { if (timer) clearTimeout(timer) }
function choose(index: number) {
  stop(); selected.value = index; phase.value = 'ready'; draft.value = ''; request.value = ''
  completed.value = 0; tab.value = 'flow'; fileIndex.value = 0; followups.value = []
}
async function scroll() { await nextTick(); transcript.value?.scrollTo({ top: transcript.value.scrollHeight, behavior: 'smooth' }) }
function send() {
  if (!['ready', 'done'].includes(phase.value)) return
  const value = draft.value.trim() || scene.value.prompt
  if (phase.value === 'done') { followups.value.push(value); draft.value = ''; scroll(); return }
  request.value = value; draft.value = ''; phase.value = 'approval'; scroll()
}
function advance() {
  phase.value = 'running'
  timer = setTimeout(() => {
    completed.value++
    if (selected.value === 3 && completed.value === 2) { phase.value = 'runApproval'; tab.value = 'files'; scroll(); return }
    if (completed.value >= scene.value.steps.length) { phase.value = 'done'; tab.value = scene.value.verified ? 'results' : 'files'; scroll(); return }
    scroll(); advance()
  }, 800)
}
function pause() { stop(); phase.value = 'paused' }
function download() {
  const url = URL.createObjectURL(new Blob([file.value.content], { type: 'text/plain;charset=utf-8' }))
  const a = document.createElement('a'); a.href = url; a.download = file.value.name; a.click(); URL.revokeObjectURL(url)
}
onUnmounted(stop)
</script>

<template>
  <div class="research-shell">
    <aside class="rail">
      <a class="brand" href="#/demo"><span class="brand-mark">r_</span><span>research_agent<small>RESEARCH WORKSPACE</small></span></a>
      <button class="new-task" @click="choose(selected)">＋ 新建科研对话</button>
      <div class="rail-label">工作空间</div>
      <div class="project-name"><span class="project-dot"></span>{{ scene.project }}</div>
      <div class="rail-label scenario-label">探索能力</div>
      <nav class="scene-nav" aria-label="科研场景">
        <button v-for="(item, i) in scenarios" :key="item.short" :class="{ active: selected === i }" @click="choose(i)">
          <span>{{ item.icon }}</span>{{ item.short }}<b v-if="selected === i">↗</b>
        </button>
      </nav>
      <div class="rail-evidence"><div class="rail-label">可核验的案例</div>
        <button @click="choose(3)">● Ollama 生成与运行 <span>实测</span></button>
        <button @click="modal = 'cases'">◈ Digits 三种子基线 <span>实测</span></button>
      </div>
      <div class="rail-bottom"><button @click="modal = 'models'">⚙ 模型与本地配置</button><a href="https://github.com/zhuanglaihong/research_agent" target="_blank" rel="noopener">GitHub ↗</a><small>个人科研 · 无需登录</small></div>
    </aside>
    <main class="workspace">
      <header class="workspace-header"><div><span class="breadcrumb">工作空间 / </span>{{ scene.short }}</div><div class="header-actions"><span class="preview-label">交互演示</span><button class="quiet" @click="choose(selected)">重置</button></div></header>
      <div class="demo-notice"><span>ⓘ</span> 静态演示：预置对话与流程回放，不调用模型、不执行代码、不访问你的电脑。</div>
      <div class="workspace-grid">
        <section class="conversation" aria-label="科研对话">
          <div class="conversation-top"><span class="status-dot" :class="{ running: phase === 'running' }"></span>{{ labels[phase] }}<span class="case-badge">{{ scene.verified ? '本机实测记录' : '预置产品流程' }}</span></div>
          <div ref="transcript" class="transcript" aria-live="polite">
            <div v-if="phase === 'ready'" class="welcome">
              <div class="welcome-symbol">r_</div><div class="eyebrow">从想法，到可追踪的实验</div>
              <h1>你的研究，<br>从一句话开始。</h1><p>对话下达任务，在同一工作区审阅计划、代码与实验结果。</p>
              <button class="prompt-card" @click="draft = scene.prompt"><span>{{ scene.title }}</span><p>{{ scene.prompt }}</p><b>使用这条示例 ↗</b></button>
              <div class="welcome-hint">左侧切换场景 · 右侧查看流程与产物</div>
            </div>
            <template v-else>
              <div class="user-message"><small>你</small><p>{{ request }}</p></div>
              <div class="assistant-message"><div class="assistant-label"><span>r_</span> research_agent <small>{{ scene.verified ? '实测回放 · 固定文本' : '预置示例 · 固定文本' }}</small></div><p>{{ scene.description }}</p>
                <div class="plan-card"><div class="card-heading">执行计划 <span>{{ scene.steps.length }} 个步骤</span></div><ol><li v-for="step in scene.steps" :key="step">{{ step }}</li></ol>
                  <div v-if="phase === 'approval'" class="card-actions"><button class="primary" @click="advance">确认计划，开始回放 →</button><small>此按钮不触发真实执行</small></div>
                </div>
              </div>
              <div v-if="completed" class="tool-stream"><div v-for="(tool, i) in scene.tools.slice(0, completed)" :key="tool"><span class="tool-check">✓</span><code>{{ tool }}</code><span>{{ scene.steps[i] }}</span></div></div>
              <div v-if="phase === 'runApproval'" class="approval-card"><div class="eyebrow">运行确认 · 回放检查点</div><h3>先审阅脚本，再批准实验</h3><code>python train.py</code><p>真实记录采用人工批准的 Java Runner。此处只回放审批过程，不在浏览器启动进程。</p><button class="primary" @click="advance">确认，继续实测回放 →</button></div>
              <div v-if="phase === 'done'" class="completion-card"><span>✓ {{ scene.verified ? '记录已整理' : '流程预览完成' }}</span><h3>{{ scene.verified ? '结果、依据和下一步，都在这里。' : '实现边界也应该是结果的一部分。' }}</h3><p v-if="scene.verified">Run 1 退出码 0；loss 4.0000 → 0.046117。建议比较学习率 0.05，后续任务仍是草稿。</p><p>{{ scene.boundary }}</p><button class="quiet" @click="tab = 'results'">查看结果与依据 ↗</button></div>
              <div v-for="(message, i) in followups" :key="i"><div class="user-message"><small>你</small><p>{{ message }}</p></div><div class="assistant-message"><p>这是静态演示，没有为这条消息调用模型。请启动本地工作台提交真实任务；这里可以继续查看既有产物和实测记录。</p></div></div>
            </template>
          </div>
          <div class="composer-area">
            <div v-if="phase === 'running' || phase === 'paused'" class="playback-controls"><span>仅回放流程，不表示训练百分比</span><button class="quiet" @click="phase === 'running' ? pause() : advance()">{{ phase === 'running' ? '暂停回放' : '继续回放' }}</button></div>
            <form class="composer" @submit.prevent="send"><textarea v-model="draft" :disabled="!['ready','done'].includes(phase)" aria-label="科研任务" placeholder="描述你的科研任务，或点击上方示例…" rows="2" @keydown.ctrl.enter.prevent="send"></textarea><div><span>演示模式 <small>· Ctrl + Enter</small></span><button class="send-button" :disabled="!['ready','done'].includes(phase)" type="submit">发送 ↑</button></div></form>
            <small class="composer-footnote">真实操作需要本地服务与模型配置；执行前审阅命令和预算。</small>
          </div>
        </section>
        <aside class="inspector" aria-label="任务可视化管理">
          <div class="inspector-heading"><span>任务工作区</span><small>{{ scene.verified ? 'VERIFIED RECORD' : 'PRODUCT PREVIEW' }}</small></div>
          <nav class="inspector-tabs"><button v-for="item in [{id:'flow',name:'流程'},{id:'files',name:'产物'},{id:'logs',name:'日志'},{id:'results',name:'结果'},{id:'evidence',name:'说明'}]" :key="item.id" :class="{ active: tab === item.id }" @click="tab = item.id">{{ item.name }}</button></nav>
          <div class="inspector-body">
            <template v-if="tab === 'flow'"><div class="eyebrow">WORKFLOW</div><h2>{{ scene.title }}</h2><p class="muted">每一步都可追踪，关键执行由你确认。</p><div class="progress-heading"><span>回放进度</span><b>{{ completed }}/{{ scene.steps.length }}</b></div><div class="progress-track"><span :style="{width: completed / scene.steps.length * 100 + '%'}"></span></div>
              <ol class="step-list"><li v-for="(step, i) in scene.steps" :key="step" :class="{ complete: i < completed, current: i === completed && phase !== 'ready' }"><span>{{ i < completed ? '✓' : String(i + 1).padStart(2, '0') }}</span><div><strong>{{ step }}</strong><small>{{ i < completed ? '回放已展示' : i === completed && phase === 'runApproval' ? '等待人工确认' : '等待展示' }}</small></div></li></ol>
              <div class="note-box">进度是演示步骤计数；真实后端通过任务事件和运行状态展示进度。</div>
            </template>
            <template v-if="tab === 'files'"><div class="eyebrow">ARTIFACTS</div><h2>可审阅的产物</h2><p class="muted">{{ scene.verified ? '真实脚本与审阅记录；分析页为预置说明。' : '预置说明文件，不是在线生成代码。' }}</p><div class="file-list"><button v-for="(item,i) in scene.files" :key="item.name" :class="{ active: fileIndex === i }" @click="fileIndex = i">▤ {{ item.name }}</button></div><div class="file-preview"><div>{{ file.name }} <button @click="download">下载 ↓</button></div><pre>{{ file.content }}</pre></div></template>
            <template v-if="tab === 'logs'"><div class="eyebrow">RUN RECORD</div><h2>运行记录</h2><template v-if="scene.verified"><div class="run-summary"><span class="success">SUCCEEDED</span><span>Run 1 · exit 0</span></div><pre class="terminal">[真实运行摘要，非完整 stdout]
provider: Ollama / qwen3:8b
runner: Java ProcessBuilder
script: train.py
metric points: 11
initial loss: 4.0
final loss: 0.04611686018427385
exit code: 0

后续实验：仅草稿，尚未执行</pre><p class="muted">本页面没有活跃进程。完整验收记录随仓库发布。</p></template><div v-else class="empty-state">此场景没有真实运行日志。不会用预览文字模拟一次成功实验。</div></template>
            <template v-if="tab === 'results'"><div class="eyebrow">RESULTS & EVIDENCE</div><h2>{{ scene.verified ? '真实观测，明确边界' : '尚无实验结果' }}</h2><template v-if="scene.verified"><div class="metric-grid"><div><small>初始 loss</small><strong>4.0000</strong></div><div><small>最终 loss</small><strong>0.046117</strong></div></div><div class="chart-card"><div>二次函数优化 <span>11 个真实点</span></div><svg viewBox="0 0 320 205" role="img" aria-label="真实 loss 从 4 降至 0.046"><path d="M40 40V174H290" fill="none" stroke="#dadfd9"/><path d="M40 110H290" stroke="#eef0ed"/><polyline :points="points" fill="none" stroke="#3b7760" stroke-width="3"/><circle v-for="p in loss.points" :key="p.step" :cx="40+p.step*25" :cy="174-p.value*32" r="3" fill="#3b7760"/><text x="8" y="48">4.0</text><text x="14" y="177">0</text><text x="40" y="198">step 0</text><text x="249" y="198">step 10</text></svg></div><div class="note-box">最小链路验证，不是论文复现或泛化性能证明。模型分析仍需人工审阅。</div><div class="evidence-links"><a :href="base+'cases/feedback/metrics-report.json'" target="_blank">原始指标 JSON ↗</a><a :href="base+'cases/feedback/review.md'" target="_blank">人工审阅记录 ↗</a></div></template><div v-else class="empty-state">论文和仓库场景是规划预览，没有训练结果或评测分数。请查看“说明”了解待实现部分。</div></template>
            <template v-if="tab === 'evidence'"><div class="eyebrow">IMPLEMENTATION BOUNDARY</div><h2>展示什么，已做什么</h2><p>{{ scene.boundary }}</p><div class="note-box">对话内容与工具步骤为前端预置。实测数值来自本机记录；输入任意任务不会在线生成答案。</div><a v-if="selected < 2" class="paper-link" href="https://arxiv.org/abs/1706.03762" target="_blank" rel="noopener">参考论文：Attention Is All You Need ↗</a><p class="muted">技术底座：Java 21 · Spring Boot · LangChain4j · Vue 3 · H2 · BM25 · SSE · 本机只读 MCP。</p><button class="quiet" @click="modal = 'models'">查看 API / Ollama 配置</button></template>
          </div><div class="inspector-footer">任务 · 代码 · 运行 · 证据，在同一工作区</div>
        </aside>
      </div>
    </main>
    <div v-if="modal" class="modal-backdrop" @click.self="modal = ''"><section class="demo-modal" role="dialog" aria-modal="true" aria-label="案例与配置"><button class="modal-close" aria-label="关闭" @click="modal = ''">×</button><RealExperimentCase v-if="modal === 'cases'"/><template v-else><div class="eyebrow">LOCAL FIRST</div><h2>模型由你选择，任务在本地运行。</h2><p>先按 README 启动本地后端和前端。选择 live 模式后，编辑根目录 .env 并重启后端。</p><h3>Ollama · 无 API Key</h3><pre>RESEARCH_AI_MODE=live
LLM_PROVIDER=ollama
LLM_BASE_URL=http://localhost:11434/v1
LLM_MODEL=qwen3:8b</pre><p>先运行 ollama list 查看模型，使用已下载且支持工具调用的模型名称。</p><h3>OpenAI 兼容 API</h3><pre>RESEARCH_AI_MODE=live
LLM_PROVIDER=openai-compatible
LLM_BASE_URL=你的兼容接口地址
LLM_MODEL=支持工具调用的模型
LLM_API_KEY=仅保存在本地</pre><p>该配置说明不会连接模型。服务器和容器需配置能访问的 Ollama 地址。</p><a href="https://github.com/zhuanglaihong/research_agent#readme" target="_blank" rel="noopener">打开 README 配置教程 ↗</a></template></section></div>
  </div>
</template>

<style scoped>
.research-shell{--ink:#26352f;--muted:#78837c;--line:#e4e8e2;--accent:#bb6449;display:grid;grid-template-columns:228px minmax(0,1fr);min-height:100dvh;background:#fcfdfb;color:var(--ink);font-size:14px;font-family:Inter,"Segoe UI","Microsoft YaHei",sans-serif}
button,a,textarea{font:inherit}button{cursor:pointer}button:disabled{cursor:default;opacity:.45}a{color:inherit;text-decoration:none}button:focus-visible,a:focus-visible,textarea:focus-visible{outline:2px solid var(--accent);outline-offset:3px}button{transition:background .15s}button:hover{filter:brightness(.97)}.rail{padding:27px 17px;background:#f3f5f0;border-right:1px solid var(--line);display:flex;flex-direction:column;gap:12px}.brand{display:flex;gap:10px;align-items:center;font-size:16px;font-weight:650;letter-spacing:-.5px}.brand-mark{display:grid;place-items:center;background:var(--ink);color:white;border-radius:12px;width:39px;height:39px;font-size:23px}.brand small{display:block;font-size:8px;letter-spacing:1.2px;color:var(--muted);margin-top:5px}.new-task{background:#fff;border:1px solid #d6ddd4;border-radius:9px;padding:12px;margin:20px 0 9px;text-align:left;font-weight:600}.rail-label{font-size:11px;color:var(--muted);letter-spacing:1px;text-transform:uppercase;margin:6px 8px}.project-name{display:flex;align-items:center;gap:8px;font-size:12px;padding:11px 8px;border:1px solid var(--line);border-radius:8px;background:#f9faf7}.project-dot{width:7px;height:7px;background:#59826d;border-radius:50%}.scenario-label{margin-top:20px}.scene-nav{display:grid;gap:5px}.scene-nav button{border:0;background:transparent;padding:13px 11px;text-align:left;border-radius:8px;display:flex;gap:13px;font-size:13px;align-items:center;color:#68736c}.scene-nav button>span{font-family:monospace;font-size:11px;opacity:.6}.scene-nav button.active{background:#e8eee4;color:#304c3b;font-weight:650}.scene-nav b{margin-left:auto;font-weight:400}.rail-evidence{margin-top:28px}.rail-evidence button{border:0;background:none;display:block;width:100%;padding:10px 8px;text-align:left;font-size:11px}.rail-evidence button span{float:right;color:#66816f;font-size:10px}.rail-bottom{margin-top:auto;padding-top:40px;display:grid;gap:16px;font-size:12px;color:#6d7870}.rail-bottom button{background:none;border:0;text-align:left;padding:0;color:inherit}.rail-bottom small{font-size:10px;color:#939b94}.workspace{min-width:0}.workspace-header{height:67px;padding:0 30px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line);font-size:13px}.breadcrumb{color:#939b94}.header-actions{display:flex;align-items:center;gap:18px}.preview-label{padding:5px 9px;border-radius:5px;background:#f1ede3;font-size:11px;color:#8b7853}.quiet{background:white;border:1px solid var(--line);border-radius:7px;padding:7px 11px;font-size:12px}.demo-notice{min-height:38px;padding:10px 25px;font-size:11px;color:#7d775e;background:#fbf8ef;border-bottom:1px solid #eee8db;display:flex;align-items:center;gap:8px}.workspace-grid{display:grid;grid-template-columns:minmax(360px,1fr) 355px;height:calc(100dvh - 105px);min-height:680px}.conversation{min-width:0;display:flex;flex-direction:column}.conversation-top{padding:20px 30px;font-size:11px;color:var(--muted);display:flex;align-items:center;gap:8px}.status-dot{height:6px;width:6px;border-radius:50%;background:#95a68c}.status-dot.running{background:var(--accent)}.case-badge{margin-left:auto;font-size:10px;padding:4px 7px;border:1px solid var(--line);border-radius:5px}.transcript{flex:1;overflow:auto;padding:0 34px 25px;scrollbar-width:thin}.welcome{max-width:480px;margin:4vh auto 0}.welcome-symbol{color:var(--accent);font-size:35px;font-weight:650;margin-bottom:25px}.eyebrow{font-size:10px;letter-spacing:1.2px;color:#8b958d;margin-bottom:12px}.welcome h1{font-size:40px;line-height:1.4;letter-spacing:-1.6px;font-weight:600;margin:0 0 15px}.welcome>p{font-size:14px;line-height:1.9;color:var(--muted);max-width:390px}.prompt-card{display:block;width:100%;background:#fff;border:1px solid #e1e6de;text-align:left;border-radius:12px;padding:22px;margin-top:32px;box-shadow:0 3px 12px #26352f03}.prompt-card>span{font-weight:600;font-size:13px}.prompt-card p{font-size:12px;color:#7c877e;line-height:1.8;margin:11px 0 16px}.prompt-card b{font-size:11px;font-weight:500;color:var(--accent)}.welcome-hint{font-size:10px;color:#a0a79e;margin-top:18px}.composer-area{padding:16px 30px 18px;background:linear-gradient(transparent,#fcfdfb 15%)}.composer{border:1px solid #dce1d8;border-radius:12px;background:white;padding:13px 14px;box-shadow:0 4px 20px #24342904}.composer textarea{resize:none;border:0;outline:0;width:100%;background:transparent;font-size:13px;line-height:1.8;color:var(--ink)}.composer textarea::placeholder{color:#a1a99f}.composer>div{display:flex;align-items:center;justify-content:space-between;padding-top:8px}.composer>div>span{font-size:10px;color:#8f988d}.composer small{font-size:9px}.send-button{background:var(--ink);color:#fff;border:0;border-radius:7px;padding:8px 13px;font-size:11px}.composer-footnote{display:block;text-align:center;font-size:9px;color:#a1a99f;margin-top:10px}.inspector{min-width:0;border-left:1px solid var(--line);background:#f9faf7;display:flex;flex-direction:column}.inspector-heading{padding:24px 23px 17px;display:flex;align-items:center;justify-content:space-between;font-size:12px;font-weight:600}.inspector-heading small{font-size:8px;letter-spacing:.5px;color:#96a08f;font-weight:400}.inspector-tabs{display:flex;padding:0 15px;border-bottom:1px solid var(--line)}.inspector-tabs button{flex:1;background:none;border:0;padding:12px 0;font-size:12px;color:#8b9588;border-bottom:2px solid transparent}.inspector-tabs button.active{color:var(--ink);border-bottom-color:var(--accent)}.inspector-body{padding:26px 22px;overflow:auto;flex:1;scrollbar-width:thin}.inspector-body h2{font-size:19px;font-weight:600;line-height:1.5;margin:0 0 12px;letter-spacing:-.4px}.inspector-body p{font-size:12px;line-height:1.9}.muted{color:var(--muted)}.progress-heading{display:flex;justify-content:space-between;font-size:11px;color:var(--muted);margin:28px 0 11px}.progress-heading b{color:var(--ink);font-weight:500}.progress-track{height:4px;border-radius:4px;background:#e7ece3;overflow:hidden}.progress-track span{display:block;height:100%;background:#62806c;transition:width .3s}.step-list{list-style:none;padding:0;margin:25px 0}.step-list li{display:flex;gap:14px;align-items:center;position:relative;padding:0 0 28px}.step-list li:not(:last-child):after{content:"";position:absolute;left:14px;top:31px;height:22px;border-left:1px solid #dfe4dc}.step-list li>span{display:grid;place-items:center;width:29px;height:29px;border:1px solid #dde3d9;border-radius:50%;font-family:monospace;font-size:10px;color:#a3ad9d;flex-shrink:0}.step-list li.complete>span{background:#e6eee4;border-color:#e6eee4;color:#51735a}.step-list li.current>span{border-color:var(--accent);color:var(--accent)}.step-list strong{font-size:12px;font-weight:500}.step-list small{display:block;color:#9da696;font-size:10px;margin-top:5px}.note-box{padding:15px;background:#eff2eb;border:1px solid #e6eadf;border-radius:8px;font-size:11px;line-height:1.9;color:#78846f}.inspector-footer{font-size:9px;text-align:center;padding:15px;border-top:1px solid var(--line);color:#a0a897}.user-message{background:#f0f3ec;border:1px solid #e8ece3;border-radius:12px;padding:13px 18px;margin:12px 0 26px 35px}.user-message small{font-size:10px;color:#8a9782}.user-message p{margin:6px 0;font-size:13px;line-height:1.8}.assistant-message{margin-bottom:23px}.assistant-message>p{font-size:13px;line-height:1.9;color:#66725f}.assistant-label{display:flex;align-items:center;gap:8px;font-size:12px;font-weight:600}.assistant-label>span{width:24px;height:24px;border-radius:6px;display:grid;place-items:center;background:var(--ink);color:#fff}.assistant-label small{margin-left:auto;font-size:9px;font-weight:400;color:#939d8c}.plan-card{border:1px solid var(--line);border-radius:10px;background:white;margin:17px 0;padding:17px 19px}.card-heading{font-size:12px;font-weight:600;display:flex;justify-content:space-between}.card-heading span{font-size:10px;color:#98a090;font-weight:400}.plan-card ol{padding-left:20px;color:#73806b;font-size:12px;line-height:2.3;margin:13px 0}.card-actions{border-top:1px solid var(--line);padding-top:14px;display:flex;align-items:center;gap:10px;flex-wrap:wrap}.card-actions small{font-size:9px;color:#a0a892}.primary{border:0;border-radius:7px;background:var(--accent);color:white;padding:10px 13px;font-size:12px}.tool-stream{margin:20px 0}.tool-stream>div{display:flex;align-items:center;gap:9px;padding:10px 0;font-size:10px;border-bottom:1px solid #edf0e8}.tool-stream code{font-size:10px;word-break:break-word}.tool-stream>div>span:last-child{margin-left:auto;color:#9ba48f}.tool-check{color:#568160}.approval-card{border:1px solid #e4d4bf;background:#fffaf2;border-radius:11px;padding:20px;margin:20px 0}.approval-card h3,.completion-card h3{font-size:16px;font-weight:600;margin:12px 0}.approval-card code{font-size:12px}.approval-card p,.completion-card p{font-size:12px;line-height:1.9;color:#7e856f}.completion-card{border-top:1px solid var(--line);padding-top:20px}.completion-card>span{font-size:11px;color:#548064}.file-list{display:flex;gap:5px;flex-wrap:wrap;margin:18px 0 12px}.file-list button{font-size:10px;padding:8px 9px;background:transparent;border:1px solid var(--line);border-radius:6px;color:#89957b}.file-list button.active{background:#e9eee4;color:#425c3a}.file-preview{border:1px solid var(--line);border-radius:8px;overflow:hidden;background:#fff}.file-preview>div{padding:10px 12px;background:#eff2ea;font-family:monospace;font-size:10px;display:flex;justify-content:space-between}.file-preview button{background:transparent;border:0;font-size:10px;color:#697b57}.file-preview pre{padding:13px;font-size:11px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere;max-height:450px;overflow:auto;margin:0}.run-summary{display:flex;justify-content:space-between;font-size:10px;padding:14px 0}.success{color:#427955}.terminal{background:#28352e;color:#cdddc8;border-radius:8px;padding:17px;font-size:11px;line-height:1.9;white-space:pre-wrap;overflow-wrap:anywhere}.metric-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:22px 0}.metric-grid>div{border:1px solid var(--line);background:white;border-radius:9px;padding:16px 12px}.metric-grid small{display:block;color:#8a957d;font-size:10px}.metric-grid strong{display:block;font-size:21px;font-weight:500;margin-top:10px;letter-spacing:-.8px}.chart-card{border:1px solid var(--line);border-radius:9px;background:white;padding:14px 12px;margin-bottom:16px}.chart-card>div{font-size:11px;display:flex;justify-content:space-between}.chart-card span{color:#97a18b;font-size:9px}.chart-card svg{width:100%;height:auto;margin-top:12px}.chart-card text{font-size:10px;fill:#9aa38e}.evidence-links{display:grid;gap:12px;margin-top:20px;font-size:11px;color:#678153}.empty-state{padding:26px 17px;background:#eef2e9;border:1px dashed #d5ddca;border-radius:10px;font-size:12px;line-height:2;color:#849071;margin-top:20px}.paper-link{display:block;font-size:12px;color:#657f50;margin:20px 0}.playback-controls{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;font-size:10px;color:#8a967b}.modal-backdrop{position:fixed;inset:0;z-index:30;background:#24312670;display:grid;place-items:center;padding:25px;backdrop-filter:blur(4px)}.demo-modal{background:#fcfdf9;border-radius:16px;max-width:850px;width:100%;max-height:90dvh;overflow:auto;padding:35px;position:relative}.modal-close{position:absolute;right:13px;top:9px;font-size:25px;border:0;background:none;color:#8a9680}.demo-modal h2{font-size:23px;margin:20px 0}.demo-modal h3{font-size:14px;margin-top:24px}.demo-modal p{font-size:13px;line-height:1.9;color:#7a866f}.demo-modal pre{padding:17px;background:#edf1e7;border-radius:8px;font-size:12px;line-height:1.8;overflow:auto}.demo-modal>a{font-size:13px;color:#527b45}
@media(min-width:1500px){.workspace-grid{grid-template-columns:minmax(400px,1fr) 410px}.transcript{padding-left:max(34px,calc((100% - 700px)/2));padding-right:max(34px,calc((100% - 700px)/2))}.composer-area{padding-left:max(30px,calc((100% - 700px)/2));padding-right:max(30px,calc((100% - 700px)/2))}}
@media(max-width:1180px){.research-shell{grid-template-columns:195px minmax(0,1fr)}.rail{padding:25px 12px}.brand{font-size:14px}.workspace-grid{grid-template-columns:minmax(320px,1fr) 300px}.transcript{padding:0 22px 20px}.composer-area{padding:15px 20px}.welcome h1{font-size:34px}.inspector-body{padding:24px 18px}}
@media(max-width:960px){.workspace-grid{grid-template-columns:1fr;height:auto;min-height:0}.conversation{min-height:700px;height:calc(100dvh - 105px)}.inspector{border-top:1px solid var(--line);border-left:0}.inspector-body{min-height:370px}.rail-evidence button{font-size:10px}.welcome{margin-top:4vh}.inspector-heading{padding-top:20px}}
@media(max-width:600px){.research-shell{display:block}.rail{padding:15px;border-right:0;border-bottom:1px solid var(--line);gap:10px}.brand{font-size:15px}.new-task,.rail-label,.project-name,.rail-evidence,.rail-bottom{display:none}.scene-nav{display:flex;overflow:auto;gap:4px}.scene-nav button{white-space:nowrap;padding:10px;font-size:11px}.scene-nav button>span,.scene-nav b{display:none}.workspace-header{height:52px;padding:0 17px;font-size:11px}.demo-notice{padding:10px 17px;line-height:1.7}.conversation{height:760px;min-height:0}.conversation-top{padding:18px}.welcome{margin-top:18px}.welcome h1{font-size:33px}.transcript{padding:0 19px 20px}.welcome-symbol{margin-bottom:15px}.prompt-card{margin-top:20px;padding:18px}.composer-area{padding:12px 17px}.user-message{margin-left:15px}.assistant-label small{font-size:8px}.tool-stream>div>span:last-child{display:none}.demo-modal{padding:30px 20px}.modal-backdrop{padding:12px}.inspector-body{padding:23px}}
@media(prefers-reduced-motion:reduce){*{transition:none!important;scroll-behavior:auto!important}}
</style>
