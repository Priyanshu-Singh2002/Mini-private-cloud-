export function getApiErrorMessage(error) {
  if (!error.response) {
    if (error.request) {
      return 'Unable to reach the server. Confirm the backend is running on port 8080 and try again.'
    }
    return error.message || 'Something went wrong. Please try again.'
  }

  const { status, data } = error.response
  const serverMessage =
    (typeof data === 'string' && data) ||
    data?.detail ||
    data?.message ||
    data?.title ||
    data?.error

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
