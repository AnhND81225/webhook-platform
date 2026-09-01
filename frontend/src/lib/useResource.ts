import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ApiError } from '../api/dashboard-api'

type State<T> = { loading: boolean; data: T | null; error: ApiError | Error | null }

export function useResource<T>(key: string, load: (signal: AbortSignal) => Promise<T>): State<T> & {
  reload: () => void
  update: (updater: (current: T | null) => T) => void
} {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState<State<T>>({ loading: true, data: null, error: null })
  const request = useRef(0)

  // Clear application-scoped data before the browser paints a route with a new key.
  // This avoids briefly showing a previous application's resource while its request starts.
  useLayoutEffect(() => {
    request.current += 1
    setState({ loading: true, data: null, error: null })
  }, [key])

  useEffect(() => {
    const controller = new AbortController()
    const id = ++request.current
    setState({ loading: true, data: null, error: null })
    void load(controller.signal).then((data) => {
      if (id === request.current) setState({ loading: false, data, error: null })
    }).catch((error: unknown) => {
      if (controller.signal.aborted || id !== request.current) return
      setState({ loading: false, data: null, error: error instanceof Error ? error : new Error('Request failed') })
    })
    return () => controller.abort()
  }, [key, version, load])
  const reload = useCallback(() => setVersion((item) => item + 1), [])
  const update = useCallback((updater: (current: T | null) => T) => {
    request.current += 1
    setState((current) => ({ loading: false, data: updater(current.data), error: null }))
  }, [])
  return { ...state, reload, update }
}
