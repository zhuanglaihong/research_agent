import request from '@/request'

type Response<T> = { code: number; message: string; data: T }
export type KnowledgeDocument = { id: string; source: string; content: string }
export type KnowledgeHit = { id: string; chunkId: string; source: string; excerpt: string; score: number }

export const getProjectMemory = (projectId: string) =>
  request<Response<string>>(`/research-projects/${projectId}/memory`)
export const saveProjectMemory = (projectId: string, content: string) =>
  request<Response<string>>(`/research-projects/${projectId}/memory`, { method: 'PUT', data: { content } })
export const listKnowledge = (projectId: string) =>
  request<Response<KnowledgeDocument[]>>(`/research-projects/${projectId}/knowledge`)
export const addKnowledge = (projectId: string, source: string, content: string) =>
  request<Response<KnowledgeDocument>>(`/research-projects/${projectId}/knowledge`, { method: 'POST', data: { source, content } })
export const searchKnowledge = (projectId: string, query: string) =>
  request<Response<KnowledgeHit[]>>(`/research-projects/${projectId}/knowledge/search`, { params: { query } })
export const deleteKnowledge = (projectId: string, documentId: string) =>
  request<Response<boolean>>(`/research-projects/${projectId}/knowledge/${documentId}`, { method: 'DELETE' })
