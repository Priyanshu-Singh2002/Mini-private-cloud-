import { useMemo, useState } from 'react'
import { AuthContext } from './auth-context'

const STORAGE_KEY = 'minicloud.user'

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
    if (!raw) {
      return null
    }
    return normalizeUser(JSON.parse(raw))
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
        throw new Error('The server response did not include a complete user profile.')
      }

      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(nextUser))
      } catch {
        // Browser storage is convenience-only, not a session. Continue in this tab.
      }

      setUser(nextUser)
    }

    function logout() {
      try {
        localStorage.removeItem(STORAGE_KEY)
      } catch {
        // Ignore storage failures on logout.
      }
      setUser(null)
    }

    return { user, persistUser, logout }
  }, [user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
