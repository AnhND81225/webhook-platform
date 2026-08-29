import { Link } from 'react-router-dom'
import { apiUrl } from '../config/api'

export function LoginPage() {
  return (
    <main className="centered-page">
      <section className="panel login-panel">
        <p className="eyebrow">Webhook Delivery Platform</p>
        <h1>Reliable delivery starts here.</h1>
        <p>
          The Google sign-in flow is prepared for the next milestone. No user account or dashboard data is created in M0.
        </p>
        <a className="button" href={apiUrl('/oauth2/authorization/google')}>
          Continue with Google
        </a>
        <Link className="text-link" to="/app">
          Preview application shell
        </Link>
      </section>
    </main>
  )
}

