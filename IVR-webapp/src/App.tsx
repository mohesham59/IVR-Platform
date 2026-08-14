import { Component, type ReactNode } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import LoginPage from './screens/LoginPage'
import SuperAdminDashboard from './screens/SuperAdminDashboard'
import SuperAdminUsers from './screens/SuperAdminUsers'
import SuperAdminCompanies from './screens/SuperAdminCompanies'
import SuperAdminSubscriptions from './screens/SuperAdminSubscriptions'
import TenantAdminDashboard from './screens/TenantAdminDashboard'
import TenantCompanies from './screens/TenantCompanies'
import TenantBilling from './screens/TenantBilling'
import PaymentCallback from './screens/PaymentCallback'
import PhoneNumbers from './screens/PhoneNumbers'
import SIPExtensions from './screens/SIPExtensions'
import QueueManagement from './screens/QueueManagement'
import IVRBuilder from './screens/IVRBuilder'
import AIAssistant from './screens/AIAssistant'
import VoicePrompts from './screens/VoicePrompts'
import CallAnalytics from './screens/CallAnalytics'
import Settings from './screens/Settings'
import SuperAdminSettings from './screens/SuperAdminSettings'
import SuperAdminLayout from './components/SuperAdminLayout'

const superAdminPages = [
  ['companies', 'Companies'],
  ['users', 'Users'],
  ['subscriptions', 'Subscriptions'],
  ['system-health', 'System Health'],
  ['audit-logs', 'Audit Logs'],
  ['reports', 'Reports'],
  ['settings', 'Settings'],
] as const

function LogoutRoute() {
  const navigate = useNavigate()
  return <LoginPage onLogin={(email) => navigate((email === 'admin@nexusivr.com' || email === 'admin@nexusivr.io') ? '/super-admin/dashboard' : '/tenant/dashboard')} />
}

function SuperAdminSection({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <SuperAdminLayout pageTitle={title} onLogout={() => navigate('/')}>
      <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-6 text-[#6B7280] text-sm">
        {title} data will appear here.
      </div>
    </SuperAdminLayout>
  )
}

interface ErrorBoundaryProps {
  children: ReactNode
  fallback?: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state = { hasError: false }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error: any, errorInfo: any) {
    console.error('ErrorBoundary caught an error:', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback || (
        <div className="flex flex-col items-center justify-center min-h-screen bg-slate-50 text-slate-800 p-6">
          <div className="bg-white rounded-xl border border-red-200 shadow-xl p-8 max-w-md w-full text-center">
            <span className="text-4xl">⚠️</span>
            <h2 className="text-lg font-bold mt-4 text-red-600">Application Error</h2>
            <p className="text-xs text-slate-500 mt-2">
              An unexpected error occurred. Please try reloading the application.
            </p>
            <button
              onClick={() => window.location.reload()}
              className="mt-6 px-4 py-2 bg-[#2563EB] hover:bg-[#1E40AF] text-white rounded-lg text-xs font-semibold shadow-md transition-all"
            >
              Reload Application
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}

export default function App() {
  const navigate = useNavigate()
  const logout = () => navigate('/')

  return (
    <ErrorBoundary>
      <Routes>
        <Route path="/" element={<LogoutRoute />} />
        <Route path="/super-admin/dashboard" element={<SuperAdminDashboard onLogout={logout} />} />
        <Route path="/super-admin/companies" element={<SuperAdminCompanies onLogout={logout} />} />
        <Route path="/super-admin/users" element={<SuperAdminUsers onLogout={logout} />} />
        <Route path="/super-admin/subscriptions" element={<SuperAdminSubscriptions onLogout={logout} />} />
        <Route path="/super-admin/settings" element={<SuperAdminSettings onLogout={logout} />} />
        {superAdminPages.filter(([slug]) => !['users', 'companies', 'subscriptions', 'settings'].includes(slug)).map(([slug, title]) => (
          <Route key={slug} path={`/super-admin/${slug}`} element={<SuperAdminSection title={title} />} />
        ))}
        <Route path="/tenant/dashboard" element={<TenantAdminDashboard onLogout={logout} />} />
        <Route path="/tenant/companies" element={<TenantCompanies onLogout={logout} />} />
        <Route path="/tenant/phone-numbers" element={<PhoneNumbers onLogout={logout} />} />
        <Route path="/tenant/sip-extensions" element={<SIPExtensions onLogout={logout} />} />
        <Route path="/tenant/queues" element={<QueueManagement onLogout={logout} />} />
        <Route path="/tenant/voice-prompts" element={<VoicePrompts onLogout={logout} />} />
        <Route path="/tenant/ivr-builder" element={<ErrorBoundary fallback={
          <div className="flex flex-col items-center justify-center p-6 text-slate-800">
            <span className="text-2xl">⚠️</span>
            <h3 className="font-bold mt-2 text-red-600">IVR Builder Error</h3>
            <p className="text-xs text-slate-500 mt-1">An error occurred rendering the builder canvas.</p>
          </div>
        }><IVRBuilder onLogout={logout} /></ErrorBoundary>} />
        <Route path="/tenant/ai-assistant" element={<AIAssistant onLogout={logout} />} />
        <Route path="/tenant/call-analytics" element={<CallAnalytics onLogout={logout} />} />
        <Route path="/tenant/call-monitoring" element={<CallMonitoring onLogout={logout} />} />
        <Route path="/tenant/call-history" element={<CallHistory onLogout={logout} />} />
        <Route path="/tenant/reports" element={<Reports onLogout={logout} />} />
        <Route path="/tenant/settings" element={<Settings onLogout={logout} />} />
        <Route path="/tenant/billing" element={<TenantBilling onLogout={logout} />} />
        <Route path="/payment/callback" element={<PaymentCallback />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ErrorBoundary>
  )
}
