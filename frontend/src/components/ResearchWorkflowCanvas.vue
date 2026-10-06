<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  scenarioId: string
  steps: string[]
  tools: string[]
  completed: number
  phase: string
}>()
const emit = defineEmits<{ inspect: [target: string] }>()
type Position = { x: number; y: number }
const width = 240
const height = 94
const positions = ref<Position[]>([])
const selected = ref(0)
const scale = ref(0.6)
const offset = ref({ x: 0, y: 0 })
const stage = ref<HTMLElement>()
const dragging = ref(false)
let observer: ResizeObserver | undefined
let gesture:
  | { pointer: number; node: number | null; x: number; y: number; origin: Position }
  | undefined
const key = () => 'research-agent:demo-layout:v1:' + props.scenarioId
function defaults() {
  return props.steps.map((_, i) => ({ x: i % 2 === 0 ? 32 : 330, y: 32 + Math.floor(i / 2) * 160 }))
}
function load() {
  positions.value = defaults()
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(key()) ?? 'null')
    if (
      Array.isArray(saved) &&
      saved.length === props.steps.length &&
      saved.every(
        (p) =>
          typeof p === 'object' &&
          p !== null &&
          Number.isFinite(p.x) &&
          Number.isFinite(p.y) &&
          p.x >= 0 &&
          p.x <= 1800 &&
          p.y >= 0 &&
          p.y <= 1800,
      )
    ) {
      positions.value = saved
    }
  } catch {
    /* Storage may be disabled; the canvas remains usable. */
  }
  selected.value = 0
  nextTick(fit)
}
function save() {
  try {
    localStorage.setItem(key(), JSON.stringify(positions.value))
  } catch {
    /* Optional local persistence. */
  }
}
function fit() {
  if (!stage.value || !positions.value.length) return
  const minX = Math.min(...positions.value.map((p) => p.x))
  const minY = Math.min(...positions.value.map((p) => p.y))
  const maxX = Math.max(...positions.value.map((p) => p.x + width))
  const maxY = Math.max(...positions.value.map((p) => p.y + height))
  scale.value = Math.min(
    1.15,
    Math.max(
      0.15,
      Math.min(
        (stage.value.clientWidth - 32) / (maxX - minX),
        (stage.value.clientHeight - 40) / (maxY - minY),
      ),
    ),
  )
  offset.value = {
    x: (stage.value.clientWidth - (maxX - minX) * scale.value) / 2 - minX * scale.value,
    y: 24 - minY * scale.value,
  }
}
function zoom(factor: number) {
  if (!stage.value) return
  const next = Math.min(1.6, Math.max(0.15, scale.value * factor))
  const cx = stage.value.clientWidth / 2
  const cy = stage.value.clientHeight / 2
  offset.value = {
    x: cx - ((cx - offset.value.x) * next) / scale.value,
    y: cy - ((cy - offset.value.y) * next) / scale.value,
  }
  scale.value = next
}
function start(event: PointerEvent, index: number | null) {
  if (event.button !== 0 || !stage.value) return
  if (index !== null) selected.value = index
  gesture = {
    pointer: event.pointerId,
    node: index,
    x: event.clientX,
    y: event.clientY,
    origin: { ...(index === null ? offset.value : positions.value[index]!) },
  }
  dragging.value = true
  stage.value.setPointerCapture(event.pointerId)
}
function move(event: PointerEvent) {
  if (!gesture || gesture.pointer !== event.pointerId) return
  const divisor = gesture.node === null ? 1 : scale.value
  const point = {
    x: gesture.origin.x + (event.clientX - gesture.x) / divisor,
    y: gesture.origin.y + (event.clientY - gesture.y) / divisor,
  }
  if (gesture.node === null) offset.value = point
  else
    positions.value[gesture.node] = {
      x: Math.min(1800, Math.max(0, point.x)),
      y: Math.min(1800, Math.max(0, point.y)),
    }
}
function end(event?: PointerEvent) {
  if (!gesture || (event && gesture.pointer !== event.pointerId)) return
  if (gesture.node !== null) save()
  if (stage.value?.hasPointerCapture(gesture.pointer))
    stage.value.releasePointerCapture(gesture.pointer)
  gesture = undefined
  dragging.value = false
}
function nudge(event: KeyboardEvent, index: number) {
  const delta: Record<string, Position> = {
    ArrowLeft: { x: -12, y: 0 },
    ArrowRight: { x: 12, y: 0 },
    ArrowUp: { x: 0, y: -12 },
    ArrowDown: { x: 0, y: 12 },
  }
  const change = delta[event.key]
  if (!change) return
  event.preventDefault()
  const current = positions.value[index]!
  positions.value[index] = {
    x: Math.max(0, Math.min(1800, current.x + change.x)),
    y: Math.max(0, Math.min(1800, current.y + change.y)),
  }
  save()
}
function reset() {
  positions.value = defaults()
  save()
  fit()
}
function status(index: number) {
  if (index < props.completed) return 'complete'
  if (index === props.completed && props.phase === 'running') return 'running'
  if (index === props.completed && ['approval', 'runApproval'].includes(props.phase))
    return 'approval'
  return 'waiting'
}
const statusLabels: Record<string, string> = {
  complete: '回放已展示',
  running: '回放中',
  approval: '等待确认',
  waiting: '等待展示',
}
const edges = computed(() =>
  positions.value.slice(0, -1).map((p, i) => {
    const next = positions.value[i + 1]!
    const rightward = next.x >= p.x
    const from = { x: p.x + (rightward ? width : 0), y: p.y + height / 2 }
    const to = { x: next.x + (rightward ? 0 : width), y: next.y + height / 2 }
    const bend = Math.max(45, Math.abs(to.x - from.x) / 2)
    return {
      id: i,
      path: `M${from.x},${from.y} C${from.x + (rightward ? bend : -bend)},${from.y} ${to.x + (rightward ? -bend : bend)},${to.y} ${to.x},${to.y}`,
    }
  }),
)
const target = computed(() => {
  const tool = props.tools[selected.value] ?? ''
  return /Runner|Experiment/.test(tool) ? 'logs' : /分析|统计|核对/.test(tool) ? 'results' : 'files'
})
watch(
  () => props.scenarioId,
  () => {
    end()
    load()
  },
  { immediate: true },
)
onMounted(() => {
  observer = new ResizeObserver(fit)
  if (stage.value) observer.observe(stage.value)
  fit()
})
onBeforeUnmount(() => {
  end()
  observer?.disconnect()
})
</script>

