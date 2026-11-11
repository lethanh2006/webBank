import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
    // Never retry writes blindly: a repeated money transfer is made safe by an
    // Idempotency-Key checked on the server, not by client-side retries.
    mutations: { retry: false },
  },
})
