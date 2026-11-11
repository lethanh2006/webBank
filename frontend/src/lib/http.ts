import axios, { type AxiosError } from 'axios'

/** Error body returned by the backend (RFC 9457 problem details). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  [extension: string]: unknown
}

export class ApiError extends Error {
  readonly status?: number
  readonly problem?: ProblemDetail

  constructor(message: string, status?: number, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  // Lets a user action be traced through backend logs. randomUUID needs a secure
  // context (https or localhost), so the header is skipped elsewhere.
  const requestId = globalThis.crypto?.randomUUID?.()
  if (requestId) config.headers.set('X-Request-Id', requestId)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const data = error.response?.data
    const problem =
      data && typeof data === 'object' ? (data as ProblemDetail) : undefined
    const message = problem?.detail ?? problem?.title ?? error.message
    return Promise.reject(new ApiError(message, error.response?.status, problem))
  },
)
