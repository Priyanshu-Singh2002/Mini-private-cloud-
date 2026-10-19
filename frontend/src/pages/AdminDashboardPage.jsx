
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
  HardDrive,
  Database,
  Files,
  Users,
} from 'lucide-react'

import client from '../api/client'
import { updateUserQuota } from '../api/storage'
import { getAdminJobs } from '../api/jobs'
import { getApiErrorMessage } from '../api/errors'
import { useAuth } from '../hooks/useAuth'

const BYTES_PER_MB = 1024 * 1024

function formatDate(value) {
  if (!value) return '—'

  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString()
}


function formatBytes(bytes) {
  const value = Number(bytes)

  if (!Number.isFinite(value) || value <= 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(
    Math.floor(Math.log(value) / Math.log(1024)),
    units.length - 1,
  )

  return `${(value / Math.pow(1024, index)).toFixed(index === 0 ? 0 : 2)} ${units[index]}`
}

function bytesToMbInput(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value <= 0) return '100'
  return String(Math.round(value / BYTES_PER_MB))
}

function jobStatusClass(status) {
  const value = String(status || '').toLowerCase()
  return `job-status is-${value}`
}


export default function AdminDashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [busyUserId, setBusyUserId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [storageStats, setStorageStats] = useState(null)
  const [userStorage, setUserStorage] = useState([])
  const [storageLoading, setStorageLoading] = useState(true)
  const [storageError, setStorageError] = useState('')
  const [quotaDrafts, setQuotaDrafts] = useState({})
  const [quotaBusyId, setQuotaBusyId] = useState(null)
  const [quotaNotice, setQuotaNotice] = useState('')
  const [jobs, setJobs] = useState([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobsError, setJobsError] = useState('')


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


  const loadStorageStats = useCallback(async () => {
    setStorageLoading(true)
    setStorageError('')

    try {
      const [summaryResponse, usersResponse] = await Promise.all([
        client.get('/admin/storage/summary'),
        client.get('/admin/storage/users'),
      ])

      setStorageStats(summaryResponse.data)
      const rows = Array.isArray(usersResponse.data) ? usersResponse.data : []
      setUserStorage(rows)
      setQuotaDrafts((current) => {
        const next = { ...current }
        rows.forEach((item) => {
          if (next[item.userId] == null) {
            next[item.userId] = bytesToMbInput(item.storageQuotaBytes)
          }
        })
        return next
      })
    } catch (err) {
      setStorageError(getApiErrorMessage(err))
    } finally {
      setStorageLoading(false)
    }
  }, [])

  const loadJobs = useCallback(async () => {
    setJobsLoading(true)
    setJobsError('')

    try {
      const { data } = await getAdminJobs()
      setJobs(Array.isArray(data) ? data : [])
    } catch (err) {
      setJobsError(getApiErrorMessage(err))
    } finally {
      setJobsLoading(false)
    }
  }, [])


  useEffect(() => {
    loadStorageStats()
  }, [loadStorageStats])


  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  useEffect(() => {
    loadJobs()
  }, [loadJobs])

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

  async function saveQuota(item) {
    const mbValue = Number(quotaDrafts[item.userId])

    if (!Number.isFinite(mbValue) || mbValue <= 0) {
      setStorageError('Enter a positive quota in MB.')
      return
    }

    const storageQuotaBytes = Math.round(mbValue * BYTES_PER_MB)

    setQuotaBusyId(item.userId)
    setStorageError('')
    setQuotaNotice('')

    try {
      const { data } = await updateUserQuota(item.userId, storageQuotaBytes)
      setUserStorage((current) =>
        current.map((row) => (row.userId === data.userId ? data : row)),
      )
      setQuotaDrafts((current) => ({
        ...current,
        [item.userId]: bytesToMbInput(data.storageQuotaBytes),
      }))
      setQuotaNotice(`Updated ${item.username}'s quota to ${mbValue} MB.`)
    } catch (err) {
      setStorageError(getApiErrorMessage(err))
    } finally {
      setQuotaBusyId(null)
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
                <h2>Cloud storage</h2>
                <p>Monitor storage usage across your private cloud.</p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={() => {
                  loadUsers()
                  loadStorageStats()
                  loadJobs()
                }}
                disabled={storageLoading}
              >
                <RefreshCw size={16} />
                Refresh storage
              </button>
            </div>

            {storageError && (
              <div className="alert alert-error" role="alert">
                <Info size={18} />
                <span>{storageError}</span>
              </div>
            )}

            {quotaNotice && (
              <div className="file-notice" role="status">
                {quotaNotice}
              </div>
            )}

            {storageLoading ? (
              <p className="file-state">Loading storage statistics…</p>
            ) : storageStats ? (
              <>
                <section className="stat-grid" aria-label="Cloud storage statistics">
                  <article className="stat-card">
                    <Users size={18} />
                    <p className="stat-label">Total users</p>
                    <p className="stat-value">{storageStats.totalUsers}</p>
                  </article>

                  <article className="stat-card">
                    <Files size={18} />
                    <p className="stat-label">Total files</p>
                    <p className="stat-value">{storageStats.totalFiles}</p>
                  </article>

                  <article className="stat-card">
                    <Database size={18} />
                    <p className="stat-label">Storage used</p>
                    <p className="stat-value">
                      {formatBytes(storageStats.totalStorageBytes)}
                    </p>
                  </article>

                  <article className="stat-card">
                    <HardDrive size={18} />
                    <p className="stat-label">Users with files</p>
                    <p className="stat-value">{storageStats.usersWithFiles}</p>
                  </article>
                </section>

                <div className="file-section-heading">
                  <div>
                    <h2>User storage usage</h2>
                    <p>
                      Storage consumption and quotas for each registered
                      account. Quota input is in MB, where 1 MB = 1,048,576 bytes.
                    </p>
                  </div>
                </div>

                {userStorage.length === 0 ? (
                  <p className="file-state">No registered users found.</p>
                ) : (
                  <div className="admin-table-wrapper">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>User</th>
                          <th>Role</th>
                          <th>Status</th>
                          <th>Files</th>
                          <th>Used</th>
                          <th>Quota</th>
                          <th>Remaining</th>
                          <th>Usage</th>
                          <th>Edit quota (MB)</th>
                        </tr>
                      </thead>

                      <tbody>
                        {userStorage.map((item) => {
                          const percent = Number(item.usagePercent) || 0
                          const barClass = percent >= 100
                            ? 'usage-bar-fill is-full'
                            : percent >= 80
                              ? 'usage-bar-fill is-high'
                              : 'usage-bar-fill'

                          return (
                          <tr key={item.userId}>
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
                            <td>{item.fileCount}</td>
                            <td>{formatBytes(item.usedBytes)}</td>
                            <td>{formatBytes(item.storageQuotaBytes)}</td>
                            <td>{formatBytes(item.remainingBytes)}</td>
                            <td>
                              <div
                                className="usage-bar"
                                role="progressbar"
                                aria-valuemin={0}
                                aria-valuemax={100}
                                aria-valuenow={Math.round(percent)}
                              >
                                <div
                                  className={barClass}
                                  style={{ width: `${Math.min(100, percent)}%` }}
                                />
                              </div>
                              <div className="usage-meta">
                                <span>{percent.toFixed(1)}%</span>
                              </div>
                            </td>
                            <td>
                              <div className="quota-editor">
                                <input
                                  type="number"
                                  min="1"
                                  step="1"
                                  aria-label={`Quota in MB for ${item.username}`}
                                  value={quotaDrafts[item.userId] ?? ''}
                                  onChange={(event) =>
                                    setQuotaDrafts((current) => ({
                                      ...current,
                                      [item.userId]: event.target.value,
                                    }))
                                  }
                                />
                                <span className="quota-unit">MB</span>
                                <button
                                  type="button"
                                  className="ghost-button"
                                  disabled={quotaBusyId === item.userId}
                                  onClick={() => saveQuota(item)}
                                >
                                  {quotaBusyId === item.userId
                                    ? 'Saving…'
                                    : 'Save'}
                                </button>
                              </div>
                            </td>
                          </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            ) : (
              <p className="file-state">
                Storage statistics could not be loaded.
              </p>
            )}
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

          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>Compute jobs</h2>
                <p>
                  Overview of queued and completed jobs across all users.
                </p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={loadJobs}
                disabled={jobsLoading}
              >
                <RefreshCw size={16} />
                Refresh jobs
              </button>
            </div>

            {jobsError && (
              <div className="alert alert-error" role="alert">
                <Info size={18} />
                <span>{jobsError}</span>
              </div>
            )}

            {jobsLoading ? (
              <p className="file-state">Loading jobs…</p>
            ) : jobs.length === 0 ? (
              <p className="file-state">No jobs have been submitted yet.</p>
            ) : (
              <div className="admin-table-wrapper">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Owner</th>
                      <th>Type</th>
                      <th>Priority</th>
                      <th>Status</th>
                      <th>Submitted</th>
                      <th>Started</th>
                      <th>Completed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {jobs.map((job) => (
                      <tr key={job.id}>
                        <td>{job.id}</td>
                        <td>{job.ownerUsername}</td>
                        <td>{job.jobType}</td>
                        <td>{job.priority}</td>
                        <td>
                          <span className={jobStatusClass(job.status)}>
                            {job.status}
                          </span>
                        </td>
                        <td>{formatDate(job.submittedAt)}</td>
                        <td>{formatDate(job.startedAt)}</td>
                        <td>{formatDate(job.completedAt)}</td>
                      </tr>
                    ))}
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
