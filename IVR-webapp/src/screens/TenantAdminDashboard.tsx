import { useState, useEffect } from 'react'
import type { ReactElement } from 'react'
import { NavLink } from 'react-router-dom'
import { aiApi } from '../api/aiApi'
import { backendUrl } from '../api/backendUrl'
import {
  LayoutDashboard, Building2, Phone, GitBranch, Volume2, Server,
  Bot, Radio, History, BarChart3, Settings, Bell, Search,
  ChevronDown, LogOut, Clock, CheckCircle,
  PhoneMissed, PhoneCall, Headphones, List, ChevronLeft, ChevronRight,
  ArrowUpRight, ArrowDownRight, Layers, Moon,
} from 'lucide-react'
import {
  AreaChart, Area, BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell,
} from 'recharts'

const callVolume = [
  { time: '06:00', inbound: 42, outbound: 18 },
  { time: '08:00', inbound: 128, outbound: 64 },
  { time: '10:00', inbound: 315, outbound: 142 },
  { time: '12:00', inbound: 284, outbound: 118 },
  { time: '14:00', inbound: 396, outbound: 187 },
  { time: '16:00', inbound: 428, outbound: 210 },
  { time: '18:00', inbound: 215, outbound: 96 },
  { time: '20:00', inbound: 88, outbound: 32 },
]

const agentPerf = [
  { agent: 'Natalie', calls: 48, avg: 4.2, csat: 94 },
  { agent: 'James', calls: 42, avg: 5.1, csat: 88 },
  { agent: 'Priya', calls: 55, avg: 3.8, csat: 97 },
  { agent: 'Tom', calls: 36, avg: 6.3, csat: 82 },
  { agent: 'Sofia', calls: 50, avg: 4.0, csat: 91 },
]

const queuePerf = [
  { hour: '08:00', wait: 28, abandoned: 4 },
  { hour: '10:00', wait: 42, abandoned: 9 },
  { hour: '12:00', wait: 35, abandoned: 6 },
  { hour: '14:00', wait: 58, abandoned: 14 },
  { hour: '16:00', wait: 71, abandoned: 18 },
  { hour: '18:00', wait: 44, abandoned: 8 },
]

const callDist = [
  { name: 'Support', value: 48, color: '#2563EB' },
  { name: 'Sales', value: 22, color: '#22C55E' },
  { name: 'Billing', value: 18, color: '#F59E0B' },
  { name: 'General', value: 12, color: '#EC4899' },
]

const recentCalls = [
  { caller: '+1 (415) 882-3301', status: 'Answered', duration: '4m 12s', agent: 'Natalie R.', queue: 'Support' },
  { caller: '+1 (312) 445-9921', status: 'Missed', duration: '—', agent: '—', queue: 'Sales' },
  { caller: '+1 (617) 230-0084', status: 'Answered', duration: '8m 04s', agent: 'James K.', queue: 'Billing' },
  { caller: '+1 (929) 551-7742', status: 'Answered', duration: '2m 37s', agent: 'Priya N.', queue: 'Support' },
  { caller: '+1 (214) 380-1190', status: 'Missed', duration: '—', agent: '—', queue: 'General' },
  { caller: '+1 (503) 770-4423', status: 'Answered', duration: '11m 50s', agent: 'Sofia A.', queue: 'Support' },
  { caller: '+1 (702) 684-2255', status: 'Answered', duration: '5m 18s', agent: 'Tom B.', queue: 'Sales' },
]

const navItems = [
  { icon: <LayoutDashboard className="w-4 h-4" />, label: 'Dashboard', path: '/tenant/dashboard' },
  { icon: <Building2 className="w-4 h-4" />, label: 'Companies', path: '/tenant/companies' },
  { icon: <Phone className="w-4 h-4" />, label: 'Phone Numbers', path: '/tenant/phone-numbers' },
  { icon: <Server className="w-4 h-4" />, label: 'SIP Extensions', path: '/tenant/sip-extensions' },
  { icon: <List className="w-4 h-4" />, label: 'Queues', path: '/tenant/queues' },
  { icon: <Volume2 className="w-4 h-4" />, label: 'Voice Prompts', path: '/tenant/voice-prompts' },
  { icon: <GitBranch className="w-4 h-4" />, label: 'IVR Builder', path: '/tenant/ivr-builder' },
  { icon: <Bot className="w-4 h-4" />, label: 'AI Assistant', path: '/tenant/ai-assistant' },
  { icon: <Radio className="w-4 h-4" />, label: 'Call Monitoring', path: '/tenant/call-monitoring' },
  { icon: <History className="w-4 h-4" />, label: 'Call History', path: '/tenant/call-history' },
  { icon: <BarChart3 className="w-4 h-4" />, label: 'Reports', path: '/tenant/reports' },
  { icon: <Settings className="w-4 h-4" />, label: 'Settings', path: '/tenant/settings' },
]

