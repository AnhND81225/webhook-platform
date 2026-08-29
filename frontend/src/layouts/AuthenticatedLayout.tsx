import { NavLink, Outlet } from 'react-router-dom'

export function AuthenticatedLayout() {
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
          <span className="mono">M0 / FOUNDATION</span>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

