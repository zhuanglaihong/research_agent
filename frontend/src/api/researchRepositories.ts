import request from '@/request'

type Response<T> = { code: number; message: string; data: T }
export type ImportedRepository = { id: string; repositoryUrl: string; repositoryName: string; localPath: string; commitHash: string; importedAt: string }
export const listImportedRepositories = (projectId: string) => request<Response<ImportedRepository[]>>(`/research-projects/${projectId}/repositories`)
export const importGithubRepository = (projectId: string, url: string) => request<Response<ImportedRepository>>(`/research-projects/${projectId}/repositories`, { method: 'POST', data: { url } })
