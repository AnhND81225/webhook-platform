import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  fetchCurrentUser,
  logoutSession,
  UnauthenticatedError,
  type AuthenticatedUser,
} from './auth-api'

type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'authenticated'; user: AuthenticatedUser }
  | { status: 'unauthenticated'; user: null }
  | { status: 'error'; user: null }

type AuthContextValue = AuthState & {
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  const refresh = useCallback(async (signal?: AbortSignal) => {
    if (mounted.current) setState({ status: 'loading', user: null })
    try {
      const user = await fetchCurrentUser(signal)
      if (!signal?.aborted && mounted.current) setState({ status: 'authenticated', user })
    } catch (error) {
      if (signal?.aborted || !mounted.current) return
      if (error instanceof UnauthenticatedError) {
        setState({ status: 'unauthenticated', user: null })
        return
      }
      setState({ status: 'error', user: null })
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutSession()
      setState({ status: 'unauthenticated', user: null })
    } catch (error) {
      if (error instanceof UnauthenticatedError) {
        setState({ status: 'unauthenticated', user: null })
        return
      }
      setState({ status: 'error', user: null })
      throw error
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void refresh(controller.signal)
    return () => controller.abort()
  }, [refresh])

  useLayoutEffect(() => {
    const handleUnauthenticated = () => setState({ status: 'unauthenticated', user: null })
    window.addEventListener('webhook-platform:unauthenticated', handleUnauthenticated)
    return () => window.removeEventListener('webhook-platform:unauthenticated', handleUnauthenticated)
  }, [])

  const value = useMemo(() => ({ ...state, refresh, logout }), [state, refresh, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
