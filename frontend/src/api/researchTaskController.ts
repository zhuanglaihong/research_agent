import request from '@/request'
type Response<T> = { code: number; message: string; data: T }
export type ResearchTask = {
  id: string; projectId: string; title: string; requestText: string; status: string
  planJson: string; resultText: string | null; outputPath: string | null; conversationId: string
  provider: string; errorMessage: string | null; createdAt: string; updatedAt: string
}
export type TaskEvent = { id: string; taskId: string; type: string; payload: string; timestamp: string }
export const listTasks = (project: string) => request<Response<ResearchTask[]>>(`/projects/${project}/tasks`)
export const createTask = (project: string, prompt: string, conversationId?: string) => request<Response<ResearchTask>>(`/projects/${project}/tasks`, { method: 'POST', data: { prompt, ...(conversationId ? { conversationId } : {}) } })
export const getTask = (id: string) => request<Response<ResearchTask>>(`/tasks/${id}`)
export const approveTask = (id: string) => request<Response<ResearchTask>>(`/tasks/${id}/approve`, { method: 'POST' })
export const cancelTask = (id: string) => request<Response<ResearchTask>>(`/tasks/${id}/cancel`, { method: 'POST' })
export const getTaskEvents = (id: string) => request<Response<TaskEvent[]>>(`/tasks/${id}/events`)
export const getTaskFiles = (id: string) => request<Response<string[]>>(`/tasks/${id}/files`)
export const getTaskFile = (id: string, path: string) => request<Response<string>>(`/tasks/${id}/file`, { params: { path } })
