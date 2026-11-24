import { useCallback, useEffect, useState } from 'react'
import {
  Clock,
  Download,
  Info,
  RefreshCw,
  ScrollText,
  Search,
} from 'lucide-react'

import {
  downloadAuditLogs,
  getAuditExportErrorMessage,
  getAuditLogs,
} from '../api/audit'
import { getApiErrorMessage } from '../api/errors'
import AdminFrame from '../components/AdminFrame'

const PAGE_SIZES = [10, 20, 50]

function formatDate(value) {
  if (!value) return '—'

  const date = new Date(value)

  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function display(value) {
  if (value == null || value === '') return '—'
  return value
}

export default function AdminAuditHistoryPage() {
  const [searchInput, setSearchInput] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)

  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => {
      const next = searchInput.trim()
      setAppliedSearch((current) => {
        if (current !== next) {
          setPage(0)
        }
        return next
      })
    }, 350)

    return () => clearTimeout(timer)
  }, [searchInput])

  const load = useCallback(async () => {
    if (fromDate && toDate && fromDate > toDate) {
      setError('From date must be on or before to date.')
      setLoading(false)
      return
    }

    setLoading(true)
    setError('')

    try {
      const { data: body } = await getAuditLogs({
        page,
        size,
        action,
        entityType,
        search: appliedSearch,
        fromDate,
        toDate,
      })
      setData(body)
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [page, size, action, entityType, appliedSearch, fromDate, toDate])

  useEffect(() => {
    load()
  }, [load])

  function clearFilters() {
    setSearchInput('')
    setAppliedSearch('')
    setAction('')
    setEntityType('')
    setFromDate('')
    setToDate('')
    setPage(0)
  }

  async function handleExport() {
    if (fromDate && toDate && fromDate > toDate) {
      setError('From date must be on or before to date.')
      return
    }

    setExporting(true)
    setError('')

    try {
      await downloadAuditLogs({
        action,
        entityType,
        search: appliedSearch,
        fromDate,
        toDate,
      })
    } catch (err) {
      setError(await getAuditExportErrorMessage(err))
    } finally {
      setExporting(false)
    }
  }

  const records = Array.isArray(data?.content) ? data.content : []
  const actions = Array.isArray(data?.actions) ? data.actions : []
  const entityTypes = Array.isArray(data?.entityTypes) ? data.entityTypes : []
  const totalPages = Number(data?.totalPages) || 0
  const totalRecords = data?.totalRecords
  const latest = data?.latestActivity
  const currentPage = totalPages === 0 ? 0 : page + 1

  return (
    <AdminFrame>
      <main className="dashboard-content">
        <section className="welcome-card">
          <div className="file-section-heading">
            <div>
              <h2>Audit history</h2>
              <p>
                Review administrator actions recorded for user
                management and storage quotas.
              </p>
            </div>

            <div className="file-actions">
              <button
                type="button"
                className="ghost-button"
                onClick={handleExport}
                disabled={exporting}
              >
                <Download size={16} />
                {exporting ? 'Exporting…' : 'Export CSV'}
              </button>

              <button
                type="button"
                className="ghost-button"
                onClick={load}
                disabled={loading}
              >
                <RefreshCw size={16} />
                Refresh
              </button>
            </div>
          </div>
        </section>

        <section className="stat-grid" aria-label="Audit summary">
          <article className="stat-card">
            <ScrollText size={18} />
            <p className="stat-label">Total audit records</p>
            <p className="stat-value">
              {totalRecords == null ? '—' : totalRecords}
            </p>
          </article>

          <article className="stat-card">
            <Search size={18} />
            <p className="stat-label">Matching records</p>
            <p className="stat-value">
              {data?.totalElements == null ? '—' : data.totalElements}
            </p>
          </article>

          <article className="stat-card">
            <Clock size={18} />
            <p className="stat-label">Latest activity</p>
            <p className="stat-value">
              {latest
                ? `${latest.actorUsername || 'Unknown'} · ${latest.action}`
                : '—'}
            </p>
            <p className="stat-label">
              {latest ? formatDate(latest.createdAt) : 'No activity yet'}
            </p>
          </article>
        </section>

        <section className="welcome-card">
          <form
            className="audit-filters"
            onSubmit={(event) => event.preventDefault()}
          >
            <label>
              Search
              <input
                type="search"
                value={searchInput}
                maxLength={200}
                placeholder="Username, unique ID, or description"
                onChange={(event) => setSearchInput(event.target.value)}
              />
            </label>

            <label>
              Action
              <select
                value={action}
                onChange={(event) => {
                  setAction(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All actions</option>
                {actions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Entity type
              <select
                value={entityType}
                onChange={(event) => {
                  setEntityType(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All entity types</option>
                {entityTypes.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>

            <label>
              From date
              <input
                type="date"
                value={fromDate}
                onChange={(event) => {
                  setFromDate(event.target.value)
                  setPage(0)
                }}
              />
            </label>

            <label>
              To date
              <input
                type="date"
                value={toDate}
                onChange={(event) => {
                  setToDate(event.target.value)
                  setPage(0)
                }}
              />
            </label>

            <div className="audit-filter-actions">
              <button
                type="button"
                className="ghost-button"
                onClick={clearFilters}
              >
                Clear filters
              </button>
            </div>
          </form>

          {error && (
            <div className="alert alert-error" role="alert">
              <Info size={18} />
              <span>{error}</span>
            </div>
          )}

          {loading ? (
            <p className="file-state">Loading audit history…</p>
          ) : records.length === 0 ? (
            <div className="file-empty">
              <ScrollText size={32} />
              <h3>No audit records</h3>
              <p>No activity matches the current filters.</p>
            </div>
          ) : (
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Date and time</th>
                    <th>Actor username</th>
                    <th>Actor unique ID</th>
                    <th>Action</th>
                    <th>Entity type</th>
                    <th>Entity ID</th>
                    <th>Entity unique ID</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((record) => (
                    <tr key={record.id}>
                      <td>{formatDate(record.createdAt)}</td>
                      <td>{display(record.actorUsername)}</td>
                      <td className="audit-id">{display(record.actorUniqueId)}</td>
                      <td>{display(record.action)}</td>
                      <td>{display(record.entityType)}</td>
                      <td>{display(record.entityId)}</td>
                      <td className="audit-id">{display(record.entityUniqueId)}</td>
                      <td className="audit-description">
                        {display(record.description)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="audit-pagination">
            <p>
              Page {currentPage} of {totalPages}
            </p>

            <div className="file-actions">
              <button
                type="button"
                className="ghost-button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={loading || page <= 0}
              >
                Previous page
              </button>

              <button
                type="button"
                className="ghost-button"
                onClick={() => setPage((current) => current + 1)}
                disabled={loading || page + 1 >= totalPages}
              >
                Next page
              </button>
            </div>

            <label className="audit-page-size">
              Page size
              <select
                value={size}
                onChange={(event) => {
                  setSize(Number(event.target.value))
                  setPage(0)
                }}
              >
                {PAGE_SIZES.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>
      </main>
    </AdminFrame>
  )
}
