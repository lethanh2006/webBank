import { useMutation, useQueryClient } from '@tanstack/react-query'
import { http } from '@/lib/http'
import type { MoneyView } from '@/lib/money'

export interface TransferRequest {
  sourceAccountNo: string
  destinationAccountNo: string
  amount: string
  description?: string
}

export interface Transfer {
  reference: string
  type: 'INTERNAL'
  status: 'COMPLETED'
  sourceAccountNo: string
  destinationAccountNo: string
  destinationName: string
  amount: MoneyView
  description: string
  createdAt: string
  completedAt: string | null
}

export interface TransferResult {
  transfer: Transfer
  /** true when the server answered from an earlier execution of the same key */
  replayed: boolean
}

/**
 * The caller owns the Idempotency-Key: one key per transfer intent, reused on
 * every retry of that intent, so a retry can never move the money twice.
 */
export function useCreateTransfer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (variables: {
      request: TransferRequest
      idempotencyKey: string
    }): Promise<TransferResult> => {
      const response = await http.post<Transfer>('/transfers', variables.request, {
        headers: { 'Idempotency-Key': variables.idempotencyKey },
      })
      return {
        transfer: response.data,
        replayed: response.headers['idempotent-replayed'] === 'true',
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })
}
