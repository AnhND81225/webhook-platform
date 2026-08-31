import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { useCallback } from 'react'
import { dashboardApi } from '../api/dashboard-api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../auth/AuthProvider'

export function AuthenticatedLayout() {
  const auth = useAuth()
  const navigate = useNavigate()
  const { applicationId } = useParams()
  const applications = useResource('applications:shell', useCallback((signal: AbortSignal) => dashboardApi.listApplications(signal), []))

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
        <label className="application-selector">Application
          <select value={applicationId ?? ''} disabled={applications.loading || !applications.data?.length} onChange={(event) => navigate(`/app/${event.target.value}`)}>
            {!applicationId && <option value="">Select application</option>}
            {applications.data?.map((application) => <option key={application.id} value={application.id}>{application.name}</option>)}
          </select>
        </label>
        <nav aria-label="Primary navigation">
          <NavLink end to={applicationId ? `/app/${applicationId}` : '/app'}>Overview</NavLink>
          <NavLink to={applicationId ? `/app/${applicationId}/events` : '/app'}>Events</NavLink>
          <NavLink to={applicationId ? `/app/${applicationId}/deliveries` : '/app'}>Deliveries</NavLink>
        </nav>
      </aside>
      <div className="app-main">
        <header className="topbar">
          <span className="mono">M11 / OBSERVABILITY</span>
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
