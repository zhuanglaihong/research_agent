import request from '@/request'

type Response<T> = { code: number; message: string; data: T }
export type PaperSubscription = { topic: string; enabled: boolean; lastChecked: string | null; lastError: string | null }
export type PaperItem = { id: string; arxivId: string; title: string; abstractText: string; url: string; publishedAt: string | null }
export const getPaperSubscription = (projectId: string) => request<Response<PaperSubscription>>(`/research-projects/${projectId}/papers/subscription`)
export const savePaperSubscription = (projectId: string, topic: string, enabled: boolean) => request<Response<PaperSubscription>>(`/research-projects/${projectId}/papers/subscription`, { method: 'PUT', data: { topic, enabled } })
export const listPapers = (projectId: string) => request<Response<PaperItem[]>>(`/research-projects/${projectId}/papers`)
export const syncPapers = (projectId: string) => request<Response<{added: number}>>(`/research-projects/${projectId}/papers/sync`, { method: 'POST' })
export type PaperMethod = { id: string; title: string; source: string; methodBrief: string; textLength: number }
export const listPaperMethods = (projectId: string) => request<Response<PaperMethod[]>>(`/research-projects/${projectId}/paper-methods`)
export const uploadPaperMethod = (projectId: string, title: string, file: File) => {
  const form = new FormData()
  form.append('title', title)
  form.append('file', file)
  return request<Response<PaperMethod>>(`/research-projects/${projectId}/paper-methods`, { method: 'POST', data: form })
}
export const createPaperMethodTask = (projectId: string, methodId: string, instructions: string) =>
  request<Response<{ id: string }>>(`/research-projects/${projectId}/paper-methods/${methodId}/tasks`, { method: 'POST', data: { instructions } })
