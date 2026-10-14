import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertCircle, Eye, EyeOff, Loader2, Lock, Mail, User } from 'lucide-react'
import { register } from '../api/auth'
import { getApiErrorMessage } from '../api/errors'

const MIN_PASSWORD = 8
const MAX_PASSWORD = 72

export default function RegisterPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const next = {}
    if (!username.trim()) {
      next.username = 'Username is required.'
    }
    if (!email.trim()) {
      next.email = 'Email is required.'
    }
    if (!password) {
      next.password = 'Password is required.'
    } else if (password.length < MIN_PASSWORD || password.length > MAX_PASSWORD) {
      next.password = `Password must be ${MIN_PASSWORD}–${MAX_PASSWORD} characters.`
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
      await register({
        username: username.trim(),
        email: email.trim(),
        password,
      })

      navigate('/login', {
        replace: true,
        state: { registered: true },
      })
    } catch (error) {
      setFormError(getApiErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section>
      <h2>Create your account</h2>
      <p className="form-intro">Register with a username, email, and password.</p>

      {formError ? (
        <div className="alert alert-error" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <span>{formError}</span>
        </div>
      ) : null}

      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="username">Username</label>
          <div className="input-wrap">
            <User size={16} aria-hidden="true" />
            <input
              id="username"
              name="username"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              aria-invalid={Boolean(fieldErrors.username)}
              aria-describedby={fieldErrors.username ? 'username-error' : undefined}
              disabled={submitting}
            />
          </div>
          {fieldErrors.username ? (
            <p id="username-error" className="field-error">
              {fieldErrors.username}
            </p>
          ) : null}
        </div>

        <div className="field">
          <label htmlFor="email">Email</label>
          <div className="input-wrap">
            <Mail size={16} aria-hidden="true" />
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
              disabled={submitting}
            />
          </div>
          {fieldErrors.email ? (
            <p id="email-error" className="field-error">
              {fieldErrors.email}
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
              autoComplete="new-password"
              minLength={MIN_PASSWORD}
              maxLength={MAX_PASSWORD}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={
                fieldErrors.password ? 'password-error' : 'password-hint'
              }
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
          ) : (
            <p id="password-hint" className="field-hint">
              Must be {MIN_PASSWORD}–{MAX_PASSWORD} characters.
            </p>
          )}
        </div>

        <button className="primary-button" type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <Loader2 size={18} className="spin" aria-hidden="true" />
              Creating account…
            </>
          ) : (
            'Create account'
          )}
        </button>
      </form>

      <p className="switch-auth">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}
