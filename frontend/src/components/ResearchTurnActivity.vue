<script setup lang="ts">
import { computed } from 'vue'
import records from '@/fixtures/feedback-events.json'
import train from '@/fixtures/feedback-train.txt?raw'
import stdout from '@/fixtures/feedback-stdout.txt?raw'
const props = defineProps<{ scenario: number; turnIndex: number; summary: string }>()
const emit = defineEmits<{ inspect: [target: string] }>()
const taskId = computed(() => (props.scenario === 3 && props.turnIndex === 0 ? '1' : '65'))
const tools = computed(() => {
  if (props.scenario === 3 && props.turnIndex === 0)
    return records.find((r) => r.taskId === '1')!.events.filter((e) => e.type === 'TOOL')
  if (
    (props.scenario === 3 && props.turnIndex === 2) ||
    (props.scenario === 4 && props.turnIndex === 0)
  )
    return records
      .find((r) => r.taskId === '65')!
      .events.filter((e) => e.type === 'TOOL' && JSON.parse(e.payload).name === 'inspectExperiment')
  if (props.scenario === 3 && props.turnIndex === 3)
    return records
      .find((r) => r.taskId === '65')!
      .events.filter((e) => e.type === 'TOOL' && JSON.parse(e.payload).name === 'writeArtifact')
  return []
})
function fields(payload: string) {
  return JSON.stringify(JSON.parse(payload), null, 2)
}
function artifactTarget(payload: string) {
  const data = JSON.parse(payload)
  if (data.name === 'inspectExperiment') return 'results'
  if (data.path?.endsWith('train.py')) return 'train.py'
  if (data.path?.endsWith('analysis.md')) return 'analysis.md'
  return ''
}
function result(payload: string) {
  const data = JSON.parse(payload)
  return data.name === 'inspectExperiment'
    ? 'Run 1 · SUCCEEDED · exit 0\n11 个观测点\nloss: 4.0 → 0.04611686018427385'
    : '保存文件：' + data.path + '\n字符数：' + data.characters + '\n所属任务：SUCCEEDED'
}
</script>

<template>
  <div class="turn-activity">
    <details class="analysis-summary">
      <summary>
        <span class="activity-icon">◈</span><strong>思考与执行分析</strong><small>分析摘要</small>
      </summary>
      <p>{{ summary }}</p>
    </details>
    <details v-for="event in tools" :key="event.timestamp" class="tool-record" open>
      <summary>
        <span class="activity-icon tool">↗</span><code>{{ JSON.parse(event.payload).name }}</code
        ><span class="record-success">✓ 已记录</span>
      </summary>
      <div class="record-body">
        <div class="record-meta">
          任务 {{ taskId }} · {{ event.timestamp.slice(11, 19) }} · 实测事件
        </div>
        <div class="record-columns">
          <div>
            <small>记录字段</small>
            <pre>{{ fields(event.payload) }}</pre>
          </div>
          <div>
            <small>结果摘要</small>
            <pre>{{ result(event.payload) }}</pre>
          </div>
        </div>
        <button
          v-if="artifactTarget(event.payload)"
          @click="emit('inspect', artifactTarget(event.payload))"
        >
          查看对应{{ JSON.parse(event.payload).name === 'inspectExperiment' ? '指标' : '文件' }} ↗
        </button>
      </div>
    </details>
    <details v-if="scenario === 3 && turnIndex === 0" class="code-record">
      <summary>
        <span class="activity-icon code">⌘</span><strong>编写代码</strong><code>train.py</code
        ><small>实际生成产物</small>
      </summary>
      <div class="record-body">
        <pre class="source-code">{{ train }}</pre>
        <button @click="emit('inspect', 'train.py')">在工作区审阅代码 ↗</button>
      </div>
    </details>
    <details v-if="scenario === 3 && turnIndex === 1" class="terminal-record" open>
      <summary>
        <span class="activity-icon terminal">›_</span><strong>终端执行记录</strong
        ><span class="record-success">exit 0</span>
      </summary>
      <div class="record-body">
        <div class="record-meta">Java Runner · Run 1 · 人工批准 · 本机运行记录</div>
        <pre class="terminal-output"><span class="terminal-command">python train.py</span>
{{ stdout }}</pre>
        <button @click="emit('inspect', 'logs')">查看完整运行摘要 ↗</button>
      </div>
    </details>
  </div>
</template>

<style scoped>
.turn-activity {
  display: grid;
  gap: 9px;
  margin: 15px 0 19px;
  font-size: 12px;
  color: #657d9f;
}
details {
  border: 1px solid #e0e8f5;
  background: #fff;
  border-radius: 9px;
  overflow: hidden;
}
summary {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  list-style: none;
  padding: 11px 13px;
  position: relative;
}
summary::-webkit-details-marker {
  display: none;
}
summary:after {
  content: '⌄';
  margin-left: 5px;
  color: #9bacc8;
}
details[open] > summary:after {
  content: '⌃';
}
summary strong {
  font-size: 11px;
  font-weight: 600;
  color: #58739d;
}
summary small {
  margin-left: auto;
  font-size: 9px;
  color: #91a1bb;
}
summary code {
  font-size: 11px;
  color: #5272a9;
  overflow-wrap: anywhere;
}
.activity-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: #edf2ff;
  color: #7394d5;
  flex-shrink: 0;
}
.activity-icon.tool {
  background: #e9f2ff;
  color: #5e88d7;
}
.activity-icon.code {
  background: #f0ecff;
  color: #8c77c6;
}
.activity-icon.terminal {
  background: #e9eff8;
  color: #546a91;
}
.analysis-summary {
  background: #f4f7fd;
}
.analysis-summary p {
  padding: 0 15px 13px;
  margin: 0;
  line-height: 1.9;
  font-size: 12px;
}
.record-body {
  padding: 0 13px 13px;
}
.record-meta {
  border-top: 1px solid #eaf0f8;
  padding-top: 11px;
  color: #95a6c0;
  font-size: 9px;
  margin-bottom: 10px;
}
.record-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.record-columns small {
  font-size: 9px;
  color: #8fa3c2;
}
pre {
  font-family: Consolas, monospace;
  font-size: 10px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #f4f7fd;
  border-radius: 6px;
  padding: 11px;
  margin: 7px 0;
  color: #6b86ad;
}
.record-success {
  margin-left: auto;
  color: #5a91aa;
  font-size: 9px;
  background: #eef6fc;
  border-radius: 4px;
  padding: 3px 6px;
}
.record-body button {
  border: 0;
  background: transparent;
  padding: 5px 0;
  color: #6b87c4;
  font-size: 10px;
  cursor: pointer;
}
.source-code {
  max-height: 260px;
  overflow: auto;
  background: #f7f9fd;
  font-size: 11px;
  color: #5876a6;
}
.terminal-output {
  background: #1c2a42;
  color: #c6d7f5;
  font-size: 11px;
  padding: 15px;
  margin-top: 12px;
}
.terminal-command {
  color: #8baef7;
}
summary:focus-visible,
button:focus-visible {
  outline: 2px solid #668bf0;
  outline-offset: -2px;
}
@media (max-width: 600px) {
  .record-columns {
    grid-template-columns: 1fr;
  }
  .record-meta {
    line-height: 1.8;
  }
}
</style>
