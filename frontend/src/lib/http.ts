import axios, { type AxiosError } from 'axios'
import { getDemoCustomerId } from './demoIdentity'

/** Error body returned by the backend (RFC 9457 problem details). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  requestId?: string
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

  get code(): string | undefined {
    return this.problem?.code
  }

  /**
   * Whether repeating the same request (with the same Idempotency-Key) makes
   * sense: network failures, timeouts, lock contention and server errors.
   */
  get retryable(): boolean {
    return this.status === undefined || this.status >= 500 || this.code === 'ACCOUNT_BUSY'
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
  const customerId = getDemoCustomerId()
  if (customerId) config.headers.set('X-Demo-Customer-Id', customerId)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const data = error.response?.data
    const problem =
      data && typeof data === 'object' ? (data as ProblemDetail) : undefined
    const message = problem?.detail ?? problem?.title ?? 'Không kết nối được máy chủ'
    return Promise.reject(new ApiError(message, error.response?.status, problem))
  },
)
