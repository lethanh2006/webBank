import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/http'

/**
 * The demo API runs on a free Render instance that sleeps when idle and needs
 * 1–2 minutes to start again. Reads that fail for a transient reason (no
 * response, timeout, 5xx) keep retrying for a few minutes, so a page opened
 * while the server is waking up fills in by itself instead of staying empty
 * until a reload. Client errors (4xx) are final and surface at once.
 */
const MAX_READ_RETRIES = 15

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) =>
        error instanceof ApiError && error.retryable && failureCount < MAX_READ_RETRIES,
      retryDelay: (attempt) => Math.min(1_000 * 2 ** attempt, 10_000),
      refetchOnWindowFocus: false,
    },
    // Never retry writes blindly: a repeated money transfer is made safe by an
    // Idempotency-Key checked on the server, not by client-side retries.
    mutations: { retry: false },
  },
})
