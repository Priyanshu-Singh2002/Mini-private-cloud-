
import { useCallback, useEffect, useState } from 'react'
import {
  Cloud,
  Cpu,
  Download,
  File,
  FileText,
  Info,
  LayoutDashboard,
  LogOut,
  RefreshCw,
  Shield,
  Trash2,
  Upload,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { deleteFile, downloadFile, getFiles, uploadFile } from '../api/files'
import { getMyStorageUsage } from '../api/storage'
import { cancelJob, getMyJobs, submitJob } from '../api/jobs'
import { getApiErrorMessage } from '../api/errors'

const BYTES_PER_MB = 1024 * 1024

function formatBytes(bytes) {
  if (bytes == null || bytes === 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1,
  )

  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 2)} ${units[index]}`
}

function formatDate(value) {
  if (!value) return '—'

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function jobStatusClass(status) {
  const value = String(status || '').toLowerCase()
  return `job-status is-${value}`
}

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [busyFileId, setBusyFileId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [usage, setUsage] = useState(null)
  const [jobs, setJobs] = useState([])
  const [jobsLoading, setJobsLoading] = useState(true)
  const [jobType, setJobType] = useState('TEXT_STATS')
  const [jobPriority, setJobPriority] = useState('MEDIUM')
  const [jobFileId, setJobFileId] = useState('')
  const [jobValues, setJobValues] = useState('1, 2, 3, 4, 5')
  const [submittingJob, setSubmittingJob] = useState(false)
  const [busyJobId, setBusyJobId] = useState(null)
  const [jobError, setJobError] = useState('')
  const [jobNotice, setJobNotice] = useState('')

  const loadUsage = useCallback(async () => {
    try {
      const { data } = await getMyStorageUsage()
      setUsage(data)
    } catch {
      setUsage(null)
    }
  }, [])

  const loadFiles = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setLoading(true)
    }

    setError('')

    try {
      const { data } = await getFiles()
      setFiles(Array.isArray(data) ? data : [])
      await loadUsage()
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      if (showLoading) {
        setLoading(false)
      }
    }
  }, [loadUsage])

  const loadJobs = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setJobsLoading(true)
    }

    try {
      const { data } = await getMyJobs()
      setJobs(Array.isArray(data) ? data : [])
    } catch (err) {
      setJobError(getApiErrorMessage(err))
    } finally {
      if (showLoading) {
        setJobsLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    async function fetchInitialFiles() {
      try {
        const { data } = await getFiles()

        if (!cancelled) {
          setFiles(Array.isArray(data) ? data : [])
          setError('')
          getMyStorageUsage()
            .then(({ data: usageData }) => {
              if (!cancelled) {
                setUsage(usageData)
              }
            })
            .catch(() => {})
        }
      } catch (err) {
        if (!cancelled) {
          setError(getApiErrorMessage(err))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    fetchInitialFiles()
    loadJobs()

    return () => {
      cancelled = true
    }
  }, [loadJobs])

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  async function handleUpload(event) {
    const input = event.target
    const selectedFile = input.files?.[0]

    if (!selectedFile) return

    setUploading(true)
    setError('')
    setNotice('')

    try {
      await uploadFile(selectedFile)
      setNotice(`"${selectedFile.name}" uploaded successfully.`)
      await loadFiles(false)
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setUploading(false)
      input.value = ''
    }
  }

  async function handleDownload(file) {
    setBusyFileId(file.id)
    setError('')
    setNotice('')

    try {
      const { data } = await downloadFile(file.id)
      const url = URL.createObjectURL(data)
      const link = document.createElement('a')

      link.href = url
      link.download = file.originalFilename || 'download'
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)

      setNotice(`Download started for "${file.originalFilename}".`)
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setBusyFileId(null)
    }
  }

  async function handleSubmitJob(event) {
    event.preventDefault()
    setJobError('')
    setJobNotice('')
    setSubmittingJob(true)

    try {
      const payload = {
        jobType,
        priority: jobPriority,
      }

      if (jobType === 'BATCH_SUM') {
        const values = jobValues
          .split(/[\s,]+/)
          .map((item) => item.trim())
          .filter(Boolean)
          .map(Number)

        if (values.length === 0 || values.some((value) => !Number.isFinite(value))) {
          setJobError('Enter a bounded list of numbers, separated by commas.')
          return
        }

        payload.values = values
      } else {
        if (!jobFileId) {
          setJobError('Select one of your uploaded files as job input.')
          return
        }

        payload.fileId = Number(jobFileId)
      }

      const { data } = await submitJob(payload)
      setJobNotice(`Job ${data.id} queued as ${data.status}.`)
      await loadJobs(false)
    } catch (err) {
      setJobError(getApiErrorMessage(err))
    } finally {
      setSubmittingJob(false)
    }
  }

  async function handleCancelJob(job) {
    setBusyJobId(job.id)
    setJobError('')
    setJobNotice('')

    try {
      await cancelJob(job.id)
      setJobNotice(`Job ${job.id} was cancelled.`)
      await loadJobs(false)
    } catch (err) {
      setJobError(getApiErrorMessage(err))
    } finally {
      setBusyJobId(null)
    }
  }

  async function handleDelete(file) {
    const filename = file.originalFilename || 'this file'

    if (!window.confirm(`Delete "${filename}" permanently?`)) return

    setBusyFileId(file.id)
    setError('')
    setNotice('')

    try {
      await deleteFile(file.id)
      setFiles((current) => current.filter((item) => item.id !== file.id))
      setNotice(`"${filename}" was deleted.`)
      await loadUsage()
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setBusyFileId(null)
    }
  }

  const initials = (user?.username || '?').slice(0, 2).toUpperCase()
  const totalBytes = usage?.usedBytes
    ?? files.reduce((sum, file) => sum + (file.fileSize || 0), 0)
  const quotaBytes = usage?.storageQuotaBytes ?? 0
  const remainingBytes = usage?.remainingBytes
    ?? Math.max(0, quotaBytes - totalBytes)
  const usagePercent = usage?.usagePercent
    ?? (quotaBytes > 0 ? Math.min(100, (totalBytes * 100) / quotaBytes) : 0)
  const usageBarClass = usagePercent >= 100
    ? 'usage-bar-fill is-full'
    : usagePercent >= 80
      ? 'usage-bar-fill is-high'
      : 'usage-bar-fill'

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
          Your private workspace for uploading, managing, and downloading files.
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
              <span className="avatar" aria-hidden="true">{initials}</span>
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
            <p>Manage your files in your Mini Cloud workspace.</p>
          </section>

          <section className="stat-grid" aria-label="Storage overview">
            <article className="stat-card">
              <FileText size={18} />
              <p className="stat-label">Stored files</p>
              <p className="stat-value">{files.length}</p>
            </article>

            <article className="stat-card">
              <Cloud size={18} />
              <p className="stat-label">Storage used</p>
              <p className="stat-value">{formatBytes(totalBytes)}</p>
            </article>

            <article className="stat-card">
              <Shield size={18} />
              <p className="stat-label">Account role</p>
              <p className="stat-value">{user?.role}</p>
            </article>
          </section>

          {quotaBytes > 0 && (
            <section className="welcome-card">
              <h2>Storage quota</h2>
              <p>
                Used {formatBytes(totalBytes)} of {formatBytes(quotaBytes)}.
                Remaining {formatBytes(remainingBytes)}.
              </p>
              <div
                className="usage-bar"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.round(usagePercent)}
              >
                <div
                  className={usageBarClass}
                  style={{ width: `${Math.min(100, usagePercent)}%` }}
                />
              </div>
              <div className="usage-meta">
                <span>{usagePercent.toFixed(1)}% used</span>
                <span>
                  Quota {(quotaBytes / BYTES_PER_MB).toFixed(0)} MB
                </span>
              </div>
            </section>
          )}

          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>Your files</h2>
                <p>Upload a file or manage files already in your cloud.</p>
              </div>

              <div className="file-actions">
                <button
                  type="button"
                  className="ghost-button"
                  onClick={loadFiles}
                  disabled={loading || uploading}
                >
                  <RefreshCw size={16} />
                  Refresh
                </button>

                <label className="primary-button upload-button">
                  <Upload size={16} />
                  {uploading ? 'Uploading…' : 'Upload file'}
                  <input
                    type="file"
                    onChange={handleUpload}
                    disabled={uploading}
                    hidden
                  />
                </label>
              </div>
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
              <p className="file-state">Loading your files…</p>
            ) : files.length === 0 ? (
              <div className="file-empty">
                <File size={32} />
                <h3>No files yet</h3>
                <p>Choose Upload file to add your first file.</p>
              </div>
            ) : (
              <div className="file-list">
                {files.map((file) => (
                  <article className="file-row" key={file.id}>
                    <div className="file-icon">
                      <FileText size={22} />
                    </div>

                    <div className="file-details">
                      <strong>{file.originalFilename}</strong>
                      <small>
                        {formatBytes(file.fileSize)} · {formatDate(file.uploadedAt)}
                      </small>
                    </div>

                    <div className="file-row-actions">
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`Download ${file.originalFilename}`}
                        title="Download"
                        onClick={() => handleDownload(file)}
                        disabled={busyFileId === file.id}
                      >
                        <Download size={17} />
                      </button>

                      <button
                        type="button"
                        className="icon-button danger-button"
                        aria-label={`Delete ${file.originalFilename}`}
                        title="Delete"
                        onClick={() => handleDelete(file)}
                        disabled={busyFileId === file.id}
                      >
                        <Trash2 size={17} />
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="welcome-card">
            <div className="file-section-heading">
              <div>
                <h2>Job processing</h2>
                <p>
                  Submit a predefined task. Jobs are queued by priority and
                  run in the background.
                </p>
              </div>

              <button
                type="button"
                className="ghost-button"
                onClick={() => loadJobs()}
                disabled={jobsLoading || submittingJob}
              >
                <RefreshCw size={16} />
                Refresh jobs
              </button>
            </div>

            {jobError && (
              <div className="alert alert-error" role="alert">
                <Info size={18} />
                <span>{jobError}</span>
              </div>
            )}

            {jobNotice && (
              <div className="file-notice" role="status">
                {jobNotice}
              </div>
            )}

            <form className="job-form" onSubmit={handleSubmitJob}>
              <div className="job-form-grid">
                <div className="field">
                  <label htmlFor="jobType">Job type</label>
                  <select
                    id="jobType"
                    value={jobType}
                    onChange={(event) => setJobType(event.target.value)}
                  >
                    <option value="TEXT_STATS">Text word and line count</option>
                    <option value="CSV_STATS">CSV row statistics</option>
                    <option value="BATCH_SUM">Bounded batch sum</option>
                  </select>
                </div>

                <div className="field">
                  <label htmlFor="jobPriority">Priority</label>
                  <select
                    id="jobPriority"
                    value={jobPriority}
                    onChange={(event) => setJobPriority(event.target.value)}
                  >
                    <option value="HIGH">HIGH</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="LOW">LOW</option>
                  </select>
                </div>
              </div>

              {jobType === 'BATCH_SUM' ? (
                <div className="field">
                  <label htmlFor="jobValues">Numbers to sum</label>
                  <textarea
                    id="jobValues"
                    value={jobValues}
                    onChange={(event) => setJobValues(event.target.value)}
                    placeholder="1, 2, 3, 4"
                  />
                  <p className="field-hint">
                    Comma-separated numbers. The backend limits list size.
                  </p>
                </div>
              ) : (
                <div className="field">
                  <label htmlFor="jobFileId">Input file</label>
                  <select
                    id="jobFileId"
                    value={jobFileId}
                    onChange={(event) => setJobFileId(event.target.value)}
                  >
                    <option value="">Select a file you uploaded</option>
                    {files.map((file) => (
                      <option key={file.id} value={file.id}>
                        {file.originalFilename}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <button
                type="submit"
                className="primary-button"
                disabled={submittingJob}
              >
                <Cpu size={16} />
                {submittingJob ? 'Submitting…' : 'Submit job'}
              </button>
            </form>

            {jobsLoading ? (
              <p className="file-state">Loading jobs…</p>
            ) : jobs.length === 0 ? (
              <p className="file-state">No jobs yet.</p>
            ) : (
              <div className="admin-table-wrapper">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Type</th>
                      <th>Priority</th>
                      <th>Status</th>
                      <th>Submitted</th>
                      <th>Result</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {jobs.map((job) => (
                      <tr key={job.id}>
                        <td>{job.id}</td>
                        <td>{job.jobType}</td>
                        <td>{job.priority}</td>
                        <td>
                          <span className={jobStatusClass(job.status)}>
                            {job.status}
                          </span>
                        </td>
                        <td>{formatDate(job.submittedAt)}</td>
                        <td>
                          {job.status === 'FAILED' && job.errorMessage}
                          {job.status === 'COMPLETED' && (
                            <pre className="job-result">{job.result}</pre>
                          )}
                          {job.status !== 'FAILED' && job.status !== 'COMPLETED' && '—'}
                        </td>
                        <td>
                          {job.status === 'QUEUED' && (
                            <button
                              type="button"
                              className="ghost-button"
                              disabled={busyJobId === job.id}
                              onClick={() => handleCancelJob(job)}
                            >
                              Cancel
                            </button>
                          )}
                        </td>
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
              <strong>Private file storage</strong>
              <p>
                File operations use your authenticated backend. The server
                determines which files belong to your account.
              </p>
            </div>
          </section>
        </main>
      </div>
    </div>
  )
}
