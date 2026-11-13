import type { AccountHolder } from '@/features/accounts/api'
import type { TransferRequest } from './api'

export interface TransferDraft {
  request: TransferRequest
  holder: AccountHolder
  idempotencyKey: string
  createdAt: number
  submitted: boolean
}

const drafts = new Map<string, TransferDraft>()
const storageKey = (customerId: string) => `webbank.transferDraft.${customerId}`

/** Keep the original intent/key across navigation and reloads, separately for each identity. */
export function readTransferDraft(customerId: string | null): TransferDraft | undefined {
  if (!customerId) return undefined
  const cached = drafts.get(customerId)
  if (cached) return cached
  try {
    const stored = sessionStorage.getItem(storageKey(customerId))
    if (!stored) return undefined
    const draft = JSON.parse(stored) as TransferDraft
    if (typeof draft?.request?.sourceAccountNo !== 'string'
      || typeof draft.request.destinationAccountNo !== 'string'
      || typeof draft.request.amount !== 'string'
      || typeof draft.holder?.name !== 'string'
      || typeof draft.holder.accountNo !== 'string'
      || typeof draft.holder.currency !== 'string'
      || typeof draft.idempotencyKey !== 'string'
      || !/^[A-Za-z0-9_-]{8,64}$/.test(draft.idempotencyKey)
      || !Number.isFinite(draft.createdAt)
      || typeof draft.submitted !== 'boolean') return undefined
    drafts.set(customerId, draft)
    return draft
  } catch {
    return undefined
  }
}

export function saveTransferDraft(customerId: string, draft: TransferDraft) {
  drafts.set(customerId, draft)
  try {
    sessionStorage.setItem(storageKey(customerId), JSON.stringify(draft))
  } catch {
    // Restricted storage still keeps the key through in-app navigation.
  }
}

export function clearTransferDraft(customerId: string) {
  drafts.delete(customerId)
  try {
    sessionStorage.removeItem(storageKey(customerId))
  } catch {
    // The in-memory draft has already been removed.
  }
}
