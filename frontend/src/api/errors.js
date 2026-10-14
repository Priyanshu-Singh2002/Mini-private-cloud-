const GENERIC_HTTP_LABELS = new Set([
  'bad request',
  'conflict',
  'error',
  'forbidden',
  'internal server error',
  'not found',
  'unauthorized',
])

function asReadableMessage(value) {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed || ''
  }

  if (Array.isArray(value)) {
    return value.map(asReadableMessage).filter(Boolean).join(' ')
  }

  return ''
}

function asServerMessage(data) {
  const candidates = [
    asReadableMessage(data),
    asReadableMessage(data?.detail),
    asReadableMessage(data?.message),
    asReadableMessage(data?.title),
    asReadableMessage(data?.error),
  ]

  for (const candidate of candidates) {
    if (candidate && !GENERIC_HTTP_LABELS.has(candidate.toLowerCase())) {
      return candidate
    }
  }

  return ''
}

export function getApiErrorMessage(error) {
  if (!error || typeof error !== 'object') {
    return 'Something went wrong. Please try again.'
  }

  if (!error.response) {
    if (error.request) {
      return 'Unable to reach the server. Confirm the backend is running on port 8080 and try again.'
    }
    return asReadableMessage(error.message) || 'Something went wrong. Please try again.'
  }

  const { status, data } = error.response
  const serverMessage = asServerMessage(data)

  if (status === 409) {
    return serverMessage || 'An account with this username or email already exists.'
  }

  if (status === 401) {
    return serverMessage || 'Invalid username/email or password.'
  }

  if (status === 400) {
    return serverMessage || 'Please check your input and try again.'
  }

  if (status >= 500) {
    return 'The server encountered an error. Please try again later.'
  }

  return serverMessage || 'Something went wrong. Please try again.'
}
