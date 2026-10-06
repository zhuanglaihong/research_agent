import request from '@/request'

type Response<T> = { code: number; message: string; data: T }
export type RunCapabilities = { enabled: boolean; language: string; pythonExecutable: string; approvalRequired: boolean; maxRuntimeSeconds: number; maxLogBytes: number }
export type ResearchRun = { id: string; taskId: string; scriptPath: string; workingDirectory: string; status: string; exitCode: number | null; createdAt: string; startedAt: string | null; finishedAt: string | null }
export const getRunCapabilities = () => request<Response<RunCapabilities>>('/research/runs/capabilities')
export const createRun = (taskId: string, scriptPath: string) => request<Response<ResearchRun>>(`/tasks/${taskId}/runs`, { method: 'POST', data: { scriptPath } })
export const listRuns = (taskId: string) => request<Response<ResearchRun[]>>(`/tasks/${taskId}/runs`)
export const approveRun = (id: string) => request<Response<ResearchRun>>(`/runs/${id}/approve`, { method: 'POST' })
export const cancelRun = (id: string) => request<Response<ResearchRun>>(`/runs/${id}/cancel`, { method: 'POST' })
export const getRunLogs = (id: string) => request<Response<{stdout: string; stderr: string}>>(`/runs/${id}/logs`)