<template>
  <section class="workflow-editor">
    <div class="canvas-toolbar">
      <span>流程画布 <small>布局可编辑</small></span>
      <div>
        <button aria-label="缩小流程画布" @click="zoom(0.8)">−</button
        ><span>{{ Math.round(scale * 100) }}%</span
        ><button aria-label="放大流程画布" @click="zoom(1.25)">＋</button
        ><button @click="fit">适应</button><button @click="reset">重置布局</button>
      </div>
    </div>
    <div
      ref="stage"
      class="canvas-stage"
      :class="{ dragging }"
      aria-label="科研流程画布"
      @pointerdown.self="start($event, null)"
      @pointermove="move"
      @pointerup="end"
      @pointercancel="end"
      @lostpointercapture="end"
    >
      <div
        class="canvas-world"
        :style="{ transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})` }"
      >
        <svg class="connections" width="2300" height="2300" aria-hidden="true">
          <path
            v-for="edge in edges"
            :key="edge.id"
            :d="edge.path"
            :class="{ passed: edge.id < completed - 1 }"
          />
        </svg>
        <button
          v-for="(step, i) in steps"
          :key="i"
          class="flow-node"
          :class="[status(i), { selected: selected === i }]"
          :style="{ left: positions[i]?.x + 'px', top: positions[i]?.y + 'px' }"
          :aria-label="'节点 ' + (i + 1) + '：' + step + '，' + statusLabels[status(i)]"
          :aria-pressed="selected === i"
          @pointerdown.stop="start($event, i)"
          @click="selected = i"
          @keydown="nudge($event, i)"
        >
          <i class="port input"></i>
          <div class="node-title">
            <span class="node-icon">{{ i < completed ? '✓' : String(i + 1).padStart(2, '0') }}</span
            ><strong>{{ step }}</strong
            ><span class="node-status"></span>
          </div>
          <code>{{ tools[i] }}</code
          ><small>{{ statusLabels[status(i)] }}</small
          ><i class="port output"></i>
        </button>
      </div>
      <div class="canvas-legend">
        <span class="green"></span>已展示 <span class="orange"></span>等待确认
        <span class="gray"></span>未展示
      </div>
    </div>
    <div class="node-inspector">
      <div>
        <small>选中节点 {{ selected + 1 }}</small
        ><strong>{{ steps[selected] }}</strong
        ><span>{{ statusLabels[status(selected)] }}</span>
      </div>
      <code>{{ tools[selected] }}</code
      ><button @click="emit('inspect', target)">
        查看相关{{ target === 'logs' ? '日志' : target === 'results' ? '结果' : '产物' }} ↗
      </button>
    </div>
    <p class="canvas-hint">
      拖动节点调整布局，拖动空白平移；方向键也可移动选中节点。布局保存在当前浏览器。连线表示预置步骤顺序，拖动不会修改执行逻辑。
    </p>
  </section>
</template>

