import { Cloud } from 'lucide-react'
import { Link, Outlet } from 'react-router-dom'

export default function AuthLayout() {
  return (
    <div className="auth-shell">
      <aside className="auth-brand" aria-hidden="false">
        <div className="auth-brand-inner">
          <div className="brand-mark">
            <Cloud size={28} strokeWidth={1.75} />
          </div>
          <p className="eyebrow">Mini Private Cloud</p>
          <h1>Your infrastructure, in one place.</h1>
          <p className="lede">
            Sign in to manage your private cloud workspace. Authentication is
            handled by the existing Spring Boot API.
          </p>
        </div>
      </aside>
      <main className="auth-panel">
        <div className="auth-panel-inner">
          <Link to="/login" className="mobile-brand">
            <Cloud size={22} strokeWidth={1.75} />
            Mini Cloud
          </Link>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
