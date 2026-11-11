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
    queryFn: async () => (await http.get<DemoCustomer[]>('/demo/customers')).data,
    staleTime: Infinity,
  })
}
