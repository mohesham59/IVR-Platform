import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import SuperAdminLayout from '../components/SuperAdminLayout'
import {
  Building2, Users, TrendingUp, Phone, Cpu, FileText, Plus, UserPlus, Download,
  ArrowUpRight, ArrowDownRight, CheckCircle2,
} from 'lucide-react'
import {
  AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'

interface PlatformStats {
  totalCompanies: number
  activeCompanies: number
  totalUsers: number
  activeCalls: number
  publishedIvrs: number
  aiRequestsToday: number
}

interface MonthlyGrowthPoint {
  month: string
  companies: number
}

interface AiUsagePoint {
  hour: string
  requests: number
}

interface CallsPerDayPoint {
  day: string
  calls: number
  ai: number
}

interface CompanyItem {
  name: string
  plan: string
  users: number
  status: string
  joined: string
}

interface UserItem {
  name: string
  email: string
  company: string
  joined: string
}

interface ActivityItem {
  action: string
  subject: string
  time: string
  type: string
}

const statusColor: Record<string, string> = {
  ACTIVE: 'bg-[#DCFCE7] text-[#15803D]',
  Active: 'bg-[#DCFCE7] text-[#15803D]',
  Trial: 'bg-[#FEF9C3] text-[#A16207]',
  INACTIVE: 'bg-[#FEE2E2] text-[#B91C1C]',
  Inactive: 'bg-[#FEE2E2] text-[#B91C1C]',
}

const activityColor: Record<string, string> = {
  success: 'bg-[#22C55E]',
  info: 'bg-[#2563EB]',
  warning: 'bg-[#F59E0B]',
  danger: 'bg-[#EF4444]',
}

export default function SuperAdminDashboard({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate()
  const [stats, setStats] = useState<PlatformStats | null>(null)
  const [monthlyCompanies, setMonthlyCompanies] = useState<MonthlyGrowthPoint[]>([])
  const [aiUsage, setAiUsage] = useState<AiUsagePoint[]>([])
  const [callsPerDay, setCallsPerDay] = useState<CallsPerDayPoint[]>([])
  const [companies, setCompanies] = useState<CompanyItem[]>([])
  const [recentUsers, setRecentUsers] = useState<UserItem[]>([])
  const [activities, setActivities] = useState<ActivityItem[]>([])
  const [loading, setLoading] = useState(true)

  const fetchDashboardData = async () => {
    try {
      const headers = { 'X-Is-SuperAdmin': 'true' }
      const [sRes, gRes, aRes, cRes, lRes, rRes] = await Promise.all([
        fetch('/api/v1/admin/platform-stats', { headers }).then(r => r.json()).catch(() => null),
        fetch('/api/v1/admin/company-growth', { headers }).then(r => r.json()).catch(() => null),
        fetch('/api/v1/admin/ai-requests-today', { headers }).then(r => r.json()).catch(() => null),
        fetch('/api/v1/admin/calls-per-day', { headers }).then(r => r.json()).catch(() => null),
        fetch('/api/v1/admin/latest-companies', { headers }).then(r => r.json()).catch(() => null),
        fetch('/api/v1/admin/recent-activity', { headers }).then(r => r.json()).catch(() => null),
      ])

      if (sRes?.success && sRes.data) setStats(sRes.data)
      if (gRes?.success && gRes.data) setMonthlyCompanies(gRes.data)
      if (aRes?.success && aRes.data) setAiUsage(aRes.data)
      if (cRes?.success && cRes.data) setCallsPerDay(cRes.data)
      if (lRes?.success && lRes.data) setCompanies(lRes.data)
      if (rRes?.success) {
        if (rRes.data) setActivities(rRes.data)
        if (rRes.users) setRecentUsers(rRes.users)
      }
    } catch (err) {
      console.error('Error fetching super admin dashboard data:', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDashboardData()
    const interval = setInterval(fetchDashboardData, 10000)
    return () => clearInterval(interval)
  }, [])

  const headerActions = (
    <div className="flex gap-2">
      <button
        onClick={() => navigate('/super-admin/users')}
        className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm cursor-pointer"
      >
        <UserPlus className="w-4 h-4" />
        Create Admin
      </button>
      <button
        onClick={() => navigate('/super-admin/reports')}
        className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm cursor-pointer"
      >
        <Download className="w-4 h-4" />
        Report
      </button>
      <button
        onClick={() => navigate('/super-admin/companies')}
        className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20 cursor-pointer"
      >
        <Plus className="w-4 h-4" />
        Add Company
      </button>
    </div>
  )

  const kpis = [
    { label: 'Total Companies', value: stats?.totalCompanies.toLocaleString() ?? '—', delta: '+100%', up: true, icon: <Building2 className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF' },
    { label: 'Active Companies', value: stats?.activeCompanies.toLocaleString() ?? '—', delta: 'Active', up: true, icon: <CheckCircle2 className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4' },
    { label: 'Total Users', value: stats?.totalUsers.toLocaleString() ?? '—', delta: 'System', up: true, icon: <Users className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF' },
    { label: 'Active Calls', value: stats?.activeCalls.toString() ?? '0', delta: 'Live', up: true, icon: <Phone className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB' },
    { label: 'Published IVRs', value: stats?.publishedIvrs.toLocaleString() ?? '—', delta: 'Live', up: true, icon: <FileText className="w-5 h-5" />, color: '#06B6D4', bg: '#ECFEFF' },
    { label: 'AI Requests Today', value: stats?.aiRequestsToday.toLocaleString() ?? '—', delta: 'Today', up: true, icon: <Cpu className="w-5 h-5" />, color: '#EC4899', bg: '#FDF2F8' },
  ]

  const todayStr = new Date().toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })

  return (
    <SuperAdminLayout pageTitle="Platform Overview" pageSubtitle={todayStr} headerActions={headerActions} onLogout={onLogout}>
      {loading ? (
        <div className="flex items-center justify-center h-64 text-[#6B7280]">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#2563EB]"></div>
        </div>
      ) : (
        <div className="space-y-6">
          {/* KPI cards */}
          <div className="grid grid-cols-2 xl:grid-cols-6 gap-4">
            {kpis.map((kpi) => (
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
                  <span className="text-[#22C55E] text-xs font-semibold">Active</span>
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
                <button onClick={() => navigate('/super-admin/companies')} className="text-[#2563EB] text-xs font-medium hover:underline cursor-pointer">View all</button>
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
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusColor[c.status] || 'bg-gray-100 text-gray-700'}`}>
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
                <button onClick={() => navigate('/super-admin/audit-logs')} className="text-[#2563EB] text-xs font-medium hover:underline cursor-pointer">View logs</button>
              </div>
              <div className="p-5 space-y-4">
                {activities.map((a, i) => (
                  <div key={i} className="flex items-start gap-3">
                    <div className={`w-2 h-2 rounded-full mt-1.5 flex-shrink-0 ${activityColor[a.type] || 'bg-[#2563EB]'}`} />
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
                  <button onClick={() => navigate('/super-admin/users')} className="text-[#2563EB] text-xs font-medium hover:underline cursor-pointer">View all</button>
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
        </div>
      )}
    </SuperAdminLayout>
  )
}
