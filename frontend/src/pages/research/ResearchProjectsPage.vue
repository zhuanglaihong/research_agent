<template>
  <main class="research-page">
    <section class="page-heading">
      <div>
        <p class="eyebrow">RESEARCH WORKSPACE</p>
        <h1>科研项目</h1>
        <p class="subtitle">本地管理科研项目、代码任务与生成成果，无需注册登录。</p>
      </div>
      <a-button type="primary" size="large" @click="showCreate = true">新建项目</a-button>
    </section>

    <a-alert
      message="创建科研项目后，可通过对话下达代码实现任务，审核计划并查看生成进度和代码产物。"
      type="info"
      show-icon
      class="notice"
    />

    <a-spin :spinning="loading">
      <a-empty v-if="!loading && projects.length === 0" description="还没有科研项目">
        <a-button type="primary" @click="showCreate = true">创建第一个项目</a-button>
      </a-empty>
      <a-row v-else :gutter="[16, 16]">
        <a-col v-for="project in projects" :key="project.id" :xs="24" :md="12" :xl="8">
          <a-card class="project-card" :title="project.name" :bordered="false">
            <template #extra>
              <a-tag color="blue">{{ project.defaultLanguage }}</a-tag>
            </template>
            <p class="description">{{ project.description || '暂无项目说明' }}</p>
            <div class="workspace-path" :title="project.workspacePath">
              {{ project.workspacePath }}
            </div>
            <template #actions>
              <RouterLink :to="`/projects/${project.id}`">进入工作台</RouterLink>
              <a-popconfirm title="归档此项目？" @confirm="archiveProject(project)">
                <span class="archive-action">归档</span>
              </a-popconfirm>
            </template>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>

    <a-modal
      v-model:open="showCreate"
      title="新建科研项目"
      ok-text="创建项目"
      cancel-text="取消"
      :confirm-loading="creating"
      @ok="submitCreate"
    >
      <a-form layout="vertical">
        <a-form-item label="项目名称" required>
          <a-input v-model:value="form.name" maxlength="160" placeholder="例如：论文复现实验" />
        </a-form-item>
        <a-form-item label="工作区路径（可选）">
          <a-input v-model:value="form.workspacePath" placeholder="留空由助手自动创建项目目录" />
        </a-form-item>
        <a-form-item label="主要语言">
          <a-select v-model:value="form.defaultLanguage">
            <a-select-option value="python">Python</a-select-option>
            <a-select-option value="go">Go</a-select-option>
            <a-select-option value="java">Java</a-select-option>
            <a-select-option value="r">R</a-select-option>
            <a-select-option value="cpp">C/C++</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目说明">
          <a-textarea v-model:value="form.description" :rows="3" maxlength="2000" />
        </a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  archiveResearchProject as archiveProjectRequest,
  createResearchProject,
  listResearchProjects,
  type ResearchProject,
} from '@/api/researchProjectController'

const projects = ref<ResearchProject[]>([])
const loading = ref(false)
const creating = ref(false)
const showCreate = ref(false)
const form = reactive({ name: '', description: '', workspacePath: '', defaultLanguage: 'python' })

const loadProjects = async () => {
  loading.value = true
  try {
    const response = await listResearchProjects()
    if (response.data.code !== 0) throw new Error(response.data.message)
    projects.value = response.data.data ?? []
  } catch (error) {
    console.error('加载科研项目失败', error)
    message.error('加载科研项目失败，请确认后端服务已启动')
  } finally {
    loading.value = false
  }
}

const submitCreate = async () => {
  if (!form.name.trim()) {
    message.warning('请填写项目名称')
    return
  }
  creating.value = true
  try {
    const response = await createResearchProject({
      name: form.name.trim(),
      description: form.description.trim(),
      workspacePath: form.workspacePath.trim(),
      defaultLanguage: form.defaultLanguage,
    })
    if (response.data.code !== 0) throw new Error(response.data.message)
    message.success('项目已创建')
    showCreate.value = false
    Object.assign(form, { name: '', description: '', workspacePath: '', defaultLanguage: 'python' })
    await loadProjects()
  } catch (error) {
    console.error('创建科研项目失败', error)
    message.error(error instanceof Error ? error.message : '创建项目失败')
  } finally {
    creating.value = false
  }
}

const archiveProject = async (project: ResearchProject) => {
  try {
    const response = await archiveProjectRequest(project.id)
    if (response.data.code !== 0) throw new Error(response.data.message)
    projects.value = projects.value.filter((item) => item.id !== project.id)
    message.success('项目已归档')
  } catch (error) {
    console.error('归档科研项目失败', error)
    message.error(error instanceof Error ? error.message : '归档失败')
  }
}

const formatDate = (value: string) => new Date(value).toLocaleDateString()

onMounted(loadProjects)
</script>

<style scoped>
.research-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px 64px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

h1 {
  margin: 0;
  color: #172033;
  font-size: 32px;
}

.subtitle {
  margin: 8px 0 0;
  color: #667085;
}

.notice {
  margin-bottom: 24px;
}

.project-card {
  height: 100%;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgb(16 24 40 / 6%);
}

.description {
  min-height: 48px;
  color: #475467;
}

.workspace-path {
  overflow: hidden;
  color: #667085;
  font-family: monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-action {
  color: #cf1322;
}

@media (max-width: 600px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
