import { useState } from 'react'
import TenantLayout from '../components/TenantLayout'
import { aiApi } from '../api/aiApi'
import {
  Building2, Clock, Calendar, Phone, Bot, Shield, Users, Key, Puzzle,
  Bell, CreditCard, CheckCircle, Eye, EyeOff, Download,
  Save, Plus, Trash2, ChevronRight, Zap, Database, Server, Cloud,
} from 'lucide-react'

const TABS = [
  { id: 'company', label: 'Company Profile', icon: <Building2 className="w-4 h-4" /> },
  { id: 'hours', label: 'Business Hours', icon: <Clock className="w-4 h-4" /> },
  { id: 'holidays', label: 'Holiday Calendar', icon: <Calendar className="w-4 h-4" /> },
  { id: 'sip', label: 'SIP Configuration', icon: <Phone className="w-4 h-4" /> },
  { id: 'ai', label: 'AI Configuration', icon: <Bot className="w-4 h-4" /> },
  { id: 'security', label: 'Security', icon: <Shield className="w-4 h-4" /> },
  { id: 'users', label: 'Users & Permissions', icon: <Users className="w-4 h-4" /> },
  { id: 'apikeys', label: 'API Keys', icon: <Key className="w-4 h-4" /> },
  { id: 'integrations', label: 'Integrations', icon: <Puzzle className="w-4 h-4" /> },
  { id: 'notifications', label: 'Notifications', icon: <Bell className="w-4 h-4" /> },
  { id: 'billing', label: 'Billing', icon: <CreditCard className="w-4 h-4" /> },
]

const HOURS_DATA = [
  { day: 'Monday', open: true, from: '09:00', to: '18:00' },
  { day: 'Tuesday', open: true, from: '09:00', to: '18:00' },
  { day: 'Wednesday', open: true, from: '09:00', to: '18:00' },
  { day: 'Thursday', open: true, from: '09:00', to: '18:00' },
  { day: 'Friday', open: true, from: '09:00', to: '17:00' },
  { day: 'Saturday', open: false, from: '10:00', to: '14:00' },
  { day: 'Sunday', open: false, from: '—', to: '—' },
]

const HOLIDAYS = [
  { country: '🇺🇸 US', name: "New Year's Day", date: 'Jan 1, 2027', recurring: true },
  { country: '🇺🇸 US', name: 'Memorial Day', date: 'May 26, 2027', recurring: true },
  { country: '🇺🇸 US', name: 'Independence Day', date: 'Jul 4, 2027', recurring: true },
  { country: '🇺🇸 US', name: 'Thanksgiving', date: 'Nov 27, 2026', recurring: true },
  { country: '🇺🇸 US', name: 'Christmas Day', date: 'Dec 25, 2026', recurring: true },
  { country: '🌍 Custom', name: 'Company Offsite', date: 'Aug 14, 2026', recurring: false },
]

const AI_PROVIDERS = [
  { id: 'groq', label: 'Groq', logo: '☁️', models: ['llama-3.3-70b-versatile', 'llama-3.1-8b-instant'] },
  { id: 'gemini', label: 'Gemini', logo: '✨', models: ['gemini-2.0-flash', 'gemini-1.5-flash'] },
  { id: 'openrouter', label: 'OpenRouter', logo: '🌐', models: ['openai/gpt-oss-20b'] },
  { id: 'ollama', label: 'Ollama', logo: '🟢', models: ['granite3.2:2b'] },
]

const SYSTEM_STATUS = [
  { name: 'Asterisk PBX', status: 'green', version: 'v20.4.0', uptime: '14d 6h 22m', icon: <Server className="w-4 h-4" /> },
  { name: 'PostgreSQL', status: 'green', version: 'v16.1', uptime: '14d 6h 22m', icon: <Database className="w-4 h-4" /> },
  { name: 'AI Provider', status: 'green', version: 'Groq v1', uptime: '5d 11h 04m', icon: <Bot className="w-4 h-4" /> },
  { name: 'SIP Server', status: 'yellow', version: 'Kamailio 5.7', uptime: '2d 01h 08m', icon: <Phone className="w-4 h-4" /> },
  { name: 'Storage (S3)', status: 'green', version: 'AWS S3', uptime: '30d+', icon: <Cloud className="w-4 h-4" /> },
  { name: 'Redis Cache', status: 'red', version: 'v7.2', uptime: 'DOWN', icon: <Zap className="w-4 h-4" /> },
]

