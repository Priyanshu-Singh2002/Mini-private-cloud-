
import { useCallback, useEffect, useState } from 'react'
import {
  Cloud,
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
import { getApiErrorMessage } from '../api/errors'

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

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [busyFileId, setBusyFileId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadFiles = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setLoading(true)
    }

    setError('')

    try {
      const { data } = await getFiles()
      setFiles(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      if (showLoading) {
        setLoading(false)
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

    return () => {
      cancelled = true
    }
  }, [])

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
      await loadFiles()
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
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setBusyFileId(null)
    }
  }

  const initials = (user?.username || '?').slice(0, 2).toUpperCase()
  const totalBytes = files.reduce((sum, file) => sum + (file.fileSize || 0), 0)

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
              <p className="stat-label">Total file size</p>
              <p className="stat-value">{formatBytes(totalBytes)}</p>
            </article>

            <article className="stat-card">
              <Shield size={18} />
              <p className="stat-label">Account role</p>
              <p className="stat-value">{user?.role}</p>
            </article>
          </section>

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
