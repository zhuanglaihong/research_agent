<template>
  <main class="workspace">
    <header class="workspace-header">
      <div><RouterLink to="/">← 科研项目</RouterLink><h1>{{ project?.name || '科研工作台' }}</h1>
        <p>{{ project?.description || '用自然语言描述科研方法、代码修改或实验脚本需求。' }}</p></div>
      <a-tag color="blue">{{ project?.defaultLanguage }}</a-tag>
    </header>
    <a-card title="项目记忆与文献笔记" :bordered="false" class="context-panel">
      <a-tabs>
        <a-tab-pane key="memory" tab="文件记忆">
          <p class="context-help">写入研究目标、数据约束和已确认的发现；保存到本地项目文件，后续任务会读取。</p>
          <a-textarea v-model:value="memoryText" :rows="4" :maxlength="16000" placeholder="例如：数据集路径由用户提供；评价指标使用 F1。" />
          <a-button type="primary" :loading="savingMemory" class="context-action" @click="saveMemory">保存记忆</a-button>
        </a-tab-pane>
        <a-tab-pane key="knowledge" :tab="`文献笔记 (${knowledgeDocs.length})`">
          <p class="context-help">项目资料按片段索引并用本地 BM25 排序；任务检索会附带来源和片段编号。无需模型密钥或外部数据库。</p>
          <div class="knowledge-input">
            <a-input v-model:value="noteSource" :maxlength="240" placeholder="来源：论文标题、URL 或笔记名称" />
            <a-textarea v-model:value="noteContent" :rows="4" :maxlength="20000" placeholder="粘贴摘要、方法要点或实验设置" />
            <a-button type="primary" :loading="addingNote" @click="addNote">保存笔记</a-button>
          </div>
          <div class="knowledge-search">
            <a-input-search v-model:value="searchQuery" placeholder="检索已保存的笔记" enter-button="检索" @search="runSearch" />
            <ul v-if="searchHits.length" class="knowledge-hits"><li v-for="hit in searchHits" :key="hit.chunkId"><strong>{{ hit.source }} · 文档 #{{ hit.id }} / 片段 #{{ hit.chunkId }}</strong><p>{{ hit.excerpt }}</p></li></ul>
            <a-empty v-else-if="searched" description="没有匹配的笔记" />
          </div>
          <h3>已收录资料</h3>
          <ul class="knowledge-hits"><li v-for="document in knowledgeDocs" :key="document.id">
            <strong>{{ document.source }}</strong>
            <a-button size="small" danger @click="removeNote(document)">删除</a-button>
          </li></ul>
        </a-tab-pane>
        <a-tab-pane key="papers" :tab="`每日论文 (${papers.length})`">
          <div class="paper-import">
            <h3>论文 PDF → 代码任务</h3>
            <p class="context-help">上传可复制文本的 PDF（≤10 MB、≤100 页），先审阅抽取的方法片段，再创建待确认的代码任务。扫描版 PDF 需先 OCR。</p>
            <a-input v-model:value="pdfTitle" placeholder="论文标题" :maxlength="500" />
            <input type="file" accept="application/pdf,.pdf" @change="selectPdf" />
            <a-button :loading="uploadingPdf" @click="importPdf">导入 PDF</a-button>
            <ul class="paper-list"><li v-for="method in paperMethods" :key="method.id">
              <strong>{{ method.title }}</strong><small>已抽取 {{ method.textLength }} 字符 · {{ method.source }}</small>
              <pre class="method-brief">{{ method.methodBrief }}</pre>
              <a-button size="small" @click="createMethodTask(method)">创建待确认代码任务</a-button>
            </li></ul>
          </div>
          <p class="context-help">填写英文研究主题。可手动同步最新 arXiv 摘要；打开每日同步后，本地后端每天 08:00 检查一次。新摘要会加入当前项目的可检索笔记。</p>
          <div class="paper-settings">
            <a-input v-model:value="paperTopic" placeholder="例如：remote sensing segmentation" :maxlength="100" />
            <a-checkbox v-model:checked="paperEnabled">每天 08:00 自动同步</a-checkbox>
            <a-button :loading="savingPaper" @click="savePaperSettings">保存主题</a-button>
            <a-button type="primary" :loading="syncingPaper" @click="syncPaperFeed">立即同步</a-button>
          </div>
          <p v-if="paperSubscription?.lastChecked" class="context-help">上次检查：{{ new Date(paperSubscription.lastChecked).toLocaleString() }}<span v-if="paperSubscription.lastError"> · {{ paperSubscription.lastError }}</span></p>
          <a-empty v-if="papers.length === 0" description="还没有同步到论文" />
          <ul v-else class="paper-list"><li v-for="paper in papers" :key="paper.id">
            <a :href="paper.url" target="_blank" rel="noopener noreferrer">{{ paper.title }}</a>
            <small>{{ paper.publishedAt ? new Date(paper.publishedAt).toLocaleDateString() : '' }} · {{ paper.arxivId }}</small>
            <p>{{ paper.abstractText }}</p>
            <a-button size="small" @click="draftPaperTask(paper)">根据摘要草拟代码任务</a-button>
          </li></ul>
          <p class="context-help">论文原始摘要保留作者信息与来源，可作为项目检索与代码任务的上下文。</p>
        </a-tab-pane>
      </a-tabs>
    </a-card>
    <div class="workspace-grid">
      <aside class="task-sidebar">
        <h3>科研任务</h3>
        <a-empty v-if="tasks.length === 0" description="先下达第一个任务" />
        <button v-for="task in tasks" :key="task.id" class="task-item" :class="{ selected: selected?.id === task.id }" @click="selectTask(task)">
          <strong>{{ task.title }}</strong><span>{{ statusLabel(task.status) }}</span>
        </button>
      </aside>
      <section class="task-main">
        <a-card title="下达科研任务" :bordered="false">
          <a-textarea v-model:value="prompt" :rows="4" :maxlength="20000" placeholder="例如：实现一份支持三个随机种子的训练脚本，记录 loss 和 accuracy，添加早停，并给出依赖及运行说明。也可以粘贴论文方法描述。" />
          <div class="composer-actions"><span>生成代码前需要确认计划</span><a-button type="primary" :loading="submitting" @click="submitTask">创建任务</a-button></div>
        </a-card>
        <a-card v-if="selected" :title="selected.title" :bordered="false" class="task-panel">
          <template #extra><a-tag :color="selected.status === 'SUCCEEDED' ? 'green' : 'blue'">{{ statusLabel(selected.status) }}</a-tag></template>
          <a-alert v-if="selected.provider === 'demo'" type="warning" show-icon message="固定样例演示模式：未调用大模型，不能视为论文复现或真实模型训练。" class="notice" />
          <a-alert v-if="selected.errorMessage" type="error" :message="selected.errorMessage" class="notice" />
          <a-steps :current="stageIndex" :status="stageStatus" size="small" class="stage-steps">
            <a-step title="确认计划" /><a-step title="等待执行" /><a-step title="检索资料" /><a-step title="生成成果" />
          </a-steps>
          <p class="context-help">阶段视图来自持久任务事件，可刷新恢复；不代表模型输出的精确完成百分比。</p>
          <div v-if="selected.status === 'WAITING_APPROVAL'" class="plan-box">
            <h3>执行计划</h3><ol><li v-for="step in planSteps" :key="step">{{ step }}</li></ol>
            <p>代码保存在独立任务目录，完成后可查看与下载。</p>
            <a-button type="primary" :loading="acting" @click="approve">确认并开始生成</a-button>
          </div>
          <div class="task-actions">
            <a-button v-if="['WAITING_APPROVAL','QUEUED','RUNNING'].includes(selected.status)" :loading="acting" @click="cancel">取消任务</a-button>
            <a-button v-if="files.length" :href="`${API_BASE_URL}/tasks/${selected.id}/download`">下载代码 ZIP</a-button>
            <span v-if="selected.status === 'RUNNING'" class="connection-state">{{ connection }}</span>
          </div>
          <a-tabs v-model:active-key="activeTab">
            <a-tab-pane key="progress" tab="执行进度">
              <ul class="event-list"><li v-for="event in events" :key="event.id"><time>{{ new Date(event.timestamp).toLocaleTimeString() }}</time><div class="event-content"><span>{{ eventText(event) }}</span><details v-if="event.type === 'TOOL' || event.type === 'RETRIEVAL'" class="event-details"><summary>{{ event.type === 'TOOL' ? '查看工具调用记录' : '查看检索记录' }}</summary><pre>{{ eventDetails(event) }}</pre><small v-if="event.type === 'TOOL'">显示任务事件中保存的调用元数据。</small></details></div></li></ul>
              <a-empty v-if="events.length === 0" description="尚无进度事件" />
            </a-tab-pane>
            <a-tab-pane key="result" tab="助手总结"><MarkdownRenderer v-if="selected.resultText" :content="selected.resultText" /><a-empty v-else description="任务完成后展示总结" /></a-tab-pane>
            <a-tab-pane key="files" :tab="`代码产物 (${files.length})`">
              <div class="files-panel"><nav><button v-for="file in files" :key="file" :class="{ selected: file === selectedFile }" @click="openFile(file)">{{ file }}</button></nav><pre v-if="selectedFile"><code>{{ fileContent }}</code></pre><a-empty v-else description="选择文件查看代码" /></div>
            </a-tab-pane>
          </a-tabs>
        </a-card>
        <ExperimentRuns v-if="selected?.status === 'SUCCEEDED'" :key="selected.id" :task-id="selected.id" :files="files" @analyze="draftAnalysisTask" />
        <a-empty v-if="!selected" description="创建或选择任务，查看计划、进度和代码产物" class="welcome" />
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ExperimentRuns from '@/components/ExperimentRuns.vue'
import { getResearchProject, type ResearchProject } from '@/api/researchProjectController'
import { listTasks, createTask, getTask, approveTask, cancelTask, getTaskEvents, getTaskFiles, getTaskFile, type ResearchTask, type TaskEvent } from '@/api/researchTaskController'
import { API_BASE_URL } from '@/config/env'
import { getProjectMemory, saveProjectMemory, listKnowledge, addKnowledge, searchKnowledge, deleteKnowledge, type KnowledgeDocument, type KnowledgeHit } from '@/api/researchKnowledge'
import { getPaperSubscription, savePaperSubscription, listPapers, syncPapers, listPaperMethods, uploadPaperMethod, createPaperMethodTask, type PaperItem, type PaperSubscription, type PaperMethod } from '@/api/researchPaper'

