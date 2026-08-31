import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { useCallback, useEffect, useRef, useState } from 'react'
import { dashboardApi, type Application } from '../api/dashboard-api'
import { useResource } from '../lib/useResource'
import { useAuth } from '../auth/AuthProvider'
import { Button } from '../components/ui/Button'
import { CreateApplicationDialog } from '../components/application/CreateApplicationDialog'

export type ApplicationShellContext = {
  applications: Application[] | null
  applicationsLoading: boolean
  applicationsError: Error | null
  retryApplications: () => void
  updateApplicationInShell: (application: Application) => void
  openApplicationCreation: (trigger?: HTMLElement) => void
}

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
  const [creationOpen, setCreationOpen] = useState(false)
  const sidebarRef = useRef<HTMLElement>(null)
  const mobileTriggerRef = useRef<HTMLButtonElement>(null)
  const creationTriggerRef = useRef<HTMLElement>(null)
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
  const openApplicationCreation = (trigger?: HTMLElement) => {
    creationTriggerRef.current = trigger ?? null
    closeNavigation()
    setCreationOpen(true)
  }
  const handleApplicationCreated = (application: Application) => {
    updateApplicationInShell(application, true)
    setCreationOpen(false)
    navigate(`/app/${application.id}`)
  }
  const updateApplicationInShell = (application: Application, prepend = false) => {
    applications.update((current) => {
      const remaining = (current ?? []).filter((item) => item.id !== application.id)
      if (prepend || !current) return [application, ...remaining]
      return current.map((item) => item.id === application.id ? application : item)
    })
  }

  return (
    <div className="console-layout">
      {navigationOpen && <button className="console-scrim" type="button" aria-label="Close navigation" onClick={closeNavigation} />}
      <aside id="application-console-navigation" className="console-sidebar" data-open={navigationOpen} ref={sidebarRef} tabIndex={-1} aria-label="Application console">
        <p className="console-brand"><span className="console-brand-mark" aria-hidden="true">W</span>Webhook Platform</p>
        <label className="application-context"><span>Application</span>
          <select className="app-selector" value={applicationId ?? ''} disabled={applications.loading || !applications.data?.length} onChange={(event) => { closeNavigation(); navigate(`/app/${event.target.value}`) }}>
            {!applicationId && <option value="">Select application</option>}
            {applications.data?.map((application) => <option key={application.id} value={application.id}>{application.name} · {application.environment === 'PRODUCTION' ? 'Production' : 'Development'}</option>)}
          </select>
        </label>
        {applications.data?.length && <Button className="application-create-trigger" variant="secondary" onClick={(event) => openApplicationCreation(event.currentTarget)}>Create application</Button>}
        <nav className="console-nav" aria-label="Primary navigation">
          <NavLink end to="/app/applications" onClick={closeNavigation}>Applications</NavLink>
          {applicationId && (
            <>
              <NavLink end to={applicationRoute} onClick={closeNavigation}>Overview</NavLink>
              <NavLink to={`/app/${applicationId}/events`} onClick={closeNavigation}>Events</NavLink>
              <NavLink to={`/app/${applicationId}/deliveries`} onClick={closeNavigation}>Deliveries</NavLink>
              <NavLink to={`/app/${applicationId}/settings`} onClick={closeNavigation}>Settings</NavLink>
            </>
          )}
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
          <Outlet context={{ applications: applications.data, applicationsLoading: applications.loading, applicationsError: applications.error, retryApplications: applications.reload, updateApplicationInShell, openApplicationCreation }} />
        </main>
      </div>
      {creationOpen && <CreateApplicationDialog onCreated={handleApplicationCreated} onDismiss={() => setCreationOpen(false)} returnFocusRef={creationTriggerRef} />}
    </div>
  )
}
