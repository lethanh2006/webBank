import { useQuery } from '@tanstack/react-query'
import { http } from '@/lib/http'

export interface DemoCustomer {
  id: number
  cifNo: string
  fullName: string
}

export function useDemoCustomers() {
  return useQuery({
    queryKey: ['demo', 'customers'],
    queryFn: async ({ signal }) => (await http.get<DemoCustomer[]>('/demo/customers', { signal })).data,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  })
}
