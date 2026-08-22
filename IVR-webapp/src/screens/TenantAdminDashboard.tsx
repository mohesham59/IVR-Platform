import { useState, useEffect } from 'react'
import type { ReactElement } from 'react'
import { useNavigate } from 'react-router-dom'
import TenantLayout from '../components/TenantLayout'
import {
  GitBranch, Volume2, Radio, Clock, CheckCircle,
  PhoneMissed, PhoneCall, Headphones, List, ChevronLeft, ChevronRight,
  ArrowUpRight, ArrowDownRight, Download
} from 'lucide-react'
import {
  AreaChart, Area, BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell,
} from 'recharts'

interface DashboardStats {
  totalCalls: number
  answeredCalls: number
  missedCalls: number
  avgDurationSeconds: number
  publishedIvrs: number
  activeAgents: number
  queues: number
  voicePrompts: number
  totalCallsTrend: number
  answeredTrend: number
  missedTrend: number
  avgDurationTrend: number
}

interface CallVolumePoint {
  time: string
  inbound: number
  outbound: number
}

interface CallDistPoint {
  name: string
  value: number
  color?: string
}

interface AgentPerfPoint {
  agent: string
  calls: number
}

interface QueuePerfPoint {
  queue: string
  waiting: number
  avgWait: number
  status: string
}

interface RecentCall {
  id: string
  sessionId: string
  callerId: string
  scenarioName: string
  status: string
  startTime: string
  duration: number
  lastNode?: string
}

const DIST_COLORS = ['#2563EB', '#22C55E', '#F59E0B', '#EC4899', '#8B5CF6']

const statusStyle: Record<string, string> = {
  ANSWERED: 'bg-[#DCFCE7] text-[#15803D]',
  MISSED: 'bg-[#FEE2E2] text-[#B91C1C]',
  BUSY: 'bg-[#FEF3C7] text-[#D97706]',
  FAILED: 'bg-[#FEE2E2] text-[#B91C1C]',
}

const statusIcon: Record<string, ReactElement> = {
  ANSWERED: <CheckCircle className="w-3 h-3" />,
  MISSED: <PhoneMissed className="w-3 h-3" />,
  BUSY: <PhoneMissed className="w-3 h-3" />,
  FAILED: <PhoneMissed className="w-3 h-3" />,
}