<style scoped>
.workflow-editor {
  color: #344a70;
  font-size: 12px;
}
.canvas-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
}
.canvas-toolbar > span {
  font-weight: 600;
}
.canvas-toolbar small {
  font-weight: 400;
  color: #8496b4;
  font-size: 10px;
  margin-left: 5px;
}
.canvas-toolbar > div {
  display: flex;
  align-items: center;
  gap: 4px;
}
.canvas-toolbar button {
  background: white;
  border: 1px solid #dfe7f5;
  border-radius: 5px;
  padding: 5px 7px;
  cursor: pointer;
  font-size: 10px;
  color: #6b83ac;
}
.canvas-toolbar > div > span {
  font-size: 10px;
  min-width: 35px;
  text-align: center;
  color: #8b9cb6;
}
.canvas-stage {
  position: relative;
  height: 430px;
  overflow: hidden;
  border: 1px solid #dce5f5;
  border-radius: 10px;
  background-color: #f2f6fe;
  background-image: radial-gradient(#ccd9f3 1px, transparent 1px);
  background-size: 16px 16px;
  touch-action: none;
  cursor: grab;
  isolation: isolate;
}
.canvas-stage.dragging {
  cursor: grabbing;
}
.canvas-world {
  position: absolute;
  left: 0;
  top: 0;
  transform-origin: 0 0;
  pointer-events: none;
}
.connections {
  position: absolute;
  left: 0;
  top: 0;
  overflow: visible;
  pointer-events: none;
}
.connections path {
  fill: none;
  stroke: #acbddf;
  stroke-width: 2;
}
.connections path.passed {
  stroke: #567cf0;
}
.flow-node {
  position: absolute;
  pointer-events: auto;
  width: 240px;
  height: 94px;
  text-align: left;
  border: 1px solid #d5e1f5;
  border-radius: 10px;
  background: #fff;
  padding: 13px 15px;
  box-shadow: 0 3px 8px #30422b09;
  color: #34517e;
  cursor: grab;
  user-select: none;
  touch-action: none;
  font: inherit;
}
.flow-node:active {
  cursor: grabbing;
}
.flow-node.selected {
  outline: 2px solid #5275f1;
  outline-offset: 3px;
}
.flow-node.complete {
  border-color: #9db6f2;
}
.flow-node.running {
  border-color: #5275f1;
  background: #f3f6ff;
}
.node-title {
  display: flex;
  align-items: center;
  gap: 9px;
}
.node-icon {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  border-radius: 6px;
  background: #edf2ff;
  color: #7f95be;
  font-size: 10px;
}
.node-title strong {
  font-size: 12px;
  font-weight: 600;
}
.node-status {
  margin-left: auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0cde3;
}
.complete .node-status {
  background: #567cf0;
}
.approval .node-status,
.running .node-status {
  background: #5275f1;
}
.complete .node-icon {
  background: #e7efff;
  color: #5076d5;
}
.flow-node code {
  display: block;
  margin: 8px 0 4px;
  font-size: 9px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #849abd;
}
.flow-node small {
  font-size: 9px;
  color: #90a3c2;
}
.port {
  position: absolute;
  width: 8px;
  height: 8px;
  border: 2px solid #b1c4e6;
  background: white;
  border-radius: 50%;
  top: calc(50% - 4px);
}
.port.input {
  left: -5px;
}
.port.output {
  right: -5px;
}
.canvas-legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 9px;
  color: #8299bf;
  pointer-events: none;
  background: #f2f6fee8;
  padding: 6px;
  border-radius: 5px;
}
.canvas-legend > span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-left: 5px;
}
.green {
  background: #567cf0;
}
.orange {
  background: #5275f1;
}
.gray {
  background: #bfcee8;
}
.node-inspector {
  margin-top: 14px;
  border: 1px solid #dfe7f5;
  border-radius: 9px;
  padding: 14px;
  background: white;
}
.node-inspector > div {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
}
.node-inspector small {
  width: 100%;
  font-size: 9px;
  color: #98a9c5;
}
.node-inspector strong {
  font-size: 12px;
  font-weight: 600;
}
.node-inspector span {
  margin-left: auto;
  font-size: 9px;
  color: #879dc0;
}
.node-inspector code {
  display: block;
  margin: 12px 0;
  color: #8099c2;
  font-size: 10px;
  overflow-wrap: anywhere;
}
.node-inspector button {
  background: #edf3ff;
  border: 0;
  border-radius: 6px;
  padding: 8px 11px;
  font-size: 10px;
  color: #5b7cc2;
  cursor: pointer;
}
.canvas-hint {
  font-size: 10px !important;
  line-height: 1.9;
  color: #91a4c5;
  margin: 12px 0;
}
button:focus-visible {
  outline: 2px solid #5275f1;
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .canvas-stage {
    height: 365px;
  }
}
</style>
