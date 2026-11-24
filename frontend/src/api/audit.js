import client from './client'
import { getApiErrorMessage } from './errors'

function compact(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value != null),
  )
}

export function getAuditLogs(filters) {
  return client.get('/admin/audit-logs', {
    params: compact(filters),
  })
}

function filenameFrom(header) {
  if (!header) return ''
  const match = /filename="([^"]+)"/.exec(header)
  return match?.[1] || ''
}

export async function downloadAuditLogs(filters) {
  const response = await client.get('/admin/audit-logs/export', {
    params: compact(filters),
    responseType: 'blob',
  })

  const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filenameFrom(response.headers['content-disposition']) || 'audit-logs.csv'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function getAuditExportErrorMessage(error) {
  const data = error?.response?.data

  if (data instanceof Blob) {
    try {
      const text = await data.text()
      const parsed = text ? JSON.parse(text) : null
      return getApiErrorMessage({
        response: {
          status: error.response.status,
          data: parsed,
        },
      })
    } catch {
      return getApiErrorMessage(error)
    }
  }

  return getApiErrorMessage(error)
}