export default function TenantAdminDashboard({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [callVolume, setCallVolume] = useState<CallVolumePoint[]>([])
  const [callDist, setCallDist] = useState<CallDistPoint[]>([])
  const [agentPerf, setAgentPerf] = useState<AgentPerfPoint[]>([])
  const [queuePerf, setQueuePerf] = useState<QueuePerfPoint[]>([])
  const [recentCalls, setRecentCalls] = useState<RecentCall[]>([])

  const tenantId = localStorage.getItem('tenant_id') || '11111111-1111-1111-1111-111111111111'

  const fetchDashboardData = async () => {
    try {
      const headers = { 'X-Tenant-ID': tenantId }

      const [sRes, vRes, dRes, aRes, qRes, rRes] = await Promise.all([
        fetch('/api/v1/dashboard/stats', { headers }).then(r => r.json()),
        fetch('/api/v1/dashboard/call-volume', { headers }).then(r => r.json()),
        fetch('/api/v1/dashboard/call-distribution', { headers }).then(r => r.json()),
        fetch('/api/v1/dashboard/agent-performance', { headers }).then(r => r.json()),
        fetch('/api/v1/dashboard/queue-performance', { headers }).then(r => r.json()),
        fetch('/api/v1/dashboard/recent-calls?limit=10', { headers }).then(r => r.json()),
      ])

      if (sRes.success) setStats(sRes.data)
      if (vRes.success) setCallVolume(vRes.data)
      if (dRes.success) {
        const formatted = (dRes.data || []).map((item: any, idx: number) => ({
          ...item,
          color: DIST_COLORS[idx % DIST_COLORS.length]
        }))
        setCallDist(formatted)
      }
      if (aRes.success) setAgentPerf(aRes.data)
      if (qRes.success) setQueuePerf(qRes.data)
      if (rRes.success) setRecentCalls(rRes.data || [])

    } catch (err) {
      console.error('Error loading dashboard data:', err)
    }
  }

  useEffect(() => {
    fetchDashboardData()
    const interval = setInterval(fetchDashboardData, 5000)
    return () => clearInterval(interval)
  }, [tenantId])

  const handleExportCsv = () => {
    window.open(`/api/v1/dashboard/recent-calls/export?tenant_id=${tenantId}`, '_blank')
  }

  const formatDur = (sec: number): string => {
    const s = Math.round(sec || 0)
    const m = Math.floor(s / 60)
    const r = s % 60
    return m > 0 ? `${m}m ${r.toString().padStart(2, '0')}s` : `${r}s`
  }

  const formatRelativeTime = (timeStr?: string): string => {
    if (!timeStr) return 'just now'
    const time = new Date(timeStr).getTime()
    if (isNaN(time)) return 'just now'
    const now = Date.now()
    const diffSec = Math.floor((now - time) / 1000)
    if (diffSec < 60) return 'just now'
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return `${diffMin}m ago`
    const diffHours = Math.floor(diffMin / 60)
    if (diffHours < 24) return `${diffHours}h ago`
    const diffDays = Math.floor(diffHours / 24)
    return `${diffDays}d ago`
  }

  const formatTrend = (val?: number) => {
    if (val === undefined || val === null) return '0%'
    const sign = val >= 0 ? '+' : ''
    return `${sign}${val}%`
  }

  const kpiCards = [
    {
      label: "Total Calls",
      value: stats?.totalCalls.toLocaleString() ?? '—',
      delta: formatTrend(stats?.totalCallsTrend),
      up: (stats?.totalCallsTrend ?? 0) >= 0,
      icon: <PhoneCall className="w-4 h-4" />,
      color: '#2563EB',
      bg: '#EFF6FF'
    },
    {
      label: 'Answered',
      value: stats?.answeredCalls.toLocaleString() ?? '—',
      delta: formatTrend(stats?.answeredTrend),
      up: (stats?.answeredTrend ?? 0) >= 0,
      icon: <CheckCircle className="w-4 h-4" />,
      color: '#22C55E',
      bg: '#F0FDF4'
    },
    {
      label: 'Missed Calls',
      value: stats?.missedCalls.toLocaleString() ?? '—',
      delta: formatTrend(stats?.missedTrend),
      up: (stats?.missedTrend ?? 0) <= 0,
      icon: <PhoneMissed className="w-4 h-4" />,
      color: '#EF4444',
      bg: '#FEF2F2'
    },
    {
      label: 'Avg Duration',
      value: stats ? formatDur(stats.avgDurationSeconds) : '—',
      delta: formatTrend(stats?.avgDurationTrend),
      up: (stats?.avgDurationTrend ?? 0) >= 0,
      icon: <Clock className="w-4 h-4" />,
      color: '#F59E0B',
      bg: '#FFFBEB'
    },
    {
      label: 'Published IVRs',
      value: stats?.publishedIvrs.toString() ?? '—',
      delta: 'Active',
      up: true,
      icon: <GitBranch className="w-4 h-4" />,
      color: '#8B5CF6',
      bg: '#F5F3FF'
    },
    {
      label: 'Active Agents',
      value: stats?.activeAgents.toString() ?? '—',
      delta: 'Online',
      up: true,
      icon: <Headphones className="w-4 h-4" />,
      color: '#06B6D4',
      bg: '#ECFEFF'
    },
    {
      label: 'Queues',
      value: stats?.queues.toString() ?? '—',
      delta: 'Configured',
      up: true,
      icon: <List className="w-4 h-4" />,
      color: '#EC4899',
      bg: '#FDF2F8'
    },
    {
      label: 'Voice Prompts',
      value: stats?.voicePrompts.toString() ?? '—',
      delta: 'Ready',
      up: true,
      icon: <Volume2 className="w-4 h-4" />,
      color: '#10B981',
      bg: '#F0FDF4'
    },
  ]

  return (
    <TenantLayout
      pageTitle="Operations Dashboard"
      pageSubtitle="Live System Metrics & Telephony Operations"
      onLogout={onLogout}
      headerActions={
        <div className="flex gap-2">
          <button
            onClick={() => navigate('/tenant/call-analytics')}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm cursor-pointer"
          >
            <Radio className="w-4 h-4 text-[#2563EB]" />
            Live Monitor
          </button>
          <button
            onClick={() => navigate('/tenant/ivr-builder')}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20 cursor-pointer"
          >
            <GitBranch className="w-4 h-4" />
            Open IVR Builder
          </button>
        </div>
      }
    >
      <div className="space-y-6">

        {/* KPI cards — 8 cards */}
        <div className="grid grid-cols-4 xl:grid-cols-8 gap-3">
          {kpiCards.map((kpi) => (
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
            {callDist.length === 0 ? (
              <div className="flex items-center justify-center h-[120px] text-[#9CA3AF] text-xs">
                No data available for this period
              </div>
            ) : (
              <>
                <ResponsiveContainer width="100%" height={120}>
                  <PieChart>
                    <Pie data={callDist} cx="50%" cy="50%" innerRadius={35} outerRadius={55} paddingAngle={3} dataKey="value">
                      {callDist.map((entry: any, index: number) => (
                        <Cell key={index} fill={entry.color || DIST_COLORS[index % DIST_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="grid grid-cols-2 gap-1.5 mt-3">
                  {callDist.map((d: any) => (
                    <div key={d.name} className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: d.color }} />
                      <span className="text-[#6B7280] text-[10px] truncate">{d.name}</span>
                      <span className="text-[#1F2937] text-[10px] font-semibold ml-auto">{d.value}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
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
            {agentPerf.length === 0 ? (
              <div className="flex items-center justify-center h-[150px] text-[#9CA3AF] text-xs">
                No data available for this period
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={150}>
                <BarChart data={agentPerf} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="agent" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                  <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                  <Bar dataKey="calls" fill="#2563EB" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Queue performance */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-[#1F2937] font-semibold text-sm">Queue Performance</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">Avg wait time (sec) & waiting calls</p>
              </div>
            </div>
            <ResponsiveContainer width="100%" height={150}>
              <LineChart data={queuePerf} margin={{ top: 0, right: 0, left: -24, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                <XAxis dataKey="queue" tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12 }} />
                <Line type="monotone" dataKey="avgWait" stroke="#2563EB" strokeWidth={2} dot={false} name="Avg Wait (s)" />
                <Line type="monotone" dataKey="waiting" stroke="#EF4444" strokeWidth={2} dot={false} strokeDasharray="4 4" name="Waiting Calls" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Recent Calls Table */}
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
            <div>
              <h3 className="text-[#1F2937] font-semibold text-sm">Recent Calls</h3>
              <p className="text-[#9CA3AF] text-xs mt-0.5">Real-time CDR history log</p>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={handleExportCsv}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#F9FAFB] border border-[#E5E7EB] text-[#6B7280] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all cursor-pointer"
              >
                <Download className="w-3.5 h-3.5" />
                Export CSV
              </button>
            </div>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F9FAFB]">
                {['Caller Number', 'Status', 'Duration', 'Scenario / Last Node', 'Time'].map((h) => (
                  <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#F3F4F6]">
              {recentCalls.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-5 py-8 text-center text-[#9CA3AF] text-xs">
                    No recent call activity recorded yet
                  </td>
                </tr>
              ) : (
                recentCalls.map((call, i) => (
                  <tr key={call.id || i} className="hover:bg-[#F9FAFB] transition-colors">
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-full bg-[#EFF6FF] flex items-center justify-center flex-shrink-0">
                          <PhoneCall className="w-3 h-3 text-[#2563EB]" />
                        </div>
                        <span className="text-[#1F2937] font-medium text-xs font-mono">{call.callerId}</span>
                      </div>
                    </td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusStyle[call.status] || 'bg-[#F3F4F6] text-[#374151]'}`}>
                        {statusIcon[call.status]}
                        {call.status}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-[#6B7280] text-xs font-mono">{formatDur(call.duration)}</td>
                    <td className="px-5 py-3">
                      <div className="flex flex-col">
                        <span className="inline-flex items-center w-fit px-2 py-0.5 rounded-md bg-[#F3F4F6] text-[#374151] text-[10px] font-medium">
                          {call.scenarioName || 'Support L1'}
                        </span>
                        {call.lastNode && (
                          <span className="text-[10px] text-[#9CA3AF] mt-0.5 font-mono">
                            Node: {call.lastNode}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3 text-[#9CA3AF] text-xs">
                      {formatRelativeTime(call.startTime)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>

          {/* Pagination */}
          <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
            <p className="text-[#9CA3AF] text-xs">
              Showing {recentCalls.length > 0 ? '1' : '0'}–{recentCalls.length} of {stats?.totalCalls ?? recentCalls.length} calls
            </p>
            <div className="flex items-center gap-1">
              <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] hover:text-[#2563EB] transition-colors">
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
              <button className="w-7 h-7 rounded-lg border border-[#2563EB] bg-[#2563EB] text-white text-xs font-medium">
                1
              </button>
              <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] hover:text-[#2563EB] transition-colors">
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </TenantLayout>
  )
}
