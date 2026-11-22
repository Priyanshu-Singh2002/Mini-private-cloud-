
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  AlertCircle,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  Mail,
  User,
  Phone,
  Building2,
  FileText,
} from 'lucide-react'

import { register } from '../api/auth'
import { getApiErrorMessage } from '../api/errors'

const MIN_PASSWORD = 8
const MAX_PASSWORD = 72

export default function RegisterPage() {
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [organization, setOrganization] = useState('')
  const [accessReason, setAccessReason] = useState('')

  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const next = {}

    if (!fullName.trim()) {
      next.fullName = 'Full name is required.'
    } else if (fullName.trim().length > 100) {
      next.fullName = 'Full name cannot exceed 100 characters.'
    }

    if (!username.trim()) {
      next.username = 'Username is required.'
    } else if (username.trim().length > 50) {
      next.username = 'Username cannot exceed 50 characters.'
    }

    if (!email.trim()) {
      next.email = 'Email is required.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      next.email = 'Enter a valid email address.'
    }

    if (!phoneNumber.trim()) {
      next.phoneNumber = 'Phone number is required.'
    } else if (!/^[0-9+\-\s()]{7,30}$/.test(phoneNumber.trim())) {
      next.phoneNumber = 'Enter a valid phone number.'
    }

    if (!organization.trim()) {
      next.organization = 'College or organization is required.'
    } else if (organization.trim().length > 100) {
      next.organization = 'Organization cannot exceed 100 characters.'
    }

    if (!accessReason.trim()) {
      next.accessReason = 'Please explain why you need access.'
    } else if (accessReason.trim().length > 1000) {
      next.accessReason = 'Reason cannot exceed 1000 characters.'
    }

    if (!password) {
      next.password = 'Password is required.'
    } else if (
      password.length < MIN_PASSWORD ||
      password.length > MAX_PASSWORD
    ) {
      next.password = `Password must be ${MIN_PASSWORD}-${MAX_PASSWORD} characters.`
    }

    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    if (!validate()) return

    setSubmitting(true)

    try {
      await register({
        username: username.trim(),
        email: email.trim(),
        password,
        fullName: fullName.trim(),
        phoneNumber: phoneNumber.trim(),
        organization: organization.trim(),
        accessReason: accessReason.trim(),
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
    <section className="register-page">
      <h2>Create your account</h2>

      <p className="form-intro">
        Submit your details to request access to Mini Private Cloud.
        Your account will be available after administrator approval.
      </p>

      {formError && (
        <div className="alert alert-error" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <span>{formError}</span>
        </div>
      )}

      <form className="register-form" onSubmit={handleSubmit} noValidate>
        <div className="register-fields-grid">

          <div className="field">
            <label htmlFor="fullName">Full name</label>
            <div className="input-wrap">
              <User size={16} aria-hidden="true" />
              <input
                id="fullName"
                name="fullName"
                type="text"
                autoComplete="name"
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
                aria-invalid={Boolean(fieldErrors.fullName)}
                disabled={submitting}
                maxLength={100}
              />
            </div>
            {fieldErrors.fullName && (
              <p className="field-error">{fieldErrors.fullName}</p>
            )}
          </div>

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
                disabled={submitting}
                maxLength={50}
              />
            </div>
            {fieldErrors.username && (
              <p className="field-error">{fieldErrors.username}</p>
            )}
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
                disabled={submitting}
                maxLength={150}
              />
            </div>
            {fieldErrors.email && (
              <p className="field-error">{fieldErrors.email}</p>
            )}
          </div>

          <div className="field">
            <label htmlFor="phoneNumber">Phone number</label>
            <div className="input-wrap">
              <Phone size={16} aria-hidden="true" />
              <input
                id="phoneNumber"
                name="phoneNumber"
                type="tel"
                autoComplete="tel"
                value={phoneNumber}
                onChange={(event) => setPhoneNumber(event.target.value)}
                aria-invalid={Boolean(fieldErrors.phoneNumber)}
                disabled={submitting}
                maxLength={30}
              />
            </div>
            {fieldErrors.phoneNumber && (
              <p className="field-error">{fieldErrors.phoneNumber}</p>
            )}
          </div>

          <div className="field">
            <label htmlFor="organization">College / Organization</label>
            <div className="input-wrap">
              <Building2 size={16} aria-hidden="true" />
              <input
                id="organization"
                name="organization"
                type="text"
                value={organization}
                onChange={(event) => setOrganization(event.target.value)}
                aria-invalid={Boolean(fieldErrors.organization)}
                disabled={submitting}
                maxLength={100}
              />
            </div>
            {fieldErrors.organization && (
              <p className="field-error">{fieldErrors.organization}</p>
            )}
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
              <p className="field-error">{fieldErrors.password}</p>
            ) : (
              <p className="field-hint">
                Must be {MIN_PASSWORD}-{MAX_PASSWORD} characters.
              </p>
            )}
          </div>

          <div className="field register-reason-field">
            <label htmlFor="accessReason">Why do you need access?</label>

            <div className="input-wrap textarea-wrap">
              <FileText size={16} aria-hidden="true" />
              <textarea
                id="accessReason"
                name="accessReason"
                rows={3}
                value={accessReason}
                onChange={(event) => setAccessReason(event.target.value)}
                aria-invalid={Boolean(fieldErrors.accessReason)}
                disabled={submitting}
                maxLength={1000}
                placeholder="Explain how you plan to use the Mini Private Cloud platform..."
              />
            </div>

            {fieldErrors.accessReason ? (
              <p className="field-error">{fieldErrors.accessReason}</p>
            ) : (
              <p className="field-hint">Maximum 1000 characters.</p>
            )}
          </div>

        </div>

        <button
          className="primary-button"
          type="submit"
          disabled={submitting}
        >
          {submitting ? (
            <>
              <Loader2 size={18} className="spin" aria-hidden="true" />
              Submitting request...
            </>
          ) : (
            'Submit registration request'
          )}
        </button>
      </form>

      <p className="switch-auth">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}
