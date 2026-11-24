
import { useCallback, useEffect, useState } from 'react'
import {
  RefreshCw,
  UserRound,
  UserX,
  UserCheck,
  Info,
  HardDrive,
  Database,
  Files,
  Users,
  CheckCircle,
  XCircle,
  Clock,
} from 'lucide-react'

import client from '../api/client'
import { updateUserQuota } from '../api/storage'
import { getAdminJobs } from '../api/jobs'
import { getApiErrorMessage } from '../api/errors'
import { useAuth } from '../hooks/useAuth'
import AdminFrame from '../components/AdminFrame'

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

  return `${(value / Math.pow(1024, index)).toFixed(
    index === 0 ? 0 : 2,
  )} ${units[index]}`
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

function userStatusClass(status) {
  const value = String(status || '').toLowerCase()
  return `admin-status is-${value}`
}

export default function AdminDashboardPage() {
  const { user } = useAuth()

  // Registered users
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [busyUserId, setBusyUserId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Pending registration requests
  const [pendingRequests, setPendingRequests] = useState([])
  const [pendingLoading, setPendingLoading] = useState(true)
  const [pendingError, setPendingError] = useState('')
  const [pendingBusyId, setPendingBusyId] = useState(null)
  const [pendingNotice, setPendingNotice] = useState('')

  // Storage
  const [storageStats, setStorageStats] = useState(null)
  const [userStorage, setUserStorage] = useState([])
  const [storageLoading, setStorageLoading] = useState(true)
  const [storageError, setStorageError] = useState('')
  const [quotaDrafts, setQuotaDrafts] = useState({})
  const [quotaBusyId, setQuotaBusyId] = useState(null)
  const [quotaNotice, setQuotaNotice] = useState('')

  // Jobs
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

  const loadPendingRequests = useCallback(async () => {
    setPendingLoading(true)
    setPendingError('')

    try {
      const { data } = await client.get('/admin/users/pending')
      setPendingRequests(Array.isArray(data) ? data : [])
    } catch (err) {
      setPendingError(getApiErrorMessage(err))
    } finally {
      setPendingLoading(false)
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

      const rows = Array.isArray(usersResponse.data)
        ? usersResponse.data
        : []

      setUserStorage(rows)

      setQuotaDrafts((current) => {
        const next = { ...current }

        rows.forEach((item) => {
          if (next[item.userId] == null) {
            next[item.userId] = bytesToMbInput(
              item.storageQuotaBytes,
            )
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
    loadUsers()
  }, [loadUsers])

  useEffect(() => {
    loadPendingRequests()
  }, [loadPendingRequests])

  useEffect(() => {
    loadStorageStats()
  }, [loadStorageStats])

  useEffect(() => {
    loadJobs()
  }, [loadJobs])

  // Approve or reject a pending registration
  async function handleRegistrationAction(request, action) {
    const isApproving = action === 'approve'

    const confirmed = window.confirm(
      isApproving
        ? `Approve registration for ${request.username}?`
        : `Reject registration for ${request.username}?`,
    )

    if (!confirmed) return

    setPendingBusyId(request.id)
    setPendingError('')
    setPendingNotice('')

    try {
      const { data } = await client.patch(
        `/admin/users/${request.id}/${action}`,
      )

      setPendingRequests((current) =>
        current.filter((item) => item.id !== request.id),
      )

      setPendingNotice(
        isApproving
          ? `${request.username} has been approved successfully.`
          : `${request.username}'s registration has been rejected.`,
      )

      // Refresh registered users after approval or rejection.
      await loadUsers()

      // Keep the backend response available for debugging if needed.
      console.log('Registration action completed:', data)
    } catch (err) {
      setPendingError(getApiErrorMessage(err))
    } finally {
      setPendingBusyId(null)
    }
  }

  // Suspend or reactivate an existing account
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

  // Update an individual user's storage quota
  async function saveQuota(item) {
    const mbValue = Number(quotaDrafts[item.userId])

    if (!Number.isFinite(mbValue) || mbValue <= 0) {
      setStorageError('Enter a positive quota in MB.')
      return
    }

    const storageQuotaBytes = Math.round(
      mbValue * BYTES_PER_MB,
    )

    setQuotaBusyId(item.userId)
    setStorageError('')
    setQuotaNotice('')

    try {
      const { data } = await updateUserQuota(
        item.userId,
        storageQuotaBytes,
      )

      setUserStorage((current) =>
        current.map((row) =>
          row.userId === data.userId ? data : row,
        ),
      )

      setQuotaDrafts((current) => ({
        ...current,
        [item.userId]: bytesToMbInput(data.storageQuotaBytes),
      }))

      setQuotaNotice(
        `Updated ${item.username}'s quota to ${mbValue} MB.`,
      )
    } catch (err) {
      setStorageError(getApiErrorMessage(err))
    } finally {
      setQuotaBusyId(null)
    }
  }

  async function refreshDashboard() {
    await Promise.all([
      loadUsers(),
      loadPendingRequests(),
      loadStorageStats(),
      loadJobs(),
    ])
  }

  const activeCount = users.filter(
    (item) => item.status === 'ACTIVE',
  ).length

  const suspendedCount = users.filter(
    (item) => item.status === 'SUSPENDED',
  ).length

  return (
    <AdminFrame>
        <main className="dashboard-content">
          {/* Dashboard introduction */}
          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>User management</h2>
                <p>
                  Review registration requests and manage access
                  to your private cloud.
                </p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={refreshDashboard}
              >
                <RefreshCw size={16} />
                Refresh dashboard
              </button>
            </div>
          </section>

          {/* User statistics */}
          <section
            className="stat-grid"
            aria-label="User statistics"
          >
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

            <article className="stat-card">
              <Clock size={18} />
              <p className="stat-label">Pending requests</p>
              <p className="stat-value">
                {pendingRequests.length}
              </p>
            </article>
          </section>

          {/* Pending registration requests */}
          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>Pending registration requests</h2>
                <p>
                  Review applicant information before granting
                  access to Mini Private Cloud.
                </p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={loadPendingRequests}
                disabled={pendingLoading || pendingBusyId !== null}
              >
                <RefreshCw size={16} />
                Refresh requests
              </button>
            </div>

            {pendingError && (
              <div className="alert alert-error" role="alert">
                <Info size={18} />
                <span>{pendingError}</span>
              </div>
            )}

            {pendingNotice && (
              <div className="file-notice" role="status">
                <CheckCircle size={17} />
                <span>{pendingNotice}</span>
              </div>
            )}

            {pendingLoading ? (
              <p className="file-state">
                Loading registration requests…
              </p>
            ) : pendingRequests.length === 0 ? (
              <div className="file-empty">
                <CheckCircle size={32} />
                <h3>No pending requests</h3>
                <p>
                  All registration requests have been processed.
                </p>
              </div>
            ) : (
              <div className="admin-table-wrapper">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Applicant</th>
                      <th>Contact</th>
                      <th>Organization</th>
                      <th>Reason for access</th>
                      <th>Submitted</th>
                      <th>Actions</th>
                    </tr>
                  </thead>

                  <tbody>
                    {pendingRequests.map((request) => {
                      const isBusy = pendingBusyId === request.id

                      return (
                        <tr key={request.id}>
                          <td>
                            <strong>
                              {request.fullName || request.username}
                            </strong>
                            <small className="admin-user-email">
                              @{request.username}
                            </small>
                          </td>

                          <td>
                            <span>{request.email}</span>
                            <small className="admin-user-email">
                              {request.phoneNumber || 'No phone provided'}
                            </small>
                          </td>

                          <td>
                            {request.organization || '—'}
                          </td>

                          <td>
                            <div className="registration-reason">
                              {request.accessReason || 'No reason provided'}
                            </div>
                          </td>

                          <td>{formatDate(request.createdAt)}</td>

                          <td>
                            <div className="registration-actions">
                              <button
                                type="button"
                                className="ghost-button"
                                disabled={isBusy}
                                onClick={() =>
                                  handleRegistrationAction(
                                    request,
                                    'approve',
                                  )
                                }
                              >
                                <CheckCircle size={15} />
                                {isBusy ? 'Working…' : 'Approve'}
                              </button>

                              <button
                                type="button"
                                className="ghost-button danger-button"
                                disabled={isBusy}
                                onClick={() =>
                                  handleRegistrationAction(
                                    request,
                                    'reject',
                                  )
                                }
                              >
                                <XCircle size={15} />
                                Reject
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
          </section>

          {/* Cloud storage */}
          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>Cloud storage</h2>
                <p>
                  Monitor storage usage across your private cloud.
                </p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={loadStorageStats}
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
              <p className="file-state">
                Loading storage statistics…
              </p>
            ) : storageStats ? (
              <>
                <section
                  className="stat-grid"
                  aria-label="Cloud storage statistics"
                >
                  <article className="stat-card">
                    <Users size={18} />
                    <p className="stat-label">Total users</p>
                    <p className="stat-value">
                      {storageStats.totalUsers}
                    </p>
                  </article>

                  <article className="stat-card">
                    <Files size={18} />
                    <p className="stat-label">Total files</p>
                    <p className="stat-value">
                      {storageStats.totalFiles}
                    </p>
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
                    <p className="stat-value">
                      {storageStats.usersWithFiles}
                    </p>
                  </article>
                </section>

                <div className="file-section-heading">
                  <div>
                    <h2>User storage usage</h2>
                    <p>
                      Storage consumption and quotas for each
                      registered account. Quota input is in MB,
                      where 1 MB = 1,048,576 bytes.
                    </p>
                  </div>
                </div>

                {userStorage.length === 0 ? (
                  <p className="file-state">
                    No registered users found.
                  </p>
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
                          const percent =
                            Number(item.usagePercent) || 0

                          const barClass =
                            percent >= 100
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
                                <span className={userStatusClass(item.status)}>
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
                                    style={{
                                      width: `${Math.min(100, percent)}%`,
                                    }}
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

          {/* Registered users */}
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
                            <span className={userStatusClass(item.status)}>
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
                            ) : item.status === 'SUSPENDED' ? (
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
                            ) : (
                              <span className="admin-muted">
                                No action available
                              </span>
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

          {/* Compute jobs */}
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
              <p className="file-state">
                No jobs have been submitted yet.
              </p>
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

          {/* Security information */}
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
    </AdminFrame>
  )
}
