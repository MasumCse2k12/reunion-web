import { Component, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppProvider, useApp } from './lib/store'
import { AdminProvider, useAdmin } from './lib/adminStore'
import Layout from './components/Layout'
import { Spinner } from './components/ui'

class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null }
  static getDerivedStateFromError(error: Error) { return { error } }
  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-8 text-center">
          <p className="text-2xl">⚠️</p>
          <p className="font-bold text-ink-900">Something went wrong</p>
          <p className="text-sm text-ink-500">{(this.state.error as Error).message}</p>
          <button
            className="mt-2 rounded-xl bg-brand-600 px-5 py-2.5 font-semibold text-white"
            onClick={() => { this.setState({ error: null }); window.location.reload() }}
          >
            Reload
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
import Landing from './pages/Landing'
import Login from './pages/Login'
import Signup from './pages/Signup'
import Dashboard from './pages/Dashboard'
import Guests from './pages/Guests'
import Batches from './pages/Batches'
import BatchDetail from './pages/BatchDetail'
import Profile from './pages/Profile'
import Privacy from './pages/Privacy'
import DeleteAccount from './pages/DeleteAccount'
import AdminLogin from './pages/admin/AdminLogin'
import AdminLayout from './pages/admin/AdminLayout'
import AdminOverview from './pages/admin/AdminOverview'
import AdminClaims from './pages/admin/AdminClaims'
import AdminMembers from './pages/admin/AdminMembers'
import AdminPayments from './pages/admin/AdminPayments'
import AdminAccounts from './pages/admin/AdminAccounts'

function Protected({ children }: { children: React.ReactNode }) {
  const { user, ready, t } = useApp()
  if (!ready) return <Spinner label={t('common.loading')} />
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

/** A member session is not an admin session — this gate only accepts the latter. */
function AdminProtected({ children }: { children: React.ReactNode }) {
  const { t } = useApp()
  const { admin, ready } = useAdmin()
  if (!ready) return <Spinner label={t('common.loading')} />
  if (!admin) return <Navigate to="/admin/login" replace />
  return <>{children}</>
}

function ScrollTop() {
  return null
}

export default function App() {
  return (
    <ErrorBoundary>
    <AppProvider>
      <AdminProvider>
        <BrowserRouter>
          <ScrollTop />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />

            {/* Both public on purpose: Google Play requires the policy and the
                deletion route to be reachable without installing the app or
                signing in. DeleteAccount does its own phone + code check. */}
            <Route path="/privacy" element={<Privacy />} />
            <Route path="/privacy-policy" element={<Privacy />} />
            <Route path="/account-deletion" element={<DeleteAccount />} />

            <Route
              path="/app"
              element={
                <Protected>
                  <Layout />
                </Protected>
              }
            >
              <Route index element={<Dashboard />} />
              <Route path="guests" element={<Guests />} />
              <Route path="batches" element={<Batches />} />
              <Route path="batches/:year" element={<BatchDetail />} />
              <Route path="profile" element={<Profile />} />
            </Route>

            {/* ---- Admin portal ---- */}
            <Route path="/admin/login" element={<AdminLogin />} />
            <Route
              path="/admin"
              element={
                <AdminProtected>
                  <AdminLayout />
                </AdminProtected>
              }
            >
              <Route index element={<AdminOverview />} />
              <Route path="claims" element={<AdminClaims />} />
              <Route path="members" element={<AdminMembers />} />
              <Route path="payments" element={<AdminPayments />} />
              <Route path="accounts" element={<AdminAccounts />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AdminProvider>
    </AppProvider>
    </ErrorBoundary>
  )
}
