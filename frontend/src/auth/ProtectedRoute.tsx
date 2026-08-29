import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthProvider'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const auth = useAuth()

  if (auth.status === 'loading') {
    return (
      <main className="centered-page" aria-live="polite">
        <section className="panel state-panel">
          <p className="eyebrow">Authentication</p>
          <h1>Loading your session</h1>
        </section>
      </main>
    )
  }

  if (auth.status === 'error') {
    return (
      <main className="centered-page" role="alert">
        <section className="panel state-panel">
          <p className="eyebrow">Connection error</p>
          <h1>We could not verify your session</h1>
          <p>Check the backend connection and try again.</p>
          <button type="button" onClick={() => void auth.refresh()}>
            Try again
          </button>
        </section>
      </main>
    )
  }

  if (auth.status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }

  return children
}
