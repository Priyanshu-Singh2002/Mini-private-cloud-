import {
  Cloud,
  Info,
  LayoutDashboard,
  LogOut,
  Shield,
  UserRound,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  const initials = (user?.username || '?').slice(0, 2).toUpperCase()

  return (
    <div className="dashboard-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <Cloud size={22} strokeWidth={1.75} />
          <span>Mini Cloud</span>
        </div>
        <nav aria-label="Primary">
          <a className="nav-item is-active" href="/dashboard" aria-current="page">
            <LayoutDashboard size={18} />
            Overview
          </a>
        </nav>
        <p className="sidebar-note">
          This dashboard is a UI shell. File storage and compute features are
          not connected yet.
        </p>
      </aside>

      <div className="dashboard-main">
        <header className="topbar">
          <div>
            <p className="eyebrow">Workspace</p>
            <h1>Overview</h1>
          </div>
          <div className="topbar-actions">
            <div className="user-chip" title={user?.email}>
              <span className="avatar" aria-hidden="true">
                {initials}
              </span>
              <span>
                <strong>{user?.username}</strong>
                <small>{user?.role}</small>
              </span>
            </div>
            <button type="button" className="ghost-button" onClick={handleLogout}>
              <LogOut size={16} />
              Log out
            </button>
          </div>
        </header>

        <main className="dashboard-content">
          <section className="welcome-card">
            <h2>Welcome, {user?.username}</h2>
            <p>
              You are signed in locally using the user object returned by{' '}
              <code>POST /api/auth/login</code> or{' '}
              <code>POST /api/auth/register</code>. Logout only clears
              browser storage.
            </p>
          </section>

          <section className="stat-grid" aria-label="Account details">
            <article className="stat-card">
              <UserRound size={18} />
              <p className="stat-label">Username</p>
              <p className="stat-value">{user?.username}</p>
            </article>
            <article className="stat-card">
              <Shield size={18} />
              <p className="stat-label">Role</p>
              <p className="stat-value">{user?.role}</p>
            </article>
            <article className="stat-card">
              <Info size={18} />
              <p className="stat-label">Email</p>
              <p className="stat-value">{user?.email}</p>
            </article>
          </section>

          <section className="notice" role="note">
            <Info size={18} />
            <div>
              <strong>UI-only route guard</strong>
              <p>
                The dashboard route checks <code>localStorage</code> in the
                browser. It does not protect backend APIs. Spring Security
                currently permits all requests, and login does not issue a
                token.
              </p>
            </div>
          </section>
        </main>
      </div>
    </div>
  )
}
