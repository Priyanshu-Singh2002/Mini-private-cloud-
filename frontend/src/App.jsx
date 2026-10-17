
import { Navigate, Route, Routes } from 'react-router-dom'

import AuthLayout from './components/AuthLayout'
import AdminRoute from './components/AdminRoute'
import GuestRoute from './components/GuestRoute'
import ProtectedRoute from './components/ProtectedRoute'

import AdminDashboardPage from './pages/AdminDashboardPage'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import { useAuth } from './hooks/useAuth'

function HomeRedirect() {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return (
    <Navigate
      to={user.role === 'ADMIN' ? '/admin' : '/dashboard'}
      replace
    />
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<GuestRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardPage />} />
      </Route>

      <Route element={<AdminRoute />}>
        <Route path="/admin" element={<AdminDashboardPage />} />
      </Route>

      <Route path="/" element={<HomeRedirect />} />

      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  )
}
