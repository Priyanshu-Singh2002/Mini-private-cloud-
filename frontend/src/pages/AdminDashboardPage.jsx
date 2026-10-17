
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  RefreshCw,
  ShieldCheck,
  UserRound,
  UserX,
  UserCheck,
  LogOut,
  Info,
} from 'lucide-react'

import client from '../api/client'
import { getApiErrorMessage } from '../api/errors'
import { useAuth } from '../hooks/useAuth'

function formatDate(value) {
  if (!value) return '—'

  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString()
}

export default function AdminDashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [busyUserId, setBusyUserId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const { data } = await client.get('/admin/users')
      setUsers(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  async function changeStatus(targetUser, action) {
    const isSuspending = action === 'suspend'

    const confirmed = window.confirm(
      isSuspending
        ? `Suspend ${targetUser.username}? They will lose access to protected APIs.`
        : `Reactivate ${targetUser.username}?`,
    )

    if (!confirmed) return

    setBusyUserId(targetUser.id)
    setError('')
    setNotice('')

    try {
      const { data } = await client.patch(
        `/admin/users/${targetUser.id}/${action}`,
      )

      setUsers((current) =>
        current.map((item) =>
          item.id === data.id ? data : item,
        ),
      )

      setNotice(
        isSuspending
          ? `${targetUser.username} was suspended.`
          : `${targetUser.username} was reactivated.`,
      )
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setBusyUserId(null)
    }
  }

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  const activeCount = users.filter(
    (item) => item.status === 'ACTIVE',
  ).length

  const suspendedCount = users.filter(
    (item) => item.status === 'SUSPENDED',
  ).length

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

          <a
            className="nav-item is-active"
            href="/admin"
            aria-current="page"
          >
            <UserRound size={18} />
            User management
          </a>
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
                {(user?.username || 'A')
                  .slice(0, 2)
                  .toUpperCase()}
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

        <main className="dashboard-content">
          <section className="welcome-card">
            <h2>User management</h2>
            <p>
              View registered users and manage their account status.
            </p>
          </section>

          <section className="stat-grid" aria-label="User statistics">
            <article className="stat-card">
              <UserRound size={18} />
              <p className="stat-label">Total users</p>
              <p className="stat-value">{users.length}</p>
            </article>

            <article className="stat-card">
              <UserCheck size={18} />
              <p className="stat-label">Active accounts</p>
              <p className="stat-value">{activeCount}</p>
            </article>

            <article className="stat-card">
              <UserX size={18} />
              <p className="stat-label">Suspended accounts</p>
              <p className="stat-value">{suspendedCount}</p>
            </article>
          </section>

          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>Registered users</h2>
                <p>
                  Account changes are enforced by the backend.
                </p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={loadUsers}
                disabled={loading || busyUserId !== null}
              >
                <RefreshCw size={16} />
                Refresh
              </button>
            </div>

            {error && (
              <div className="alert alert-error" role="alert">
                <Info size={18} />
                <span>{error}</span>
              </div>
            )}

            {notice && (
              <div className="file-notice" role="status">
                {notice}
              </div>
            )}

            {loading ? (
              <p className="file-state">Loading users…</p>
            ) : users.length === 0 ? (
              <div className="file-empty">
                <UserRound size={32} />
                <h3>No users found</h3>
              </div>
            ) : (
              <div className="admin-table-wrapper">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>User</th>
                      <th>Role</th>
                      <th>Status</th>
                      <th>Registered</th>
                      <th>Actions</th>
                    </tr>
                  </thead>

                  <tbody>
                    {users.map((item) => {
                      const isSelf = item.id === user?.id
                      const isAdmin = item.role === 'ADMIN'
                      const isBusy = busyUserId === item.id

                      return (
                        <tr key={item.id}>
                          <td>
                            <strong>{item.username}</strong>
                            <small className="admin-user-email">
                              {item.email}
                            </small>
                          </td>

                          <td>{item.role}</td>

                          <td>
                            <span
                              className={
                                item.status === 'ACTIVE'
                                  ? 'admin-status is-active'
                                  : 'admin-status is-suspended'
                              }
                            >
                              {item.status}
                            </span>
                          </td>

                          <td>{formatDate(item.createdAt)}</td>

                          <td>
                            {isAdmin ? (
                              <span className="admin-muted">
                                Admin account
                              </span>
                            ) : item.status === 'ACTIVE' ? (
                              <button
                                type="button"
                                className="ghost-button danger-button"
                                disabled={isBusy || isSelf}
                                onClick={() =>
                                  changeStatus(item, 'suspend')
                                }
                              >
                                {isBusy ? 'Working…' : 'Suspend'}
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="ghost-button"
                                disabled={isBusy}
                                onClick={() =>
                                  changeStatus(item, 'reactivate')
                                }
                              >
                                {isBusy ? 'Working…' : 'Reactivate'}
                              </button>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="notice" role="note">
            <Info size={18} />
            <div>
              <strong>Admin access</strong>
              <p>
                The server checks your ADMIN role for every admin
                API request. The dashboard does not replace backend
                authorization.
              </p>
            </div>
          </section>
        </main>
      </div>
    </div>
  )
}
