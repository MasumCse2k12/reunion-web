import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { adminApi, type AdminAccount } from './api'

/**
 * The admin session is deliberately separate from the member session in `store.tsx`.
 * Signing in as a coordinator never touches — and never grants — a member session.
 */
type Ctx = {
  admin: AdminAccount | null
  setAdmin: (a: AdminAccount | null) => void
  ready: boolean
  isSuper: boolean
  signOut: () => Promise<void>
}

const AdminCtx = createContext<Ctx | null>(null)

export function AdminProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminAccount | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (adminApi.getSession()) {
      adminApi.me().then((a) => {
        setAdmin(a)
        setReady(true)
      })
    } else {
      setReady(true)
    }
  }, [])

  const signOut = useCallback(async () => {
    await adminApi.logout()
    setAdmin(null)
  }, [])

  const value = useMemo(
    () => ({ admin, setAdmin, ready, isSuper: admin?.role === 'SUPER_ADMIN', signOut }),
    [admin, ready, signOut],
  )

  return <AdminCtx.Provider value={value}>{children}</AdminCtx.Provider>
}

export function useAdmin() {
  const c = useContext(AdminCtx)
  if (!c) throw new Error('useAdmin must be used inside AdminProvider')
  return c
}
