import { Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { apiUrl } from '../config/api'

export function LoginPage() {
  const auth = useAuth()
  const [searchParams] = useSearchParams()

  if (auth.status === 'authenticated') {
    return <Navigate to="/app" replace />
  }

  return (
    <main className="centered-page">
      <section className="panel login-panel">
        <p className="eyebrow">Webhook Delivery Platform</p>
        <h1>Reliable delivery starts here.</h1>
        <p>Sign in with your verified Google identity to access the developer dashboard.</p>
        {searchParams.get('error') === 'oauth' && (
          <p className="error-message" role="alert">
            Google sign-in could not be completed. Please try again.
          </p>
        )}
        <a className="button" href={apiUrl('/oauth2/authorization/google')}>
          Continue with Google
        </a>
      </section>
    </main>
  )
}
