import request from '@/request'

type Response<T> = { code: number; message: string; data: T }
export type PaperSubscription = { topic: string; enabled: boolean; lastChecked: string | null; lastError: string | null }
export type PaperItem = { id: string; arxivId: string; title: string; abstractText: string; url: string; publishedAt: string | null }
export const getPaperSubscription = (projectId: string) => request<Response<PaperSubscription>>(`/research-projects/${projectId}/papers/subscription`)
export const savePaperSubscription = (projectId: string, topic: string, enabled: boolean) => request<Response<PaperSubscription>>(`/research-projects/${projectId}/papers/subscription`, { method: 'PUT', data: { topic, enabled } })
export const listPapers = (projectId: string) => request<Response<PaperItem[]>>(`/research-projects/${projectId}/papers`)
export const syncPapers = (projectId: string) => request<Response<{added: number}>>(`/research-projects/${projectId}/papers/sync`, { method: 'POST' })