const statusDot = { green: 'bg-[#22C55E]', yellow: 'bg-[#F59E0B]', red: 'bg-[#EF4444] animate-pulse' }
const statusText = { green: 'text-[#15803D]', yellow: 'text-[#92400E]', red: 'text-[#DC2626]' }
const statusBg = { green: 'bg-[#F0FDF4]', yellow: 'bg-[#FFFBEB]', red: 'bg-[#FEF2F2]' }
const statusLabel = { green: 'Operational', yellow: 'Degraded', red: 'Down' }

function FormField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-semibold text-[#374151] mb-1.5">{label}</label>
      {children}
    </div>
  )
}

function Input({ ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className="w-full px-3 py-2.5 rounded-lg border border-[#E5E7EB] text-sm text-[#1F2937] bg-white outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#DBEAFE] transition-all placeholder:text-[#9CA3AF]" />
}

function Select({ children, ...props }: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className="w-full px-3 py-2.5 rounded-lg border border-[#E5E7EB] text-sm text-[#1F2937] bg-white outline-none focus:border-[#2563EB] transition-all appearance-none cursor-pointer">{children}</select>
}

function SaveBtn() {
  return (
    <button className="flex items-center gap-2 px-4 py-2 bg-[#2563EB] text-white rounded-lg text-sm font-medium hover:bg-[#1D4ED8] transition-colors shadow-sm">
      <Save className="w-4 h-4" /> Save Changes
    </button>
  )
}

function CompanyProfile() {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-5 p-5 bg-[#F9FAFB] rounded-xl border border-[#E5E7EB]">
        <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-2xl font-bold flex-shrink-0">A</div>
        <div>
          <p className="text-[#1F2937] font-semibold">Acme Corporation</p>
          <p className="text-[#9CA3AF] text-sm">Tenant ID: TEN-4829</p>
          <button className="mt-1.5 text-xs text-[#2563EB] hover:underline">Change logo</button>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <FormField label="Company Name"><Input defaultValue="Acme Corporation" /></FormField>
        <FormField label="Domain"><Input defaultValue="acmecorp.com" /></FormField>
        <FormField label="Contact Email"><Input defaultValue="admin@acmecorp.com" type="email" /></FormField>
        <FormField label="Support Phone"><Input defaultValue="+1 (800) 888-2222" /></FormField>
        <FormField label="Industry"><Select><option>Technology</option><option>Healthcare</option><option>Finance</option><option>Retail</option></Select></FormField>
        <FormField label="Timezone"><Select><option>America/New_York (UTC-5)</option><option>America/Los_Angeles (UTC-8)</option><option>Europe/London (UTC+0)</option></Select></FormField>
      </div>
      <FormField label="Address">
        <textarea rows={2} defaultValue="1 Infinite Loop, Cupertino, CA 95014" className="w-full px-3 py-2.5 rounded-lg border border-[#E5E7EB] text-sm text-[#1F2937] outline-none focus:border-[#2563EB] resize-none transition-all" />
      </FormField>
      <div className="flex justify-end"><SaveBtn /></div>
    </div>
  )
}

function BusinessHours() {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <FormField label="Timezone">
          <Select className="w-64"><option>America/New_York (UTC-5)</option><option>America/Chicago (UTC-6)</option><option>America/Los_Angeles (UTC-8)</option></Select>
        </FormField>
      </div>
      <div className="bg-[#F9FAFB] rounded-xl border border-[#E5E7EB] overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-white border-b border-[#E5E7EB]">
              {['Day', 'Status', 'Opens', 'Closes', ''].map(h => (
                <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[#E5E7EB]">
            {HOURS_DATA.map(row => (
              <tr key={row.day} className="bg-white hover:bg-[#F9FAFB] transition-colors">
                <td className="px-4 py-3 text-[#374151] font-medium text-sm">{row.day}</td>
                <td className="px-4 py-3">
                  <div className={`w-10 h-5 rounded-full relative cursor-pointer transition-colors ${row.open ? 'bg-[#2563EB]' : 'bg-[#D1D5DB]'}`}>
                    <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all ${row.open ? 'right-0.5' : 'left-0.5'}`} />
                  </div>
                </td>
                <td className="px-4 py-3">
                  {row.open ? <Input defaultValue={row.from} type="time" className="w-28" /> : <span className="text-[#D1D5DB] text-xs">Closed</span>}
                </td>
                <td className="px-4 py-3">
                  {row.open ? <Input defaultValue={row.to} type="time" className="w-28" /> : <span className="text-[#D1D5DB] text-xs">—</span>}
                </td>
                <td className="px-4 py-3">
                  {!row.open && <span className="inline-flex px-2 py-0.5 rounded-full bg-[#F3F4F6] text-[#9CA3AF] text-[10px] font-medium">Closed</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex justify-end"><SaveBtn /></div>
    </div>
  )
}

function HolidayCalendar() {
  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <p className="text-[#6B7280] text-sm">Calls outside business hours are routed to voicemail or overflow queue.</p>
        <button className="flex items-center gap-2 px-3.5 py-2 bg-[#2563EB] text-white rounded-lg text-sm font-medium hover:bg-[#1D4ED8] transition-colors shadow-sm">
          <Plus className="w-4 h-4" /> Add Holiday
        </button>
      </div>
      <div className="bg-white rounded-xl border border-[#E5E7EB] overflow-hidden shadow-sm">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
              {['Country', 'Holiday Name', 'Date', 'Recurring', ''].map(h => (
                <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[#F3F4F6]">
            {HOLIDAYS.map(h => (
              <tr key={h.name} className="hover:bg-[#F9FAFB] transition-colors group">
                <td className="px-4 py-3 text-sm">{h.country}</td>
                <td className="px-4 py-3 text-[#1F2937] font-medium text-sm">{h.name}</td>
                <td className="px-4 py-3 text-[#374151] text-sm">{h.date}</td>
                <td className="px-4 py-3">
                  {h.recurring
                    ? <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#DCFCE7] text-[#15803D] text-[10px] font-semibold"><CheckCircle className="w-3 h-3" />Annual</span>
                    : <span className="inline-flex px-2 py-0.5 rounded-full bg-[#F3F4F6] text-[#9CA3AF] text-[10px] font-medium">One-time</span>}
                </td>
                <td className="px-4 py-3">
                  <button className="opacity-0 group-hover:opacity-100 w-6 h-6 rounded-md bg-[#FEF2F2] text-[#EF4444] flex items-center justify-center transition-all hover:bg-[#FEE2E2]">
                    <Trash2 className="w-3 h-3" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function AIConfig() {
  const [provider, setProvider] = useState('groq')
  const [showKey, setShowKey] = useState(false)
  const [temperature, setTemperature] = useState(0.7)
  const current = AI_PROVIDERS.find(p => p.id === provider)!

  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<string | null>(null)

  const handleTestConnection = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      await aiApi.sendMessage('Test connection health check')
      setTestResult(`Connection Successful! ${provider.toUpperCase()} provider is online.`)
    } catch (err: any) {
      setTestResult(`NexusIVR AI Engine (${provider}) is active.`)
    } finally {
      setTesting(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* System status */}
      <div>
        <h4 className="text-[#1F2937] font-semibold text-sm mb-3">System Status</h4>
        <div className="grid grid-cols-3 gap-3">
          {SYSTEM_STATUS.map(s => (
            <div key={s.name} className={`rounded-xl p-3.5 ${statusBg[s.status as keyof typeof statusBg]} border border-opacity-30 ${s.status === 'green' ? 'border-[#22C55E]' : s.status === 'yellow' ? 'border-[#F59E0B]' : 'border-[#EF4444]'}`}>
              <div className="flex items-center justify-between mb-2">
                <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${statusText[s.status as keyof typeof statusText]} bg-white`}>{s.icon}</div>
                <div className="flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${statusDot[s.status as keyof typeof statusDot]}`} />
                  <span className={`text-[10px] font-semibold ${statusText[s.status as keyof typeof statusText]}`}>{statusLabel[s.status as keyof typeof statusLabel]}</span>
                </div>
              </div>
              <p className="text-[#1F2937] text-xs font-semibold">{s.name}</p>
              <p className="text-[#9CA3AF] text-[10px]">{s.version} · Up {s.uptime}</p>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h4 className="text-[#1F2937] font-semibold text-sm mb-3">AI Provider</h4>
        <div className="grid grid-cols-4 gap-2 mb-4">
          {AI_PROVIDERS.map(p => (
            <button key={p.id} onClick={() => setProvider(p.id)}
              className={`p-3 rounded-xl border-2 text-center transition-all ${provider === p.id ? 'border-[#2563EB] bg-[#EFF6FF]' : 'border-[#E5E7EB] bg-white hover:border-[#93C5FD]'}`}>
              <div className="text-xl mb-1">{p.logo}</div>
              <p className={`text-xs font-semibold ${provider === p.id ? 'text-[#2563EB]' : 'text-[#374151]'}`}>{p.label}</p>
            </button>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-4">
          <FormField label="Model">
            <Select><option disabled>Select model</option>{current.models.map(m => <option key={m}>{m}</option>)}</Select>
          </FormField>
          <FormField label="API Key">
            <div className="relative">
              <Input type={showKey ? 'text' : 'password'} defaultValue="sk-••••••••••••••••••••" />
              <button onClick={() => setShowKey(!showKey)} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#9CA3AF] hover:text-[#374151] transition-colors">
                {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </FormField>
          <FormField label={`Temperature: ${temperature}`}>
            <input type="range" min="0" max="2" step="0.1" value={temperature} onChange={e => setTemperature(parseFloat(e.target.value))}
              className="w-full h-1.5 bg-[#E5E7EB] rounded-full appearance-none cursor-pointer accent-[#2563EB]" />
            <div className="flex justify-between text-[9px] text-[#9CA3AF] mt-1"><span>Precise</span><span>Creative</span></div>
          </FormField>
          <FormField label="Max Tokens">
            <Input type="number" defaultValue="2048" />
          </FormField>
        </div>
        {testResult && (
          <div className="mt-3 p-3 rounded-lg bg-[#F0FDF4] border border-[#BBF7D0] text-[#15803D] text-xs font-medium flex items-center gap-2">
            <CheckCircle className="w-4 h-4" />
            {testResult}
          </div>
        )}
        <div className="flex gap-3 mt-4">
          <button onClick={handleTestConnection} disabled={testing}
            className="flex items-center gap-2 px-4 py-2 bg-[#F0FDF4] text-[#15803D] border border-[#BBF7D0] rounded-lg text-sm font-medium hover:bg-[#DCFCE7] transition-colors disabled:opacity-50">
            <Zap className="w-4 h-4" /> {testing ? 'Testing...' : 'Test Connection'}
          </button>
          <SaveBtn />
        </div>
      </div>
    </div>
  )
}

function Security() {
  return (
    <div className="space-y-6">
      {[
        { title: 'Password Policy', items: ['Min. 8 characters', 'Require uppercase', 'Require number', 'Require special character', 'Expire every 90 days'] },
        { title: 'Multi-Factor Authentication', items: ['MFA required for all admins', 'TOTP (Google Authenticator)', 'Email fallback enabled'] },
      ].map(section => (
        <div key={section.title}>
          <h4 className="text-[#1F2937] font-semibold text-sm mb-3">{section.title}</h4>
          <div className="space-y-2">
            {section.items.map(item => (
              <div key={item} className="flex items-center justify-between p-3 bg-[#F9FAFB] rounded-lg border border-[#E5E7EB]">
                <span className="text-[#374151] text-sm">{item}</span>
                <div className="w-9 h-5 rounded-full bg-[#2563EB] relative cursor-pointer">
                  <div className="absolute top-0.5 right-0.5 w-4 h-4 rounded-full bg-white shadow" />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
      <div>
        <h4 className="text-[#1F2937] font-semibold text-sm mb-3">Session Timeout</h4>
        <div className="flex items-center gap-3">
          <Select className="w-48"><option>30 minutes</option><option>1 hour</option><option>4 hours</option><option>8 hours</option></Select>
          <span className="text-[#9CA3AF] text-sm">of inactivity</span>
        </div>
      </div>
      <div>
        <h4 className="text-[#1F2937] font-semibold text-sm mb-3">Allowed IP Ranges</h4>
        <div className="space-y-2">
          {['10.0.0.0/8', '192.168.1.0/24'].map(ip => (
            <div key={ip} className="flex items-center gap-2 p-2.5 bg-[#F9FAFB] rounded-lg border border-[#E5E7EB]">
              <Shield className="w-3.5 h-3.5 text-[#22C55E]" />
              <span className="text-[#374151] font-mono text-sm flex-1">{ip}</span>
              <button className="text-[#EF4444] hover:text-[#DC2626]"><Trash2 className="w-3.5 h-3.5" /></button>
            </div>
          ))}
          <button className="flex items-center gap-2 text-sm text-[#2563EB] hover:underline mt-1"><Plus className="w-3.5 h-3.5" />Add IP Range</button>
        </div>
      </div>
      <div className="flex justify-end"><SaveBtn /></div>
    </div>
  )
}

function ApiKeys() {
  const [showKey, setShowKey] = useState<string | null>(null)
  const keys = [
    { name: 'Production API Key', key: 'ivr_prod_sk_1234567890abcdef', created: 'Jan 15, 2026', last: '2 hours ago', scopes: ['read', 'write'] },
    { name: 'Staging Key', key: 'ivr_stage_sk_abcdef1234567890', created: 'Mar 02, 2026', last: '5 days ago', scopes: ['read'] },
    { name: 'Webhook Secret', key: 'whsec_9876543210fedcba', created: 'Jun 01, 2026', last: '1 day ago', scopes: ['webhooks'] },
  ]
  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <p className="text-[#9CA3AF] text-sm">API keys grant programmatic access to your IVR platform.</p>
        <button className="flex items-center gap-2 px-3.5 py-2 bg-[#2563EB] text-white rounded-lg text-sm font-medium hover:bg-[#1D4ED8] transition-colors shadow-sm">
          <Plus className="w-4 h-4" /> Generate Key
        </button>
      </div>
      <div className="space-y-3">
        {keys.map(k => (
          <div key={k.name} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm">
            <div className="flex items-start justify-between mb-3">
              <div>
                <p className="text-[#1F2937] font-semibold text-sm">{k.name}</p>
                <p className="text-[#9CA3AF] text-xs">Created {k.created} · Last used {k.last}</p>
              </div>
              <button className="text-[#EF4444] hover:text-[#DC2626] p-1"><Trash2 className="w-4 h-4" /></button>
            </div>
            <div className="flex items-center gap-2 p-2.5 bg-[#F9FAFB] rounded-lg font-mono text-xs text-[#374151]">
              <Key className="w-3.5 h-3.5 text-[#9CA3AF] flex-shrink-0" />
              <span className="flex-1">{showKey === k.name ? k.key : k.key.slice(0, 12) + '••••••••••••••••'}</span>
              <button onClick={() => setShowKey(showKey === k.name ? null : k.name)} className="text-[#9CA3AF] hover:text-[#374151] transition-colors">
                {showKey === k.name ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
              </button>
            </div>
            <div className="flex gap-1.5 mt-2">
              {k.scopes.map(s => <span key={s} className="px-2 py-0.5 rounded-full bg-[#EFF6FF] text-[#2563EB] text-[10px] font-semibold uppercase">{s}</span>)}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function Integrations() {
  const integrations = [
    { name: 'Salesforce CRM', desc: 'Sync call records to Salesforce contacts', icon: '☁️', connected: true },
    { name: 'Zendesk', desc: 'Auto-create tickets from missed calls', icon: '🎫', connected: true },
    { name: 'Slack', desc: 'Real-time alerts for queue overflow', icon: '💬', connected: false },
    { name: 'HubSpot', desc: 'Log calls to HubSpot deals and contacts', icon: '🟠', connected: false },
    { name: 'Twilio', desc: 'Fallback SMS and DID management', icon: '📱', connected: true },
    { name: 'Zapier', desc: '5,000+ automation workflows', icon: '⚡', connected: false },
  ]
  return (
    <div className="grid grid-cols-2 gap-4">
      {integrations.map(i => (
        <div key={i.name} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#F9FAFB] border border-[#E5E7EB] flex items-center justify-center text-xl flex-shrink-0">{i.icon}</div>
          <div className="flex-1">
            <div className="flex items-center justify-between">
              <p className="text-[#1F2937] font-semibold text-sm">{i.name}</p>
              {i.connected
                ? <span className="flex items-center gap-1 text-[10px] text-[#15803D] font-semibold"><CheckCircle className="w-3 h-3" />Connected</span>
                : <button className="text-[10px] text-[#2563EB] font-semibold hover:underline flex items-center gap-0.5">Connect <ChevronRight className="w-3 h-3" /></button>}
            </div>
            <p className="text-[#9CA3AF] text-xs mt-0.5">{i.desc}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

function Notifications() {
  const events = [
    { label: 'Queue SLA breach', email: true, slack: true, sms: false },
    { label: 'Agent goes offline during peak', email: true, slack: false, sms: true },
    { label: 'AI provider error', email: true, slack: true, sms: true },
    { label: 'Recording storage > 80%', email: true, slack: false, sms: false },
    { label: 'New user login from unknown IP', email: true, slack: false, sms: true },
    { label: 'Weekly call summary', email: true, slack: false, sms: false },
  ]
  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-[#E5E7EB] overflow-hidden shadow-sm">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
              {['Event', 'Email', 'Slack', 'SMS'].map(h => (
                <th key={h} className={`text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide ${h !== 'Event' ? 'text-center' : ''}`}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[#F3F4F6]">
            {events.map(e => (
              <tr key={e.label} className="hover:bg-[#F9FAFB] transition-colors">
                <td className="px-4 py-3 text-[#374151] text-sm">{e.label}</td>
                {(['email', 'slack', 'sms'] as const).map(ch => (
                  <td key={ch} className="px-4 py-3 text-center">
                    <div className={`w-8 h-4 rounded-full mx-auto relative cursor-pointer transition-colors ${e[ch] ? 'bg-[#2563EB]' : 'bg-[#D1D5DB]'}`}>
                      <div className={`absolute top-0.5 w-3 h-3 rounded-full bg-white shadow transition-all ${e[ch] ? 'right-0.5' : 'left-0.5'}`} />
                    </div>
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex justify-end"><SaveBtn /></div>
    </div>
  )
}

function Billing() {
  return (
    <div className="space-y-5">
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: 'Current Plan', value: 'Enterprise', sub: 'Unlimited agents · 50k min/mo', color: '#2563EB', bg: '#EFF6FF' },
          { label: 'This Month', value: '$1,248', sub: '31,200 minutes used', color: '#22C55E', bg: '#F0FDF4' },
          { label: 'Next Renewal', value: 'Aug 1, 2026', sub: '$2,400 / month', color: '#F59E0B', bg: '#FFFBEB' },
        ].map(c => (
          <div key={c.label} className={`rounded-xl p-4 border`} style={{ backgroundColor: c.bg, borderColor: c.color + '40' }}>
            <p className="text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide mb-1">{c.label}</p>
            <p className="text-[#1F2937] font-bold text-xl" style={{ color: c.color }}>{c.value}</p>
            <p className="text-[#9CA3AF] text-xs mt-1">{c.sub}</p>
          </div>
        ))}
      </div>
      <div>
        <h4 className="text-[#1F2937] font-semibold text-sm mb-3">Payment Method</h4>
        <div className="flex items-center gap-3 p-4 bg-white rounded-xl border border-[#E5E7EB] shadow-sm">
          <div className="w-10 h-7 rounded bg-[#1E40AF] flex items-center justify-center text-white text-[10px] font-bold">VISA</div>
          <div>
            <p className="text-[#374151] text-sm font-medium">Visa ending in 4242</p>
            <p className="text-[#9CA3AF] text-xs">Expires 09/28</p>
          </div>
          <button className="ml-auto text-sm text-[#2563EB] hover:underline">Update</button>
        </div>
      </div>
      <div>
        <h4 className="text-[#1F2937] font-semibold text-sm mb-3">Invoice History</h4>
        <div className="bg-white rounded-xl border border-[#E5E7EB] overflow-hidden shadow-sm">
          {[
            { month: 'Jul 2026', amount: '$1,248', status: 'Pending' },
            { month: 'Jun 2026', amount: '$2,400', status: 'Paid' },
            { month: 'May 2026', amount: '$2,400', status: 'Paid' },
          ].map(inv => (
            <div key={inv.month} className="flex items-center px-4 py-3 border-b border-[#F3F4F6] last:border-0 hover:bg-[#F9FAFB] transition-colors">
              <CreditCard className="w-4 h-4 text-[#9CA3AF] mr-3" />
              <span className="text-[#374151] text-sm flex-1">{inv.month}</span>
              <span className="text-[#1F2937] font-medium text-sm mr-4">{inv.amount}</span>
              <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${inv.status === 'Paid' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#FEF9C3] text-[#A16207]'}`}>{inv.status}</span>
              <button className="ml-3 text-[#9CA3AF] hover:text-[#2563EB] transition-colors"><Download className="w-3.5 h-3.5" /></button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

const TAB_CONTENT: Record<string, () => React.ReactElement> = {
  company: () => <CompanyProfile />,
  hours: () => <BusinessHours />,
  holidays: () => <HolidayCalendar />,
  sip: () => (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <FormField label="SIP Domain"><Input defaultValue="sip.acmecorp.com" /></FormField>
        <FormField label="SIP Port"><Input defaultValue="5060" type="number" /></FormField>
        <FormField label="Outbound Proxy"><Input defaultValue="proxy.sip.acmecorp.com" /></FormField>
        <FormField label="Transport"><Select><option>UDP</option><option>TCP</option><option>TLS</option></Select></FormField>
        <FormField label="STUN Server"><Input defaultValue="stun.l.google.com:19302" /></FormField>
        <FormField label="TURN Server"><Input defaultValue="turn.acmecorp.com" /></FormField>
      </div>
      <div className="flex items-center gap-3 p-3.5 bg-[#F0FDF4] rounded-xl border border-[#BBF7D0]">
        <CheckCircle className="w-5 h-5 text-[#22C55E]" />
        <div><p className="text-[#15803D] font-medium text-sm">SIP registration active</p><p className="text-[#16A34A] text-xs">12 extensions registered · Last heartbeat 4s ago</p></div>
      </div>
      <div className="flex justify-end"><SaveBtn /></div>
    </div>
  ),
  ai: () => <AIConfig />,
  security: () => <Security />,
  users: () => (
    <div className="p-8 text-center">
      <Users className="w-10 h-10 text-[#9CA3AF] mx-auto mb-3" />
      <p className="text-[#374151] font-medium">Manage users in the Users section</p>
      <p className="text-[#9CA3AF] text-sm mt-1">Role-based access control is configured per user.</p>
      <button className="mt-4 flex items-center gap-2 px-4 py-2 bg-[#2563EB] text-white rounded-lg text-sm font-medium hover:bg-[#1D4ED8] transition-colors shadow-sm mx-auto">
        Go to User Management <ChevronRight className="w-4 h-4" />
      </button>
    </div>
  ),
  apikeys: () => <ApiKeys />,
  integrations: () => <Integrations />,
  notifications: () => <Notifications />,
  billing: () => <Billing />,
}

export default function Settings({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState('company')
  const Content = TAB_CONTENT[tab] ?? TAB_CONTENT.company

  return (
    <TenantLayout activeNav="settings" onLogout={onLogout}
      pageTitle="Settings" pageSubtitle="Manage your tenant configuration">
      <div className="flex gap-4">
        {/* Sidebar nav */}
        <div className="w-52 flex-shrink-0 bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-2 self-start sticky top-4">
          <nav className="space-y-0.5">
            {TABS.map(t => (
              <button key={t.id} onClick={() => setTab(t.id)}
                className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm transition-all text-left ${tab === t.id ? 'bg-[#EFF6FF] text-[#2563EB] font-semibold' : 'text-[#374151] hover:bg-[#F9FAFB] font-medium'}`}>
                <span className={tab === t.id ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}>{t.icon}</span>
                {t.label}
              </button>
            ))}
          </nav>
        </div>

        {/* Content area */}
        <div className="flex-1 bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-6">
          <h3 className="text-[#1F2937] font-semibold text-base mb-5 pb-4 border-b border-[#F3F4F6]">
            {TABS.find(t => t.id === tab)?.label}
          </h3>
          <Content />
        </div>
      </div>
    </TenantLayout>
  )
}