const route = useRoute()
const projectId = String(route.params.id)
const project = ref<ResearchProject>()
const tasks = ref<ResearchTask[]>([])
const selected = ref<ResearchTask>()
const events = ref<TaskEvent[]>([])
const files = ref<string[]>([])
const selectedFile = ref('')
const fileContent = ref('')
const prompt = ref('')
const submitting = ref(false)
const acting = ref(false)
const activeTab = ref('progress')
const connection = ref('正在连接进度流')
const memoryText = ref('')
const knowledgeDocs = ref<KnowledgeDocument[]>([])
const noteSource = ref('')
const noteContent = ref('')
const searchQuery = ref('')
const searchHits = ref<KnowledgeHit[]>([])
const searched = ref(false)
const savingMemory = ref(false)
const addingNote = ref(false)
const paperTopic = ref('')
const paperEnabled = ref(false)
const paperSubscription = ref<PaperSubscription>()
const papers = ref<PaperItem[]>([])
const paperMethods = ref<PaperMethod[]>([])
const pdfTitle = ref('')
const pdfFile = ref<File>()
const uploadingPdf = ref(false)
const savingPaper = ref(false)
const syncingPaper = ref(false)
let stream: EventSource | undefined
const labels: Record<string,string> = { WAITING_APPROVAL:'待确认计划', QUEUED:'排队中', RUNNING:'生成中', SUCCEEDED:'已完成', FAILED:'失败', CANCELED:'已取消', INTERRUPTED:'已中断' }
const statusLabel = (status: string) => labels[status] || status
const stageIndex = computed(() => {
  if (!selected.value || selected.value.status === 'WAITING_APPROVAL') return 0
  if (selected.value.status === 'QUEUED') return 1
  if (selected.value.status === 'SUCCEEDED') return 4
  return events.value.some(event => event.type === 'RETRIEVAL' || event.type === 'TOOL') ? 3 : 2
})
const stageStatus = computed(() => ['FAILED', 'CANCELED', 'INTERRUPTED'].includes(selected.value?.status || '') ? 'error' : 'process')
const planSteps = computed<string[]>(() => {
  try { return JSON.parse(selected.value?.planJson || '{}').steps || [] } catch { return [] }
})
const eventText = (event: TaskEvent) => {
  try {
    const value = JSON.parse(event.payload)
    if (event.type === 'RETRIEVAL') return `检索到 ${value.count ?? 0} 条笔记${value.sources?.length ? ` · ${value.sources.join('、')}` : ''}`
    if (event.type === 'MEMORY') return value.message || '项目记忆已更新'
    if (value.name) return `${value.name}${value.path ? ` · ${value.path}` : ''}${value.count !== undefined ? ` · ${value.count} 个文件` : ''}`
    return value.message || statusLabel(value.status || event.type)
  } catch { return event.payload }
}
const eventDetails = (event: TaskEvent) => {
  try { return JSON.stringify(JSON.parse(event.payload), null, 2) }
  catch { return event.payload }
}
const loadTasks = async () => {
  const response = await listTasks(projectId)
  if (response.data.code !== 0) throw new Error(response.data.message)
  tasks.value = response.data.data || []
}
const refresh = async (id: string) => {
  const response = await getTask(id)
  if (response.data.code !== 0) throw new Error(response.data.message)
  if (selected.value?.id !== id) return
  selected.value = response.data.data
  tasks.value = tasks.value.map(task => task.id === id ? response.data.data : task)
  if (['SUCCEEDED','FAILED','CANCELED','INTERRUPTED'].includes(selected.value.status)) {
    stream?.close()
    files.value = (await getTaskFiles(id)).data.data || []
  }
}
const subscribe = (id: string) => {
  stream?.close()
  if (!['QUEUED','RUNNING'].includes(selected.value?.status || '')) return
  const cursor = events.value[events.value.length - 1]?.id || '0'
  const source = new EventSource(`${API_BASE_URL}/tasks/${id}/stream?after=${cursor}`)
  stream = source
  source.onopen = () => { connection.value = '已连接 · 可关闭页面，任务会继续生成' }
  source.onerror = () => { connection.value = '连接中断，正在重新连接' }
  source.addEventListener('task-event', (messageEvent) => {
    if (selected.value?.id !== id) return
    const event = JSON.parse((messageEvent as MessageEvent).data) as TaskEvent
    if (!events.value.some(item => item.id === event.id)) events.value.push(event)
    refresh(id).catch(() => { connection.value = '状态更新失败，请刷新页面' })
  })
  source.addEventListener('business-error', () => { source.close(); connection.value = '进度订阅失败，请刷新页面' })
}
const loadContext = async () => {
  const [saved, documents, subscription, paperList, methods] = await Promise.all([getProjectMemory(projectId), listKnowledge(projectId), getPaperSubscription(projectId), listPapers(projectId), listPaperMethods(projectId)])
  if (saved.data.code !== 0 || documents.data.code !== 0 || subscription.data.code !== 0 || paperList.data.code !== 0) throw new Error('无法加载项目记忆、笔记或论文')
  memoryText.value = saved.data.data || ''
  knowledgeDocs.value = documents.data.data || []
  paperSubscription.value = subscription.data.data
  paperTopic.value = subscription.data.data?.topic || ''
  paperEnabled.value = subscription.data.data?.enabled || false
  papers.value = paperList.data.data || []
  paperMethods.value = methods.data.data || []
}
const selectPdf = (event: Event) => { pdfFile.value = (event.target as HTMLInputElement).files?.[0] }
const importPdf = async () => {
  if (!pdfTitle.value.trim() || !pdfFile.value) { message.warning('请填写标题并选择 PDF'); return }
  uploadingPdf.value = true
  try {
    const result = await uploadPaperMethod(projectId, pdfTitle.value.trim(), pdfFile.value)
    if (result.data.code !== 0) throw new Error(result.data.message)
    paperMethods.value = (await listPaperMethods(projectId)).data.data || []
    knowledgeDocs.value = (await listKnowledge(projectId)).data.data || []
    pdfTitle.value = ''; pdfFile.value = undefined
    message.success('PDF 已导入，请核对方法片段')
  } catch (error) { message.error(error instanceof Error ? error.message : 'PDF 导入失败') }
  finally { uploadingPdf.value = false }
}
const createMethodTask = async (method: PaperMethod) => {
  try {
    const result = await createPaperMethodTask(projectId, method.id, '')
    if (result.data.code !== 0) throw new Error(result.data.message)
    await loadTasks()
    const task = tasks.value.find(item => item.id === result.data.data.id)
    if (task) await selectTask(task)
    message.info('代码任务已创建，请检查任务计划后确认生成')
  } catch (error) { message.error(error instanceof Error ? error.message : '创建任务失败') }
}
const savePaperSettings = async () => {
  if (!paperTopic.value.trim()) { message.warning('请填写英文研究主题'); return }
  savingPaper.value = true
  try {
    const response = await savePaperSubscription(projectId, paperTopic.value.trim(), paperEnabled.value)
    if (response.data.code !== 0) throw new Error(response.data.message)
    paperSubscription.value = response.data.data
    message.success('论文主题已保存')
  } catch (error) { message.error(error instanceof Error ? error.message : '保存失败') }
  finally { savingPaper.value = false }
}
const syncPaperFeed = async () => {
  if (!paperSubscription.value?.topic) { message.warning('请先保存论文主题'); return }
  syncingPaper.value = true
  try {
    const response = await syncPapers(projectId)
    if (response.data.code !== 0) throw new Error(response.data.message)
    const [subscription, paperList, documents] = await Promise.all([getPaperSubscription(projectId), listPapers(projectId), listKnowledge(projectId)])
    paperSubscription.value = subscription.data.data
    papers.value = paperList.data.data || []
    knowledgeDocs.value = documents.data.data || []
    message.success(`同步完成，新增 ${response.data.data.added} 篇`)
  } catch (error) { message.error(error instanceof Error ? error.message : '同步失败') }
  finally { syncingPaper.value = false }
}
const draftPaperTask = (paper: PaperItem) => {
  prompt.value = `请基于以下论文摘要，设计可运行的最小复现方案，并生成当前项目主要语言的代码、依赖说明、实验步骤和未验证假设。不要声称完整复现论文。\n\n论文：${paper.title}\n来源：${paper.url}\n摘要：${paper.abstractText}`.slice(0, 19000)
  message.info('已草拟任务，请检查需求后点击“创建任务”')
  document.querySelector('.task-main')?.scrollIntoView({ behavior: 'smooth' })
}
const draftAnalysisTask = (content: string) => {
  prompt.value = `请分析以下实验日志，指出可观察到的趋势、异常与下一步验证建议。生成一个读取日志并绘制 loss 曲线的脚本；不要编造尚未测量的指标。\n\n${content}`.slice(0, 19000)
  message.info('已草拟分析任务，请检查后创建')
  document.querySelector('.task-main')?.scrollIntoView({ behavior: 'smooth' })
}
const saveMemory = async () => {
  savingMemory.value = true
  try {
    const result = await saveProjectMemory(projectId, memoryText.value)
    if (result.data.code !== 0) throw new Error(result.data.message)
    message.success('项目记忆已保存')
  } catch (error) { message.error(error instanceof Error ? error.message : '保存失败') }
  finally { savingMemory.value = false }
}
const addNote = async () => {
  if (!noteSource.value.trim() || !noteContent.value.trim()) { message.warning('请填写来源和笔记内容'); return }
  addingNote.value = true
  try {
    const result = await addKnowledge(projectId, noteSource.value.trim(), noteContent.value.trim())
    if (result.data.code !== 0) throw new Error(result.data.message)
    knowledgeDocs.value.unshift(result.data.data)
    noteSource.value = ''; noteContent.value = ''
    message.success('文献笔记已保存')
  } catch (error) { message.error(error instanceof Error ? error.message : '保存失败') }
  finally { addingNote.value = false }
}
const removeNote = async (document: KnowledgeDocument) => {
  if (!window.confirm(`删除知识库资料“${document.source}”？`)) return
  try {
    const result = await deleteKnowledge(projectId, document.id)
    if (result.data.code !== 0) throw new Error(result.data.message)
    knowledgeDocs.value = knowledgeDocs.value.filter(item => item.id !== document.id)
    searchHits.value = searchHits.value.filter(item => item.id !== document.id)
    message.success('资料已删除')
  } catch (error) { message.error(error instanceof Error ? error.message : '删除失败') }
}
const runSearch = async () => {
  if (!searchQuery.value.trim()) return
  try {
    const result = await searchKnowledge(projectId, searchQuery.value.trim())
    if (result.data.code !== 0) throw new Error(result.data.message)
    searchHits.value = result.data.data || []
    searched.value = true
  } catch (error) { message.error(error instanceof Error ? error.message : '检索失败') }
}
const selectTask = async (task: ResearchTask) => {
  stream?.close()
  selected.value = task
  files.value = []; selectedFile.value = ''; fileContent.value = ''; events.value = []
  try {
    const [history, artifacts] = await Promise.all([getTaskEvents(task.id), getTaskFiles(task.id)])
    if (selected.value?.id !== task.id) return
    events.value = history.data.data || []
    files.value = artifacts.data.data || []
    await refresh(task.id)
    subscribe(task.id)
  } catch { message.error('加载任务详情失败') }
}
const submitTask = async () => {
  if (!prompt.value.trim()) { message.warning('请描述科研任务'); return }
  submitting.value = true
  try {
    const response = await createTask(projectId, prompt.value.trim())
    if (response.data.code !== 0) throw new Error(response.data.message)
    tasks.value.unshift(response.data.data)
    prompt.value = ''
    await selectTask(response.data.data)
  } catch (error) { message.error(error instanceof Error ? error.message : '创建任务失败') }
  finally { submitting.value = false }
}
const approve = async () => {
  if (!selected.value) return
  acting.value = true
  try {
    const response = await approveTask(selected.value.id)
    if (response.data.code !== 0) throw new Error(response.data.message)
    await selectTask(response.data.data)
  } catch (error) { message.error(error instanceof Error ? error.message : '提交失败') }
  finally { acting.value = false }
}
const cancel = async () => {
  if (!selected.value) return
  acting.value = true
  try {
    const response = await cancelTask(selected.value.id)
    if (response.data.code !== 0) throw new Error(response.data.message)
    await selectTask(response.data.data)
  } catch (error) { message.error(error instanceof Error ? error.message : '取消失败') }
  finally { acting.value = false }
}
const openFile = async (file: string) => {
  if (!selected.value) return
  try {
    const response = await getTaskFile(selected.value.id, file)
    if (response.data.code !== 0) throw new Error(response.data.message)
    selectedFile.value = file; fileContent.value = response.data.data
  } catch { message.error('读取产物失败') }
}
onMounted(async () => {
  try {
    const response = await getResearchProject(projectId)
    if (response.data.code !== 0) throw new Error(response.data.message)
    project.value = response.data.data
    await loadContext()
    await loadTasks()
    if (tasks.value[0]) await selectTask(tasks.value[0])
  } catch (error) { message.error(error instanceof Error ? error.message : '工作台加载失败') }
})
onUnmounted(() => stream?.close())
</script>

