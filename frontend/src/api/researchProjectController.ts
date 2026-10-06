import request from '@/request'

export type ResearchProject = {
  id: string
  name: string
  description: string | null
  workspacePath: string
  defaultLanguage: string
  status: string
  createdAt: string
  updatedAt: string
}

type BaseResponse<T> = {
  code: number
  data: T
  message: string
}

export type CreateResearchProjectRequest = {
  name: string
  description?: string
  workspacePath: string
  defaultLanguage: string
}

export async function listResearchProjects() {
  return request<BaseResponse<ResearchProject[]>>('/research-projects', { method: 'GET' })
}

export async function createResearchProject(body: CreateResearchProjectRequest) {
  return request<BaseResponse<ResearchProject>>('/research-projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
  })
}

export async function archiveResearchProject(projectId: string) {
  return request<BaseResponse<boolean>>(`/research-projects/${projectId}`, { method: 'DELETE' })
}

export async function getResearchProject(projectId: string) {
  return request<BaseResponse<ResearchProject>>(`/research-projects/${projectId}`, { method: 'GET' })
}
