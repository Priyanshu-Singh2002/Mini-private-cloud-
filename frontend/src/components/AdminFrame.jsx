import { Link, NavLink, useNavigate } from 'react-router-dom'
import { ArrowLeft, LogOut, ScrollText, ShieldCheck, UserRound } from 'lucide-react'

import { useAuth } from '../hooks/useAuth'

function navClass({ isActive }) {
  return isActive ? 'nav-item is-active' : 'nav-item'
}

export default function AdminFrame({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="dashboard-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <ShieldCheck size={22} />
          <span>Mini Cloud Admin</span>
        </div>

        <nav aria-label="Admin navigation">
          <Link className="nav-item" to="/dashboard">
            <ArrowLeft size={18} />
            My workspace
          </Link>

          <NavLink className={navClass} to="/admin" end>
            <UserRound size={18} />
            User management
          </NavLink>

          <NavLink className={navClass} to="/admin/audit">
            <ScrollText size={18} />
            Audit history
          </NavLink>
        </nav>

        <p className="sidebar-note">
          Manage accounts and access to your private cloud.
        </p>
      </aside>

      <div className="dashboard-main">
        <header className="topbar">
          <div>
            <p className="eyebrow">Administration</p>
            <h1>Admin dashboard</h1>
          </div>

          <div className="topbar-actions">
            <div className="user-chip">
              <span className="avatar">
                {(user?.username || 'A').slice(0, 2).toUpperCase()}
              </span>

              <span>
                <strong>{user?.username}</strong>
                <small>ADMIN</small>
              </span>
            </div>

            <button
              type="button"
              className="ghost-button"
              onClick={handleLogout}
            >
              <LogOut size={16} />
              Log out
            </button>
          </div>
        </header>

        {children}
      </div>
    </div>
  )
}