<style scoped>
.workspace { max-width:1400px; margin:auto; padding:28px 24px 48px; }
.workspace-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; }
.workspace-header h1 { margin:12px 0 6px; font-size:28px; }
.workspace-header p, .connection-state { color:#667085; }
.context-panel { margin-bottom: 20px; }
.context-help { color:#667085; font-size:13px; margin:10px 0 16px; }
.context-action { margin-top:12px; }
.knowledge-input { display:grid; gap:10px; }
.knowledge-input button { justify-self:start; }
.knowledge-search { margin-top:20px; max-width:800px; }
.paper-settings { display:flex; align-items:center; flex-wrap:wrap; gap:10px; }
.paper-settings :deep(.ant-input) { width:min(340px,100%); }
.paper-list { list-style:none; padding:0; margin:14px 0; }
.paper-list li { border-bottom:1px solid #eee; padding:12px 0; }
.paper-list a { font-weight:600; }
.paper-list small { display:block; color:#98a2b3; margin-top:4px; }
.paper-list p { color:#475467; max-height:7em; overflow:auto; }
.paper-import { border:1px solid #d6e4ff; border-radius:8px; padding:16px; margin-bottom:20px; }
.paper-import input[type=file] { display:block; margin:12px 0; }
.method-brief { white-space:pre-wrap; max-height:180px; overflow:auto; background:#f8fafc; padding:12px; }
.knowledge-hits { list-style:none; margin:12px 0 0; padding:0; }
.knowledge-hits li { border-bottom:1px solid #eee; padding:10px 0; }
.knowledge-hits p { color:#667085; margin:6px 0; }
.stage-steps { margin:16px 0; }
.workspace-grid { display:grid; grid-template-columns:260px minmax(0,1fr); gap:24px; }
.task-sidebar { background:#fff; padding:18px; border-radius:12px; align-self:start; }
.task-item { display:flex; flex-direction:column; gap:8px; width:100%; border:1px solid #eee; background:#fff; border-radius:8px; padding:12px; margin-bottom:10px; text-align:left; cursor:pointer; }
.task-item strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.task-item span { color:#667085; font-size:12px; }
.selected { border-color:#1677ff!important; background:#f0f7ff!important; }
.task-panel { margin-top:20px; }
.composer-actions, .task-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:16px; }
.composer-actions span { color:#667085; font-size:13px; }
.plan-box { border:1px solid #d6e4ff; background:#f5f8ff; padding:16px; border-radius:8px; }
.notice { margin-bottom:16px; }
.event-list { list-style:none; padding:0; }
.event-list li { display:flex; gap:16px; padding:12px 0; border-bottom:1px solid #f0f0f0; }
.event-list time { color:#98a2b3; min-width:85px; }
.event-content { min-width:0; flex:1; overflow-wrap:anywhere; }
.event-details { margin-top:8px; color:#475467; }
.event-details summary { cursor:pointer; font-size:12px; color:#315e9e; }
.event-details pre { max-height:220px; overflow:auto; white-space:pre-wrap; overflow-wrap:anywhere; background:#f8fafc; border-radius:6px; padding:10px; font-size:12px; }
.event-details small { color:#98a2b3; }
@media(max-width:600px) { .event-list li { gap:8px; } .event-list time { min-width:65px; } }
.files-panel { display:grid; grid-template-columns:200px minmax(0,1fr); gap:16px; }
.files-panel nav button { display:block; width:100%; text-align:left; padding:10px; border:1px solid #eee; background:white; cursor:pointer; overflow:hidden; text-overflow:ellipsis; }
.files-panel pre { margin:0; max-height:600px; overflow:auto; background:#f8fafc; padding:16px; border-radius:8px; }
.welcome { padding:80px 0; }
@media(max-width:800px) { .workspace-grid { grid-template-columns:1fr; } .files-panel { grid-template-columns:1fr; } }
</style>