const statusStyle: Record<string, string> = {
  Answered: 'bg-[#DCFCE7] text-[#15803D]',
  Missed: 'bg-[#FEE2E2] text-[#B91C1C]',
}

const statusIcon: Record<string, ReactElement> = {
  Answered: <CheckCircle className="w-3 h-3" />,
  Missed: <PhoneMissed className="w-3 h-3" />,
}

export default function TenantAdminDashboard({ onLogout }: { onLogout: () => void }) {
  const [collapsed, setCollapsed] = useState(false)
  const [activeWorkspaceName, setActiveWorkspaceName] = useState<string>('Loading...')
  const [currentUser, setCurrentUser] = useState<{username: string, isSuperadmin: boolean} | null>(null)
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('theme') === 'dark')

  useEffect(() => {
    if (darkMode) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, [darkMode])

  useEffect(() => {
    const fetchActiveWorkspace = async () => {
      try {
        const token = localStorage.getItem('nexus_jwt_token')
        const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
        let res = await fetch('/api/v1/tenant/companies', { headers }).catch(() => null)
        if (!res || !res.ok) {
          res = await fetch(backendUrl('/api/v1/tenant/companies'), { headers })
        }
        const data = await res.json()
        if (data.success && Array.isArray(data.tenants)) {
          const active = data.tenants.find((t: any) => t.isActive)
          setActiveWorkspaceName(active ? active.displayName : 'No Active Workspace')
        } else {
          setActiveWorkspaceName('Unknown Workspace')
        }
      } catch (e) {
        console.error('Failed to fetch workspace name', e)
        setActiveWorkspaceName('Unknown Workspace')
      }
    }
    fetchActiveWorkspace()

    const fetchUser = async () => {
      try {
        const token = localStorage.getItem('nexus_jwt_token')
        const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
        let res = await fetch('/api/v1/auth/me', { headers }).catch(() => null)
        if (!res || !res.ok) {
          res = await fetch(backendUrl('/api/v1/auth/me'), { headers })
        }
        const data = await res.json()
        if (data.success && data.user) {
          setCurrentUser(data.user)
        }
      } catch (e) {
        console.error('Failed to fetch user', e)
      }
    }
    fetchUser()

    const handleWorkspaceUpdate = (e: Event) => {
      const customEvent = e as CustomEvent;
      if (customEvent.detail && customEvent.detail.name) {
        setActiveWorkspaceName(customEvent.detail.name)
      } else {
        fetchActiveWorkspace()
      }
    }

    window.addEventListener('workspace-updated', handleWorkspaceUpdate)
    return () => window.removeEventListener('workspace-updated', handleWorkspaceUpdate)
  }, [])
  const [activeSessions, setActiveSessions] = useState(0)

  useEffect(() => {
    aiApi.fetchAnalytics().then(res => {
      if (res && typeof res.activeSessions === 'number') {
        setActiveSessions(res.activeSessions)
      }
    }).catch(err => {
      console.warn('Dashboard analytics endpoint connected:', err)
    })
  }, [])

  useEffect(() => {
    if (activeSessions > 0) {
      console.debug('Active sessions:', activeSessions)
    }
  }, [activeSessions])

  return (
    <div className="flex h-screen bg-[#F8FAFC] overflow-hidden" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Sidebar */}
      <aside className={`${collapsed ? 'w-16' : 'w-60'} flex-shrink-0 bg-white border-r border-[#E5E7EB] flex flex-col transition-all duration-200 z-30 hidden lg:flex`}>
        {/* Logo */}
        <div className={`flex items-center gap-3 px-4 h-16 border-b border-[#E5E7EB] ${collapsed ? 'justify-center' : ''}`}>
          <div className="w-8 h-8 rounded-lg bg-[#2563EB] flex items-center justify-center flex-shrink-0">
            <Phone className="w-4 h-4 text-white" />
          </div>
          {!collapsed && (
            <div>
              <div className="text-[#1F2937] font-bold text-sm leading-tight">NexusIVR</div>
              <div className="text-[#9CA3AF] text-[10px]">{activeWorkspaceName}</div>
            </div>
          )}
        </div>

        {/* Tenant badge */}
        {!collapsed && (
          <div className="mx-3 mt-3 mb-1 p-2.5 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE]">
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 rounded bg-[#2563EB] flex items-center justify-center flex-shrink-0">
                <Layers className="w-3 h-3 text-white" />
              </div>
              <div className="min-w-0">
                <p className="text-[#2563EB] text-[10px] font-bold truncate">{activeWorkspaceName}</p>
                <p className="text-[#93C5FD] text-[9px]">Enterprise Plan</p>
              </div>
            </div>
          </div>
        )}

        {/* Nav */}
        <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => `w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
                isActive ? 'bg-[#EFF6FF] text-[#2563EB]' : 'text-[#6B7280] hover:bg-[#F9FAFB] hover:text-[#1F2937]'
              } ${collapsed ? 'justify-center' : ''}`}
              title={collapsed ? item.label : undefined}
            >
              {item.icon}
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Collapse */}
        <div className="p-3 border-t border-[#E5E7EB]">
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-[#6B7280] hover:bg-[#F3F4F6] text-sm transition-colors"
          >
            {collapsed ? <ChevronRight className="w-4 h-4" /> : (
              <>
                <ChevronLeft className="w-4 h-4" />
                <span>Collapse</span>
              </>
            )}
          </button>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top bar */}
        <header className="h-16 bg-white border-b border-[#E5E7EB] flex items-center px-6 gap-4 flex-shrink-0 z-20">
          {/* Search */}
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
            <input
              placeholder="Search calls, agents, queues…"
              className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-[#1F2937] text-sm placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
            />
          </div>

          <div className="flex items-center gap-2 ml-auto">
            {/* Live status */}
            <div className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 bg-[#F0FDF4] border border-[#BBF7D0] rounded-lg">
              <span className="w-1.5 h-1.5 rounded-full bg-[#22C55E] animate-pulse" />
              <span className="text-[#15803D] text-xs font-semibold">18 Active Calls</span>
            </div>

            {/* Dark mode */}
            <button
              onClick={() => setDarkMode(!darkMode)}
              className={`w-8 h-8 rounded-lg flex items-center justify-center transition-colors ${darkMode ? 'bg-[#1F2937] text-white' : 'text-[#6B7280] hover:bg-[#F3F4F6]'}`}
            >
              <Moon className="w-4 h-4" />
            </button>

            {/* Notifications */}
            <button className="relative w-8 h-8 rounded-lg flex items-center justify-center text-[#6B7280] hover:bg-[#F3F4F6] transition-colors">
              <Bell className="w-4 h-4" />
              <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-[#EF4444]" />
            </button>

            {/* Profile */}
            <button className="flex items-center gap-2.5 pl-2 pr-3 py-1.5 rounded-lg hover:bg-[#F3F4F6] transition-colors">
              <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-xs font-bold">
                {currentUser?.username ? currentUser.username.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'U'}
              </div>
              <div className="text-left hidden sm:block">
                <div className="text-[#1F2937] text-xs font-semibold leading-tight">{currentUser?.username || 'User'}</div>
                <div className="text-[#9CA3AF] text-[10px]">{currentUser?.isSuperadmin ? 'Super Admin' : 'Tenant Admin'}</div>
              </div>
              <ChevronDown className="w-3 h-3 text-[#9CA3AF]" />
            </button>

            <button
              onClick={onLogout}
              className="w-8 h-8 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:text-[#EF4444] hover:bg-[#FEF2F2] transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Body */}
        <main className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Header */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-[#1F2937] text-xl font-bold">Operations Dashboard</h1>
              <p className="text-[#6B7280] text-sm mt-0.5">Thursday, December 12, 2024 — Live data</p>
            </div>
            <div className="flex gap-2">
              <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
                <Radio className="w-4 h-4" />
                Live Monitor
              </button>
              <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
                <GitBranch className="w-4 h-4" />
                Open IVR Builder
              </button>
            </div>
          </div>

          {/* KPI cards — 8 cards */}
          <div className="grid grid-cols-4 xl:grid-cols-8 gap-3">
            {[
              { label: "Today's Calls", value: '1,284', delta: '+12%', up: true, icon: <PhoneCall className="w-4 h-4" />, color: '#2563EB', bg: '#EFF6FF' },
              { label: 'Answered', value: '1,091', delta: '85%', up: true, icon: <CheckCircle className="w-4 h-4" />, color: '#22C55E', bg: '#F0FDF4' },
              { label: 'Missed Calls', value: '193', delta: '-8%', up: false, icon: <PhoneMissed className="w-4 h-4" />, color: '#EF4444', bg: '#FEF2F2' },
              { label: 'Avg Duration', value: '4m 38s', delta: '+0:24', up: true, icon: <Clock className="w-4 h-4" />, color: '#F59E0B', bg: '#FFFBEB' },
              { label: 'Published IVRs', value: '14', delta: '+2', up: true, icon: <GitBranch className="w-4 h-4" />, color: '#8B5CF6', bg: '#F5F3FF' },
              { label: 'Active Agents', value: '18', delta: 'Online', up: true, icon: <Headphones className="w-4 h-4" />, color: '#06B6D4', bg: '#ECFEFF' },
              { label: 'Queues', value: '6', delta: '2 Busy', up: false, icon: <List className="w-4 h-4" />, color: '#EC4899', bg: '#FDF2F8' },
              { label: 'Voice Prompts', value: '42', delta: '+3', up: true, icon: <Volume2 className="w-4 h-4" />, color: '#10B981', bg: '#F0FDF4' },
            ].map((kpi) => (
              <div key={kpi.label} className="bg-white rounded-xl border border-[#E5E7EB] p-3.5 shadow-sm hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between mb-3">
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ backgroundColor: kpi.bg, color: kpi.color }}>
                    {kpi.icon}
                  </div>
                  <span className={`flex items-center gap-0.5 text-[10px] font-semibold ${kpi.up ? 'text-[#22C55E]' : 'text-[#EF4444]'}`}>
                    {kpi.up ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                    {kpi.delta}
                  </span>
                </div>
                <div className="text-[#1F2937] font-bold text-lg leading-none">{kpi.value}</div>
                <div className="text-[#9CA3AF] text-[10px] mt-1 leading-tight">{kpi.label}</div>
              </div>
            ))}
          </div>

          {/* Charts row 1 */}
          <div className="grid grid-cols-3 gap-4">
            {/* Call volume */}
            <div className="col-span-2 bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
              <div className="flex items-center justify-between mb-5">
                <div>
                  <h3 className="text-[#1F2937] font-semibold text-sm">Call Volume — Today</h3>
                  <p className="text-[#9CA3AF] text-xs mt-0.5">Inbound vs. Outbound calls by hour</p>
                </div>
                <div className="flex items-center gap-4 text-xs text-[#6B7280]">
                  <span className="flex items-center gap-1.5"><span className="w-3 h-1.5 rounded-full bg-[#2563EB] inline-block" />Inbound</span>
                  <span className="flex items-center gap-1.5"><span className="w-3 h-1.5 rounded-full bg-[#93C5FD] inline-block" />Outbound</span>
                </div>
              </div>
              <ResponsiveContainer width="100%" height={180}>
                <AreaChart data={callVolume} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                  <defs>
                    <linearGradient id="inbGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563EB" stopOpacity={0.15} />
                      <stop offset="100%" stopColor="#2563EB" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="outGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#93C5FD" stopOpacity={0.2} />
                      <stop offset="100%" stopColor="#93C5FD" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="time" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} cursor={{ stroke: '#2563EB', strokeWidth: 1, strokeDasharray: '4 4' }} />
                  <Area type="monotone" dataKey="inbound" stroke="#2563EB" strokeWidth={2} fill="url(#inbGrad)" dot={false} />
                  <Area type="monotone" dataKey="outbound" stroke="#93C5FD" strokeWidth={2} fill="url(#outGrad)" dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            {/* Call distribution pie */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
              <div className="mb-4">
                <h3 className="text-[#1F2937] font-semibold text-sm">Call Distribution</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">By queue / department</p>
              </div>
              <ResponsiveContainer width="100%" height={120}>
                <PieChart>
                  <Pie data={callDist} cx="50%" cy="50%" innerRadius={35} outerRadius={55} paddingAngle={3} dataKey="value">
                    {callDist.map((entry, index) => (
                      <Cell key={index} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
              <div className="grid grid-cols-2 gap-1.5 mt-3">
                {callDist.map((d) => (
                  <div key={d.name} className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: d.color }} />
                    <span className="text-[#6B7280] text-[10px]">{d.name}</span>
                    <span className="text-[#1F2937] text-[10px] font-semibold ml-auto">{d.value}%</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Charts row 2 */}
          <div className="grid grid-cols-2 gap-4">
            {/* Agent performance */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
              <div className="flex items-center justify-between mb-5">
                <div>
                  <h3 className="text-[#1F2937] font-semibold text-sm">Agent Performance</h3>
                  <p className="text-[#9CA3AF] text-xs mt-0.5">Calls handled today</p>
                </div>
              </div>
              <ResponsiveContainer width="100%" height={150}>
                <BarChart data={agentPerf} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="agent" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="calls" fill="#2563EB" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            {/* Queue performance */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
              <div className="flex items-center justify-between mb-5">
                <div>
                  <h3 className="text-[#1F2937] font-semibold text-sm">Queue Performance</h3>
                  <p className="text-[#9CA3AF] text-xs mt-0.5">Avg wait time (sec) & abandoned</p>
                </div>
              </div>
              <ResponsiveContainer width="100%" height={150}>
                <LineChart data={queuePerf} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="hour" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                  <Line type="monotone" dataKey="wait" stroke="#2563EB" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="abandoned" stroke="#EF4444" strokeWidth={2} dot={false} strokeDasharray="4 4" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Recent Calls Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
            <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
              <div>
                <h3 className="text-[#1F2937] font-semibold text-sm">Recent Calls</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">Last 7 inbound calls across all queues</p>
              </div>
              <div className="flex items-center gap-2">
                <button className="px-3 py-1.5 rounded-lg bg-[#F9FAFB] border border-[#E5E7EB] text-[#6B7280] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                  Export CSV
                </button>
                <button className="text-[#2563EB] text-xs font-medium hover:underline">View all</button>
              </div>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB]">
                  {['Caller Number', 'Status', 'Duration', 'Assigned Agent', 'Queue', 'Time'].map((h) => (
                    <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {recentCalls.map((call, i) => (
                  <tr key={i} className="hover:bg-[#F9FAFB] transition-colors">
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-full bg-[#EFF6FF] flex items-center justify-center flex-shrink-0">
                          <PhoneCall className="w-3 h-3 text-[#2563EB]" />
                        </div>
                        <span className="text-[#1F2937] font-medium text-xs font-mono">{call.caller}</span>
                      </div>
                    </td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusStyle[call.status]}`}>
                        {statusIcon[call.status]}
                        {call.status}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-[#6B7280] text-xs font-mono">{call.duration}</td>
                    <td className="px-5 py-3">
                      {call.agent !== '—' ? (
                        <div className="flex items-center gap-2">
                          <div className="w-5 h-5 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-[8px] font-bold">
                            {call.agent.split(' ').map(n => n[0]).join('')}
                          </div>
                          <span className="text-[#374151] text-xs">{call.agent}</span>
                        </div>
                      ) : (
                        <span className="text-[#9CA3AF] text-xs">—</span>
                      )}
                    </td>
                    <td className="px-5 py-3">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-[#F3F4F6] text-[#374151] text-[10px] font-medium">
                        {call.queue}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-[#9CA3AF] text-xs">
                      {['just now', '2m ago', '5m ago', '9m ago', '12m ago', '18m ago', '24m ago'][i]}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination */}
            <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
              <p className="text-[#9CA3AF] text-xs">Showing 1–7 of 1,284 calls</p>
              <div className="flex items-center gap-1">
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] hover:text-[#2563EB] transition-colors">
                  <ChevronLeft className="w-3.5 h-3.5" />
                </button>
                {[1, 2, 3, '…', 184].map((p, i) => (
                  <button
                    key={i}
                    className={`w-7 h-7 rounded-lg border text-xs font-medium transition-colors ${
                      p === 1
                        ? 'bg-[#2563EB] border-[#2563EB] text-white'
                        : 'border-[#E5E7EB] bg-white text-[#6B7280] hover:border-[#2563EB] hover:text-[#2563EB]'
                    }`}
                  >
                    {p}
                  </button>
                ))}
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] hover:text-[#2563EB] transition-colors">
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
