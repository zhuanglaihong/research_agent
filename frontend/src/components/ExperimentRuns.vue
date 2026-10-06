<template>
  <a-card title="本地实验运行" :bordered="false" class="run-card">
    <a-alert type="warning" show-icon message="仅在本机启用：运行生成代码前请先检查脚本。当前只有 Python，进程有 5 分钟与 1 MB 日志限制，但没有容器或操作系统沙箱。" />
    <p v-if="!capabilities?.enabled" class="help">此功能默认关闭。在本地 .env 设置 RESEARCH_RUNNER_ENABLED=true 并重启后端后可使用。</p>
    <template v-else>
      <div class="controls">
        <a-select v-model:value="script" placeholder="选择生成的 Python 脚本" :options="scriptOptions" style="min-width:240px" />
        <a-button type="primary" :disabled="!script" :loading="busy" @click="prepareRun">准备实验运行</a-button>
      </div>
      <p class="help">这里只运行选中的任务产物；执行命令由后端固定为 Python + 脚本路径，不经过 shell。</p>
      <a-empty v-if="!runs.length" description="尚无实验运行记录" />
      <div v-for="run in runs" :key="run.id" class="run-item">
        <div class="run-heading"><strong>#{{ run.id }} · {{ run.scriptPath }}</strong><a-tag :color="color(run.status)">{{ label(run.status) }}</a-tag></div>
        <code>{{ capabilities.pythonExecutable }} {{ run.scriptPath }}</code>
        <p class="help">工作目录：{{ run.workingDirectory }}</p>
        <div class="actions">
          <a-popconfirm v-if="run.status === 'WAITING_APPROVAL'" title="已检查脚本，确认在本机运行？" @confirm="approve(run.id)">
            <a-button type="primary" size="small" :loading="busy">确认运行</a-button>
          </a-popconfirm>
          <a-button v-if="['WAITING_APPROVAL','QUEUED','RUNNING'].includes(run.status)" size="small" @click="cancel(run.id)">取消</a-button>
          <a-button size="small" @click="loadLogs(run.id)">查看日志</a-button>
          <a-button v-if="['SUCCEEDED','FAILED'].includes(run.status)" size="small" @click="draftAnalysis(run.id)">草拟结果分析任务</a-button>
          <span v-if="run.exitCode !== null" class="help">退出码 {{ run.exitCode }}</span>
        </div>
        <div v-if="selectedLogId === run.id" class="log-grid">
          <div><b>stdout</b><pre>{{ logs.stdout || '暂无输出' }}</pre></div>
          <div><b>stderr</b><pre>{{ logs.stderr || '暂无错误输出' }}</pre></div>
        </div>
      </div>
    </template>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { approveRun, cancelRun, createRun, getRunCapabilities, getRunLogs, listRuns, type ResearchRun, type RunCapabilities } from '@/api/researchRun'

const props = defineProps<{ taskId: string; files: string[] }>()
const emit = defineEmits<{ analyze: [content: string] }>()
const capabilities = ref<RunCapabilities>()
const runs = ref<ResearchRun[]>([])
const script = ref<string>()
const selectedLogId = ref<string>()
const logs = ref({ stdout: '', stderr: '' })
const busy = ref(false)
const scriptOptions = computed(() => props.files.filter(file => file.endsWith('.py') && !file.startsWith('.research_agent/')).map(value => ({ label: value, value })))
const labels: Record<string,string> = { WAITING_APPROVAL:'待确认', QUEUED:'排队中', RUNNING:'运行中', SUCCEEDED:'成功', FAILED:'失败', CANCELED:'已取消', INTERRUPTED:'已中断' }
const label = (status: string) => labels[status] || status
const color = (status: string) => status === 'SUCCEEDED' ? 'green' : status === 'FAILED' ? 'red' : status === 'RUNNING' ? 'blue' : 'default'
let timer: ReturnType<typeof setInterval> | undefined

const refresh = async () => {
  const response = await listRuns(props.taskId)
  if (response.data.code !== 0) throw new Error(response.data.message)
  runs.value = response.data.data || []
  if (selectedLogId.value && runs.value.some(run => run.id === selectedLogId.value && run.status === 'RUNNING')) await loadLogs(selectedLogId.value)
}
const prepareRun = async () => {
  if (!script.value) return
  busy.value = true
  try {
    const response = await createRun(props.taskId, script.value)
    if (response.data.code !== 0) throw new Error(response.data.message)
    await refresh()
    message.success('实验已创建，请检查命令并确认运行')
  } catch (error) { message.error(error instanceof Error ? error.message : '创建实验失败') }
  finally { busy.value = false }
}
const approve = async (id: string) => {
  busy.value = true
  try {
    const response = await approveRun(id)
    if (response.data.code !== 0) throw new Error(response.data.message)
    await refresh()
  } catch (error) { message.error(error instanceof Error ? error.message : '运行失败') }
  finally { busy.value = false }
}
const cancel = async (id: string) => {
  try {
    const response = await cancelRun(id)
    if (response.data.code !== 0) throw new Error(response.data.message)
    await refresh()
  } catch (error) { message.error(error instanceof Error ? error.message : '取消失败') }
}
const loadLogs = async (id: string) => {
  try {
    const response = await getRunLogs(id)
    if (response.data.code !== 0) throw new Error(response.data.message)
    selectedLogId.value = id
    logs.value = response.data.data
  } catch (error) { message.error(error instanceof Error ? error.message : '读取日志失败') }
}
const draftAnalysis = async (id: string) => {
  await loadLogs(id)
  emit('analyze', `实验 #${id}，任务 #${props.taskId}\nstdout:\n${logs.value.stdout.slice(-12000)}\nstderr:\n${logs.value.stderr.slice(-4000)}`)
}
onMounted(async () => {
  try {
    const response = await getRunCapabilities()
    capabilities.value = response.data.data
    await refresh()
    timer = setInterval(() => { if (runs.value.some(run => ['QUEUED','RUNNING'].includes(run.status))) refresh().catch(() => {}) }, 1000)
  } catch { message.error('实验运行状态加载失败') }
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.run-card { margin-top:20px; }
.run-card :deep(.ant-alert) { margin-bottom:16px; }
.controls, .actions, .run-heading { display:flex; gap:12px; align-items:center; flex-wrap:wrap; }
.controls { margin:18px 0 8px; }
.help { color:#667085; font-size:13px; margin:10px 0; overflow-wrap:anywhere; }
.run-item { border-top:1px solid #eee; padding:16px 0; }
.run-heading { justify-content:space-between; margin-bottom:10px; }
.run-item code { background:#f5f8ff; display:block; padding:10px; overflow-wrap:anywhere; }
.log-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:14px; }
.log-grid pre { white-space:pre-wrap; overflow-wrap:anywhere; max-height:260px; overflow:auto; background:#f8fafc; padding:12px; }
@media(max-width:800px) { .log-grid { grid-template-columns:1fr; } }
</style>
