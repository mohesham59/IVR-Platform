import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, Building2, Users, CreditCard, Activity, ScrollText,
  BarChart3, Settings, Bell, Search, ChevronLeft, ChevronRight, Moon,
  TrendingUp, Phone, Cpu, FileText, Plus, UserPlus, Download,
  LogOut, ChevronDown, ArrowUpRight, ArrowDownRight, Menu,
  CheckCircle2,
} from 'lucide-react'
import {
  AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'

const monthlyCompanies = [
  { month: 'Jan', companies: 210 },
  { month: 'Feb', companies: 248 },
  { month: 'Mar', companies: 295 },
  { month: 'Apr', companies: 340 },
  { month: 'May', companies: 398 },
  { month: 'Jun', companies: 462 },
  { month: 'Jul', companies: 530 },
  { month: 'Aug', companies: 610 },
  { month: 'Sep', companies: 695 },
  { month: 'Oct', companies: 780 },
  { month: 'Nov', companies: 890 },
  { month: 'Dec', companies: 1020 },
]

const callsPerDay = [
  { day: 'Mon', calls: 14200, ai: 8400 },
  { day: 'Tue', calls: 18900, ai: 11200 },
  { day: 'Wed', calls: 22100, ai: 13800 },
  { day: 'Thu', calls: 19500, ai: 12100 },
  { day: 'Fri', calls: 24300, ai: 15600 },
  { day: 'Sat', calls: 9800, ai: 6200 },
  { day: 'Sun', calls: 7400, ai: 4800 },
]

const aiUsage = [
  { hour: '00:00', requests: 820 },
  { hour: '04:00', requests: 450 },
  { hour: '08:00', requests: 2100 },
  { hour: '12:00', requests: 4800 },
  { hour: '16:00', requests: 5200 },
  { hour: '20:00', requests: 2900 },
]

const companies = [
  { name: 'Meridian Health', plan: 'Enterprise', users: 142, status: 'Active', joined: 'Dec 12, 2024' },
  { name: 'Vantage Retail', plan: 'Business', users: 56, status: 'Active', joined: 'Dec 10, 2024' },
  { name: 'Apex Logistics', plan: 'Business', users: 38, status: 'Trial', joined: 'Dec 9, 2024' },
  { name: 'ClearPath Finance', plan: 'Enterprise', users: 218, status: 'Active', joined: 'Dec 8, 2024' },
  { name: 'Solaris Telecom', plan: 'Starter', users: 12, status: 'Inactive', joined: 'Dec 7, 2024' },
]

const recentUsers = [
  { name: 'Marcus Webb', email: 'marcus@meridian.io', role: 'Tenant Admin', company: 'Meridian Health', joined: '2h ago' },
  { name: 'Priya Nair', email: 'priya@vantage.com', role: 'Agent', company: 'Vantage Retail', joined: '5h ago' },
  { name: 'Tom Brecker', email: 'tom@apex.co', role: 'Tenant Admin', company: 'Apex Logistics', joined: '9h ago' },
  { name: 'Sofia Alvarez', email: 'sofia@clearpath.io', role: 'Supervisor', company: 'ClearPath Finance', joined: '12h ago' },
]

const activities = [
  { action: 'Company created', subject: 'Meridian Health', time: '2 min ago', type: 'success' },
  { action: 'IVR published', subject: 'Support Flow v3', time: '14 min ago', type: 'info' },
  { action: 'Login failed (5×)', subject: 'priya@vantage.com', time: '31 min ago', type: 'warning' },
  { action: 'Subscription upgraded', subject: 'ClearPath Finance → Enterprise', time: '1h ago', type: 'success' },
  { action: 'API limit exceeded', subject: 'Solaris Telecom', time: '2h ago', type: 'danger' },
]

const navItems = [
  { icon: <LayoutDashboard className="w-4 h-4" />, label: 'Dashboard', path: '/super-admin/dashboard' },
  { icon: <Building2 className="w-4 h-4" />, label: 'Companies', path: '/super-admin/companies' },
  { icon: <Users className="w-4 h-4" />, label: 'Users', path: '/super-admin/users' },
  { icon: <CreditCard className="w-4 h-4" />, label: 'Subscriptions', path: '/super-admin/subscriptions' },
  { icon: <Activity className="w-4 h-4" />, label: 'System Health', path: '/super-admin/system-health' },
  { icon: <ScrollText className="w-4 h-4" />, label: 'Audit Logs', path: '/super-admin/audit-logs' },
  { icon: <BarChart3 className="w-4 h-4" />, label: 'Reports', path: '/super-admin/reports' },
  { icon: <Settings className="w-4 h-4" />, label: 'Settings', path: '/super-admin/settings' },
]

const statusColor: Record<string, string> = {
  Active: 'bg-[#DCFCE7] text-[#15803D]',
  Trial: 'bg-[#FEF9C3] text-[#A16207]',
  Inactive: 'bg-[#FEE2E2] text-[#B91C1C]',
}

const activityColor: Record<string, string> = {
  success: 'bg-[#22C55E]',
  info: 'bg-[#2563EB]',
  warning: 'bg-[#F59E0B]',
  danger: 'bg-[#EF4444]',
}

export default function SuperAdminDashboard({ onLogout }: { onLogout: () => void }) {
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [darkMode, setDarkMode] = useState(false)

  const sidebarW = collapsed ? 'w-16' : 'w-60'

  return (
    <div className="flex h-screen bg-[#F8FAFC] overflow-hidden" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Sidebar */}
      <aside
        className={`${sidebarW} flex-shrink-0 bg-white border-r border-[#E5E7EB] flex flex-col transition-all duration-200 z-30 hidden lg:flex`}
      >
        {/* Logo */}
        <div className={`flex items-center gap-3 px-4 h-16 border-b border-[#E5E7EB] ${collapsed ? 'justify-center' : ''}`}>
          <div className="w-8 h-8 rounded-lg bg-[#2563EB] flex items-center justify-center flex-shrink-0">
            <Phone className="w-4 h-4 text-white" />
          </div>
          {!collapsed && (
            <div>
              <div className="text-[#1F2937] font-bold text-sm leading-tight">NexusIVR</div>
              <div className="text-[#9CA3AF] text-[10px]">Super Admin</div>
            </div>
          )}
        </div>

        {/* Nav */}
        <nav className="flex-1 px-2 py-4 space-y-0.5 overflow-y-auto">
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

        {/* Collapse toggle */}
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
          <button className="lg:hidden text-[#6B7280]" onClick={() => setMobileOpen(!mobileOpen)}>
            <Menu className="w-5 h-5" />
          </button>

          {/* Search */}
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
            <input
              placeholder="Search companies, users…"
              className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-[#1F2937] text-sm placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
            />
          </div>

          <div className="flex items-center gap-2 ml-auto">
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
              <div className="w-7 h-7 rounded-full bg-[#2563EB] flex items-center justify-center text-white text-xs font-bold">
                SA
              </div>
              <div className="text-left hidden sm:block">
                <div className="text-[#1F2937] text-xs font-semibold leading-tight">Super Admin</div>
                <div className="text-[#9CA3AF] text-[10px]">admin@nexusivr.io</div>
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

        {/* Dashboard body */}
        <main className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Page heading */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-[#1F2937] text-xl font-bold">Platform Overview</h1>
              <p className="text-[#6B7280] text-sm mt-0.5">Thursday, December 12, 2024</p>
            </div>

            {/* Quick actions */}
            <div className="flex gap-2">
              <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
                <UserPlus className="w-4 h-4" />
                Create Admin
              </button>
              <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
                <Download className="w-4 h-4" />
                Report
              </button>
              <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
                <Plus className="w-4 h-4" />
                Add Company
              </button>
            </div>
          </div>

          {/* KPI cards */}
          <div className="grid grid-cols-2 xl:grid-cols-6 gap-4">
            {[
              { label: 'Total Companies', value: '3,412', delta: '+24', up: true, icon: <Building2 className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF' },
              { label: 'Active Companies', value: '3,104', delta: '+18', up: true, icon: <CheckCircle2 className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4' },
              { label: 'Total Users', value: '48,291', delta: '+312', up: true, icon: <Users className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF' },
              { label: 'Active Calls', value: '1,847', delta: '-92', up: false, icon: <Phone className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB' },
              { label: 'Published IVRs', value: '9,263', delta: '+67', up: true, icon: <FileText className="w-5 h-5" />, color: '#06B6D4', bg: '#ECFEFF' },
              { label: 'AI Requests Today', value: '284K', delta: '+8.2%', up: true, icon: <Cpu className="w-5 h-5" />, color: '#EC4899', bg: '#FDF2F8' },
            ].map((kpi) => (
              <div key={kpi.label} className="col-span-1 bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between mb-3">
                  <div className="w-9 h-9 rounded-lg flex items-center justify-center" style={{ backgroundColor: kpi.bg, color: kpi.color }}>
                    {kpi.icon}
                  </div>
                  <span className={`flex items-center gap-0.5 text-xs font-semibold ${kpi.up ? 'text-[#22C55E]' : 'text-[#EF4444]'}`}>
                    {kpi.up ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                    {kpi.delta}
                  </span>
                </div>
                <div className="text-[#1F2937] font-bold text-xl leading-none">{kpi.value}</div>
                <div className="text-[#9CA3AF] text-xs mt-1.5 leading-tight">{kpi.label}</div>
              </div>
            ))}
          </div>

          {/* Charts row */}
          <div className="grid grid-cols-3 gap-4">
            {/* Monthly growth */}
            <div className="col-span-2 bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
              <div className="flex items-center justify-between mb-5">
                <div>
                  <h3 className="text-[#1F2937] font-semibold text-sm">Monthly Company Growth</h3>
                  <p className="text-[#9CA3AF] text-xs mt-0.5">New companies registered per month</p>
                </div>
                <div className="flex items-center gap-1.5 bg-[#F0FDF4] border border-[#BBF7D0] rounded-full px-2.5 py-1">
                  <TrendingUp className="w-3 h-3 text-[#22C55E]" />
                  <span className="text-[#22C55E] text-xs font-semibold">+38.5%</span>
                </div>
              </div>
              <ResponsiveContainer width="100%" height={180}>
                <AreaChart data={monthlyCompanies} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                  <defs>
                    <linearGradient id="compGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563EB" stopOpacity={0.15} />
                      <stop offset="100%" stopColor="#2563EB" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="month" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }}
                    cursor={{ stroke: '#2563EB', strokeWidth: 1, strokeDasharray: '4 4' }}
                  />
                  <Area type="monotone" dataKey="companies" stroke="#2563EB" strokeWidth={2} fill="url(#compGrad)" dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            {/* AI Usage */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
              <div className="mb-5">
                <h3 className="text-[#1F2937] font-semibold text-sm">AI Requests Today</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">Requests per time period</p>
              </div>
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={aiUsage} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="hour" tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }}
                  />
                  <Bar dataKey="requests" fill="#2563EB" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Calls chart */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-[#1F2937] font-semibold text-sm">Calls Per Day</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">Total calls vs. AI-handled calls this week</p>
              </div>
              <div className="flex items-center gap-4 text-xs text-[#6B7280]">
                <span className="flex items-center gap-1.5"><span className="w-3 h-1.5 rounded-full bg-[#2563EB] inline-block" />Total Calls</span>
                <span className="flex items-center gap-1.5"><span className="w-3 h-1.5 rounded-full bg-[#93C5FD] inline-block" />AI Handled</span>
              </div>
            </div>
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={callsPerDay} margin={{ top: 0, right: 0, left: -20, bottom: 0 }} barGap={4}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                <Bar dataKey="calls" fill="#2563EB" radius={[4, 4, 0, 0]} />
                <Bar dataKey="ai" fill="#93C5FD" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Tables row */}
          <div className="grid grid-cols-5 gap-4">
            {/* Companies table */}
            <div className="col-span-3 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
                <h3 className="text-[#1F2937] font-semibold text-sm">Latest Registered Companies</h3>
                <button className="text-[#2563EB] text-xs font-medium hover:underline">View all</button>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F9FAFB]">
                    {['Company', 'Plan', 'Users', 'Status', 'Joined'].map((h) => (
                      <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#F3F4F6]">
                  {companies.map((c) => (
                    <tr key={c.name} className="hover:bg-[#F9FAFB] transition-colors">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-3">
                          <div className="w-7 h-7 rounded-lg bg-[#EFF6FF] flex items-center justify-center">
                            <Building2 className="w-3.5 h-3.5 text-[#2563EB]" />
                          </div>
                          <span className="text-[#1F2937] font-medium text-xs">{c.name}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-[#6B7280] text-xs">{c.plan}</td>
                      <td className="px-5 py-3 text-[#6B7280] text-xs">{c.users}</td>
                      <td className="px-5 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusColor[c.status]}`}>
                          {c.status}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-[#9CA3AF] text-xs">{c.joined}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Activity feed */}
            <div className="col-span-2 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
                <h3 className="text-[#1F2937] font-semibold text-sm">Recent Activity</h3>
                <button className="text-[#2563EB] text-xs font-medium hover:underline">View logs</button>
              </div>
              <div className="p-5 space-y-4">
                {activities.map((a, i) => (
                  <div key={i} className="flex items-start gap-3">
                    <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${activityColor[a.type]}`} />
                    <div className="flex-1 min-w-0">
                      <p className="text-[#374151] text-xs font-medium leading-snug">{a.action}</p>
                      <p className="text-[#9CA3AF] text-[11px] mt-0.5 truncate">{a.subject}</p>
                    </div>
                    <span className="text-[#9CA3AF] text-[10px] flex-shrink-0 mt-0.5">{a.time}</span>
                  </div>
                ))}
              </div>

              {/* Users */}
              <div className="border-t border-[#F3F4F6]">
                <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
                  <h3 className="text-[#1F2937] font-semibold text-sm">Latest Users</h3>
                  <button className="text-[#2563EB] text-xs font-medium hover:underline">View all</button>
                </div>
                <div className="divide-y divide-[#F9FAFB]">
                  {recentUsers.map((u) => (
                    <div key={u.email} className="flex items-center gap-3 px-5 py-3 hover:bg-[#F9FAFB] transition-colors">
                      <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-[10px] font-bold flex-shrink-0">
                        {u.name.split(' ').map(n => n[0]).join('')}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-[#1F2937] text-xs font-medium truncate">{u.name}</p>
                        <p className="text-[#9CA3AF] text-[11px] truncate">{u.company}</p>
                      </div>
                      <span className="text-[#9CA3AF] text-[10px] flex-shrink-0">{u.joined}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
