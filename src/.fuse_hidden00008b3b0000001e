import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import LoginPage from './screens/LoginPage'
import SuperAdminDashboard from './screens/SuperAdminDashboard'
import TenantAdminDashboard from './screens/TenantAdminDashboard'
import UserManagement from './screens/UserManagement'
import PhoneNumbers from './screens/PhoneNumbers'
import SIPExtensions from './screens/SIPExtensions'
import QueueManagement from './screens/QueueManagement'
import IVRBuilder from './screens/IVRBuilder'
import AIAssistant from './screens/AIAssistant'
import VoicePrompts from './screens/VoicePrompts'
import CallMonitoring from './screens/CallMonitoring'
import CallHistory from './screens/CallHistory'
import Reports from './screens/Reports'
import Settings from './screens/Settings'
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
  return <LoginPage onLogin={(email) => navigate(email === 'admin@nexusivr.io' ? '/super-admin/dashboard' : '/tenant/dashboard')} />
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

export default function App() {
  const navigate = useNavigate()
  const logout = () => navigate('/')

  return (
    <Routes>
      <Route path="/" element={<LogoutRoute />} />
      <Route path="/super-admin/dashboard" element={<SuperAdminDashboard onLogout={logout} />} />
      {superAdminPages.map(([slug, title]) => (
        <Route key={slug} path={`/super-admin/${slug}`} element={<SuperAdminSection title={title} />} />
      ))}
      <Route path="/tenant/dashboard" element={<TenantAdminDashboard onLogout={logout} />} />
      <Route path="/tenant/users" element={<UserManagement onLogout={logout} />} />
      <Route path="/tenant/phone-numbers" element={<PhoneNumbers onLogout={logout} />} />
      <Route path="/tenant/sip-extensions" element={<SIPExtensions onLogout={logout} />} />
      <Route path="/tenant/queues" element={<QueueManagement onLogout={logout} />} />
      <Route path="/tenant/voice-prompts" element={<VoicePrompts onLogout={logout} />} />
      <Route path="/tenant/ivr-builder" element={<IVRBuilder onLogout={logout} />} />
      <Route path="/tenant/ai-assistant" element={<AIAssistant onLogout={logout} />} />
      <Route path="/tenant/call-monitoring" element={<CallMonitoring onLogout={logout} />} />
      <Route path="/tenant/call-history" element={<CallHistory onLogout={logout} />} />
      <Route path="/tenant/reports" element={<Reports onLogout={logout} />} />
      <Route path="/tenant/settings" element={<Settings onLogout={logout} />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
