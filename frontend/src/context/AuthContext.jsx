
import { useMemo, useState } from 'react'
import { AuthContext } from './auth-context'

const STORAGE_KEY = 'minicloud.user'
const TOKEN_KEY = 'minicloud.token'

function normalizeUser(candidate) {
  if (!candidate || typeof candidate !== 'object') {
    return null
  }

  const { id, username, email, role } = candidate

  if (id == null || !username || !email || !role) {
    return null
  }

  return { id, username, email, role }
}

function readStoredUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? normalizeUser(JSON.parse(raw)) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser)

  const value = useMemo(() => {
    function persistUser(payload) {
      const nextUser = normalizeUser(payload)

      if (!nextUser) {
        throw new Error(
          'The server response did not include a complete user profile.',
        )
      }

      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(nextUser))
      } catch {
        // Keep the profile in React state if browser storage is unavailable.
      }

      setUser(nextUser)
    }

    function persistToken(token) {
      if (typeof token !== 'string' || !token.trim()) {
        throw new Error('The server did not return a valid login token.')
      }

      localStorage.setItem(TOKEN_KEY, token)
    }

    function logout() {
      try {
        localStorage.removeItem(STORAGE_KEY)
        localStorage.removeItem(TOKEN_KEY)
      } catch {
        // Ignore browser storage errors during logout.
      }

      setUser(null)
    }

    return { user, persistUser, persistToken, logout }
  }, [user])

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}
