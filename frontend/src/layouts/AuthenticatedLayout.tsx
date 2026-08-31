import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { useCallback, useEffect, useRef, useState } from 'react'
import { dashboardApi } from '../api/dashboard-api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../auth/AuthProvider'
import { Button } from '../components/ui/Button'

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || '?'
}

function AccountMenu({
  displayName,
  email,
  avatarUrl,
  onLogout,
}: {
  displayName: string
  email: string
  avatarUrl: string | null
  onLogout: () => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  async function signOut() {
    setSigningOut(true)
    await onLogout()
    setSigningOut(false)
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    function closeOnOutsidePointer(event: PointerEvent) {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false)
    }
    window.addEventListener('keydown', closeOnEscape)
    window.addEventListener('pointerdown', closeOnOutsidePointer)
    return () => {
      window.removeEventListener('keydown', closeOnEscape)
      window.removeEventListener('pointerdown', closeOnOutsidePointer)
    }
  }, [open])

  return (
    <div className="account-menu" ref={menuRef}>
      <button
        type="button"
        className="account-trigger"
        aria-expanded={open}
        aria-controls="account-menu"
        onClick={() => setOpen((value) => !value)}
      >
        <span className="account-avatar" aria-hidden="true">
          {avatarUrl ? <img src={avatarUrl} alt="" /> : initials(displayName)}
        </span>
        <span className="account-name">{displayName}</span>
      </button>
      {open && (
        <div className="account-menu-panel" id="account-menu" role="group" aria-label="Account actions">
          <div className="account-menu-identity">
            <strong>{displayName}</strong>
            <span>{email}</span>
          </div>
          <Button variant="secondary" disabled={signingOut} onClick={() => void signOut()}>
            {signingOut ? 'Signing out…' : 'Sign out'}
          </Button>
        </div>
      )}
    </div>
  )
}

export function AuthenticatedLayout() {
  const auth = useAuth()
  const navigate = useNavigate()
  const { applicationId } = useParams()
  const applications = useResource('applications:shell', useCallback((signal: AbortSignal) => dashboardApi.listApplications(signal), []))
  const [navigationOpen, setNavigationOpen] = useState(false)
  const sidebarRef = useRef<HTMLElement>(null)
  const mobileTriggerRef = useRef<HTMLButtonElement>(null)
  const wasNavigationOpen = useRef(false)

  useEffect(() => {
    if (navigationOpen) {
      sidebarRef.current?.focus()
    } else if (wasNavigationOpen.current) {
      mobileTriggerRef.current?.focus()
    }
    wasNavigationOpen.current = navigationOpen
  }, [navigationOpen])

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setNavigationOpen(false)
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [])

  if (auth.status !== 'authenticated') {
    return null
  }

  async function handleLogout(): Promise<void> {
    try {
      await auth.logout()
      navigate('/login', { replace: true })
    } catch {
      // AuthProvider transitions to its established connection-error state.
    }
  }

  const currentApplication = applications.data?.find((application) => application.id === applicationId)
  const applicationRoute = applicationId ? `/app/${applicationId}` : '/app'
  const closeNavigation = () => setNavigationOpen(false)

  return (
    <div className="console-layout">
      {navigationOpen && <button className="console-scrim" type="button" aria-label="Close navigation" onClick={closeNavigation} />}
      <aside id="application-console-navigation" className="console-sidebar" data-open={navigationOpen} ref={sidebarRef} tabIndex={-1} aria-label="Application console">
        <p className="console-brand"><span className="console-brand-mark" aria-hidden="true">W</span>Webhook Platform</p>
        <label className="application-context"><span>Application</span>
          <select className="app-selector" value={applicationId ?? ''} disabled={applications.loading || !applications.data?.length} onChange={(event) => { closeNavigation(); navigate(`/app/${event.target.value}`) }}>
            {!applicationId && <option value="">Select application</option>}
            {applications.data?.map((application) => <option key={application.id} value={application.id}>{application.name}</option>)}
          </select>
        </label>
        <nav className="console-nav" aria-label="Primary navigation">
          <NavLink end to={applicationRoute} onClick={closeNavigation}>Overview</NavLink>
          <NavLink to={applicationId ? `/app/${applicationId}/events` : '/app'} onClick={closeNavigation}>Events</NavLink>
          <NavLink to={applicationId ? `/app/${applicationId}/deliveries` : '/app'} onClick={closeNavigation}>Deliveries</NavLink>
        </nav>
        <div className="sidebar-account">
          <AccountMenu
            displayName={auth.user.displayName}
            email={auth.user.email}
            avatarUrl={auth.user.avatarUrl}
            onLogout={handleLogout}
          />
        </div>
      </aside>
      <div className="console-main">
        <header className="console-topbar">
          <button ref={mobileTriggerRef} className="mobile-nav-trigger" type="button" aria-label="Open navigation" aria-expanded={navigationOpen} aria-controls="application-console-navigation" onClick={() => setNavigationOpen(true)}>Menu</button>
          <span className="console-context">{currentApplication?.name ?? 'Developer console'}</span>
        </header>
        <main className="console-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
