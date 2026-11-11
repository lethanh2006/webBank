import { useSyncExternalStore } from 'react'

/**
 * Phase 1 stand-in for login: the selected demo customer id is sent as the
 * X-Demo-Customer-Id header. Phase 2 replaces this with a Keycloak access token.
 */
const STORAGE_KEY = 'webbank.demoCustomerId'
const listeners = new Set<() => void>()

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null // storage can be unavailable (private mode, blocked site data)
  }
}

let current = readStored()

export function getDemoCustomerId(): string | null {
  return current
}

export function setDemoCustomerId(id: string | null) {
  current = id
  try {
    if (id) localStorage.setItem(STORAGE_KEY, id)
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // keep the in-memory value only
  }
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useDemoCustomerId(): string | null {
  return useSyncExternalStore(subscribe, getDemoCustomerId, () => null)
}
