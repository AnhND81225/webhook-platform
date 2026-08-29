import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

export function AuthenticatedLayout() {
  const auth = useAuth()
  const navigate = useNavigate()

  if (auth.status !== 'authenticated') {
    return null
  }

  async function handleLogout() {
    try {
      await auth.logout()
      navigate('/login', { replace: true })
    } catch {
      console.error('Backend session logout failed')
    }
  }

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <p className="brand">Webhook Platform</p>
        <nav aria-label="Primary navigation">
          <NavLink to="/app">Foundation</NavLink>
        </nav>
      </aside>
      <div className="app-main">
        <header className="topbar">
          <span className="mono">M1 / AUTHENTICATION</span>
          <div className="user-actions">
            <span>{auth.user.displayName}</span>
            <button className="secondary-button" type="button" onClick={() => void handleLogout()}>
              Sign out
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
