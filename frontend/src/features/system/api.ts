import { useQuery } from '@tanstack/react-query'
import { http } from '@/lib/http'

export interface PingResponse {
  status: string
  service: string
  serverTime: string
}

export function usePing() {
  return useQuery({
    queryKey: ['system', 'ping'],
    queryFn: async () => (await http.get<PingResponse>('/system/ping')).data,
    refetchInterval: 15_000,
  })
}
