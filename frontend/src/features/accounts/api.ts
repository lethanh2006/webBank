import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useDemoCustomerId } from '@/lib/demoIdentity'
import { http } from '@/lib/http'
import type { MoneyView } from '@/lib/money'

export interface Account {
  accountNo: string
  name: string
  type: 'CURRENT' | 'INTERNAL'
  status: 'ACTIVE' | 'FROZEN' | 'CLOSED'
  ledgerBalance: MoneyView
  availableBalance: MoneyView
}

export interface Transaction {
  id: number
  reference: string
  type: 'DEPOSIT' | 'INTERNAL_TRANSFER' | 'REVERSAL'
  description: string
  direction: 'DEBIT' | 'CREDIT'
  amount: MoneyView
  balanceAfter: MoneyView
  counterpartyAccountNo: string | null
  counterpartyName: string | null
  createdAt: string
}

interface HistoryPage {
  items: Transaction[]
  nextCursor: string | null
}

export interface AccountHolder {
  accountNo: string
  name: string
  currency: string
}

const path = (accountNo: string) => `/accounts/${encodeURIComponent(accountNo)}`

export function useAccounts() {
  const customerId = useDemoCustomerId()
  return useQuery({
    queryKey: ['accounts', customerId],
    queryFn: async () => (await http.get<Account[]>('/accounts')).data,
    enabled: customerId !== null,
  })
}

export function useAccount(accountNo: string) {
  const customerId = useDemoCustomerId()
  return useQuery({
    queryKey: ['accounts', customerId, accountNo],
    queryFn: async () => (await http.get<Account>(path(accountNo))).data,
    enabled: customerId !== null,
    retry: false,
  })
}

/** Account statement with cursor (keyset) pagination: each page asks for rows older than the cursor. */
export function useTransactions(accountNo: string) {
  const customerId = useDemoCustomerId()
  return useInfiniteQuery({
    queryKey: ['transactions', customerId, accountNo],
    queryFn: async ({ pageParam }) =>
      (
        await http.get<HistoryPage>(`${path(accountNo)}/transactions`, {
          params: { cursor: pageParam, size: 10 },
        })
      ).data,
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: customerId !== null,
    retry: false,
  })
}

export async function lookupHolder(accountNo: string): Promise<AccountHolder> {
  return (await http.get<AccountHolder>(`/account-holders/${encodeURIComponent(accountNo)}`)).data
}
