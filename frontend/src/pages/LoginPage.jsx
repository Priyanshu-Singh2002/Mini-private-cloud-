import { AlertCircle, Eye, EyeOff, Loader2, Lock, User } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { getApiErrorMessage } from '../api/errors'
import { useAuth } from '../hooks/useAuth'

export default function LoginPage() {
  const navigate = useNavigate()
  const { persistUser, persistToken } = useAuth()
  const [usernameOrEmail, setUsernameOrEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const next = {}
    if (!usernameOrEmail.trim()) {
      next.usernameOrEmail = 'Username or email is required.'
    }
    if (!password) {
      next.password = 'Password is required.'
    }
    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')
    if (!validate()) {
      return
    }

    setSubmitting(true)
    try {
      const { data } = await login({
        usernameOrEmail: usernameOrEmail.trim(),
        password,
      })

      persistToken(data.token)
      persistUser(data)
      navigate(data.role === 'ADMIN' ? '/admin' : '/dashboard', {
        replace: true,
      })
    } catch (error) {
      setFormError(getApiErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section>
      <h2>Welcome back</h2>
      <p className="form-intro">Sign in with your username or email.</p>

      {formError ? (
        <div className="alert alert-error" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <span>{formError}</span>
        </div>
      ) : null}

      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="usernameOrEmail">Username or email</label>
          <div className="input-wrap">
            <User size={16} aria-hidden="true" />
            <input
              id="usernameOrEmail"
              name="usernameOrEmail"
              type="text"
              autoComplete="username"
              value={usernameOrEmail}
              onChange={(event) => setUsernameOrEmail(event.target.value)}
              aria-invalid={Boolean(fieldErrors.usernameOrEmail)}
              aria-describedby={
                fieldErrors.usernameOrEmail ? 'usernameOrEmail-error' : undefined
              }
              disabled={submitting}
            />
          </div>
          {fieldErrors.usernameOrEmail ? (
            <p id="usernameOrEmail-error" className="field-error">
              {fieldErrors.usernameOrEmail}
            </p>
          ) : null}
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <div className="input-wrap">
            <Lock size={16} aria-hidden="true" />
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              disabled={submitting}
            />
            <button
              type="button"
              className="icon-button"
              onClick={() => setShowPassword((open) => !open)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {fieldErrors.password ? (
            <p id="password-error" className="field-error">
              {fieldErrors.password}
            </p>
          ) : null}
        </div>

        <button className="primary-button" type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <Loader2 size={18} className="spin" aria-hidden="true" />
              Signing in…
            </>
          ) : (
            'Sign in'
          )}
        </button>
      </form>

      <p className="switch-auth">
        New here? <Link to="/register">Create an account</Link>
      </p>
    </section>
  )
}
