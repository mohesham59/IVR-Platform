import { useState, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import { aiApi, AnalyticsApiResponse } from '../api/aiApi'
import {
  AreaChart, Area, BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'
import { Download, FileText, Calendar, ChevronDown, TrendingUp, TrendingDown } from 'lucide-react'

const DAILY = [
  { day: 'Jul 12', calls: 312, answered: 280, abandoned: 32 },
  { day: 'Jul 13', calls: 291, answered: 265, abandoned: 26 },
  { day: 'Jul 14', calls: 178, answered: 160, abandoned: 18 },
  { day: 'Jul 15', calls: 155, answered: 140, abandoned: 15 },
  { day: 'Jul 16', calls: 348, answered: 315, abandoned: 33 },
  { day: 'Jul 17', calls: 388, answered: 350, abandoned: 38 },
  { day: 'Jul 18', calls: 214, answered: 197, abandoned: 17 },
]

const HOURLY = [
  { hour: '06', calls: 12 }, { hour: '07', calls: 28 }, { hour: '08', calls: 65 },
  { hour: '09', calls: 118 }, { hour: '10', calls: 142 }, { hour: '11', calls: 156 },
  { hour: '12', calls: 98 }, { hour: '13', calls: 112 }, { hour: '14', calls: 148 },
  { hour: '15', calls: 135 }, { hour: '16', calls: 94 }, { hour: '17', calls: 62 },
  { hour: '18', calls: 31 }, { hour: '19', calls: 18 },
]

const QUEUE_PERF = [
  { queue: 'Support', sla: 88, abandoned: 12, avg: 4.2 },
  { queue: 'Billing', sla: 75, abandoned: 25, avg: 5.8 },
  { queue: 'Sales', sla: 95, abandoned: 5, avg: 3.1 },
  { queue: 'Technical', sla: 70, abandoned: 30, avg: 7.2 },
  { queue: 'General', sla: 60, abandoned: 40, avg: 8.9 },
]

const AGENT_PERF = [
  { agent: 'Natalie R.', handled: 42, avgHandle: 4.2, csat: 4.8 },
  { agent: 'James K.', handled: 38, avgHandle: 6.1, csat: 4.6 },
  { agent: 'Priya N.', handled: 51, avgHandle: 3.8, csat: 4.9 },
  { agent: 'Tom B.', handled: 29, avgHandle: 5.4, csat: 4.3 },
  { agent: 'Sofia A.', handled: 44, avgHandle: 4.7, csat: 4.7 },
]

const AI_USAGE = [
  { name: 'Self-Served', value: 38, color: '#2563EB' },
  { name: 'Escalated', value: 22, color: '#7C3AED' },
  { name: 'Assisted', value: 18, color: '#22C55E' },
  { name: 'No AI', value: 22, color: '#E5E7EB' },
]

const PEAK = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(day => ({
  day,
  hours: Array.from({ length: 14 }, (_, i) => ({
    hour: `${(i + 6).toString().padStart(2, '0')}:00`,
    value: Math.round(Math.random() * 100),
  })),
}))

const MISSED = [
  { day: 'Jul 12', missed: 32 }, { day: 'Jul 13', missed: 26 },
  { day: 'Jul 14', missed: 18 }, { day: 'Jul 15', missed: 15 },
  { day: 'Jul 16', missed: 33 }, { day: 'Jul 17', missed: 38 }, { day: 'Jul 18', missed: 17 },
]

const KPIS = [
  { label: 'Answered Rate', value: '90.8%', change: '+2.1%', up: true, color: '#22C55E', bg: '#F0FDF4' },
  { label: 'Abandoned Rate', value: '9.2%', change: '-2.1%', up: false, color: '#EF4444', bg: '#FEF2F2', inverse: true },
  { label: 'Avg Wait Time', value: '2m 14s', change: '-0m 18s', up: false, color: '#F59E0B', bg: '#FFFBEB', inverse: true },
  { label: 'Avg Handle Time', value: '4m 38s', change: '+0m 12s', up: true, color: '#6366F1', bg: '#EEF2FF' },
  { label: 'CSAT Score', value: '4.7 / 5', change: '+0.2', up: true, color: '#2563EB', bg: '#EFF6FF' },
  { label: 'AI Automation', value: '38.4%', change: '+5.8%', up: true, color: '#8B5CF6', bg: '#F5F3FF' },
]

const tooltipStyle = { backgroundColor: '#1F2937', border: 'none', borderRadius: 8, fontSize: 12 }

export default function Reports({ onLogout }: { onLogout: () => void }) {
  const [dateRange] = useState('Last 7 Days')
  const [analytics, setAnalytics] = useState<AnalyticsApiResponse | null>(null)

  useEffect(() => {
    aiApi.fetchAnalytics().then(res => {
      if (res) setAnalytics(res)
    }).catch(err => {
      console.warn('Analytics API endpoint loaded with note:', err)
    })
  }, [])

  useEffect(() => {
    if (analytics) {
      console.debug('Analytics updated:', analytics.totalSessions)
    }
  }, [analytics])

  const headerActions = (
    <div className="flex gap-2">
      <button className="flex items-center gap-1.5 px-3 py-2 bg-white border border-[#E5E7EB] rounded-lg text-[#374151] text-sm hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Calendar className="w-4 h-4" /> {dateRange} <ChevronDown className="w-3.5 h-3.5 text-[#9CA3AF]" />
      </button>
      {[{ label: 'PDF', icon: <FileText className="w-3.5 h-3.5" /> }, { label: 'Excel', icon: <Download className="w-3.5 h-3.5" /> }, { label: 'CSV', icon: <Download className="w-3.5 h-3.5" /> }].map(b => (
        <button key={b.label} className="flex items-center gap-1.5 px-3 py-2 bg-white border border-[#E5E7EB] rounded-lg text-[#374151] text-sm hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
          {b.icon} {b.label}
        </button>
      ))}
    </div>
  )

  return (
    <TenantLayout activeNav="reports" onLogout={onLogout}
      pageTitle="Reports & Analytics" pageSubtitle="Jul 12 – Jul 18, 2026 · 1,886 total calls"
      headerActions={headerActions}>
      <div className="space-y-4">
        {/* KPI cards */}
        <div className="grid grid-cols-6 gap-3">
          {KPIS.map(k => {
            const isPositive = k.up !== (k as any).inverse
            return (
              <div key={k.label} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow">
                <p className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wide mb-2">{k.label}</p>
                <p className="text-[#1F2937] font-bold text-xl leading-none mb-2">{k.value}</p>
                <div className={`flex items-center gap-1 text-[10px] font-medium ${isPositive ? 'text-[#22C55E]' : 'text-[#EF4444]'}`}>
                  {isPositive ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                  {k.change} vs prev
                </div>
              </div>
            )
          })}
        </div>

        {/* Row 1: Calls per day + Calls per hour */}
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-4">Calls Per Day</h3>
            <ResponsiveContainer width="100%" height={180}>
              <AreaChart data={DAILY}>
                <defs>
                  <linearGradient id="gAnswered" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563EB" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#2563EB" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="gAbandoned" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#EF4444" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#EF4444" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" />
                <XAxis dataKey="day" tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: '#fff' }} />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Area type="monotone" dataKey="answered" name="Answered" stroke="#2563EB" fill="url(#gAnswered)" strokeWidth={2} dot={false} />
                <Area type="monotone" dataKey="abandoned" name="Abandoned" stroke="#EF4444" fill="url(#gAbandoned)" strokeWidth={2} dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-4">Calls Per Hour (Today)</h3>
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={HOURLY}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" />
                <XAxis dataKey="hour" tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: '#fff' }} />
                <Bar dataKey="calls" name="Calls" fill="#2563EB" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Row 2: Queue perf + Agent perf */}
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-4">Queue Performance (SLA %)</h3>
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={QUEUE_PERF} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" horizontal={false} />
                <XAxis type="number" tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} domain={[0, 100]} />
                <YAxis type="category" dataKey="queue" tick={{ fontSize: 11, fill: '#374151' }} axisLine={false} tickLine={false} width={60} />
                <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: '#fff' }} />
                <Bar dataKey="sla" name="SLA %" fill="#22C55E" radius={[0, 3, 3, 0]} />
                <Bar dataKey="abandoned" name="Abandon %" fill="#EF4444" radius={[0, 3, 3, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-4">Agent Performance</h3>
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={AGENT_PERF}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" />
                <XAxis dataKey="agent" tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: '#fff' }} />
                <Bar dataKey="handled" name="Calls Handled" fill="#2563EB" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Row 3: Missed + AI + IVR heatmap */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-4">Missed / Abandoned</h3>
            <ResponsiveContainer width="100%" height={160}>
              <LineChart data={MISSED}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" />
                <XAxis dataKey="day" tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#9CA3AF' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: '#fff' }} />
                <Line type="monotone" dataKey="missed" name="Missed" stroke="#EF4444" strokeWidth={2} dot={{ fill: '#EF4444', r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-4">AI Automation Breakdown</h3>
            <div className="flex items-center gap-4">
              <ResponsiveContainer width={130} height={130}>
                <PieChart>
                  <Pie data={AI_USAGE} cx="50%" cy="50%" innerRadius={36} outerRadius={56} paddingAngle={2} dataKey="value">
                    {AI_USAGE.map((e, i) => <Cell key={i} fill={e.color} />)}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
              <div className="flex-1 space-y-2">
                {AI_USAGE.map(s => (
                  <div key={s.name} className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: s.color }} />
                    <div className="flex-1 flex justify-between">
                      <span className="text-[#374151] text-xs">{s.name}</span>
                      <span className="text-[#1F2937] text-xs font-semibold">{s.value}%</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <div className="bg-white rounded-xl border border-[#E5E7EB] p-5 shadow-sm">
            <h3 className="text-[#1F2937] font-semibold text-sm mb-3">Peak Hours Heatmap</h3>
            <div className="flex gap-1">
              {PEAK.map(d => (
                <div key={d.day} className="flex-1">
                  <p className="text-center text-[9px] text-[#9CA3AF] mb-1">{d.day}</p>
                  <div className="flex flex-col gap-px">
                    {d.hours.map(h => (
                      <div key={h.hour} title={`${d.day} ${h.hour}: ${h.value} calls`}
                        className="w-full rounded-sm"
                        style={{ height: 9, backgroundColor: h.value > 75 ? '#1E40AF' : h.value > 50 ? '#2563EB' : h.value > 25 ? '#93C5FD' : '#DBEAFE' }} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
            <div className="flex items-center gap-2 mt-3 justify-end">
              {[['#DBEAFE', 'Low'], ['#93C5FD', 'Med'], ['#2563EB', 'High'], ['#1E40AF', 'Peak']].map(([color, label]) => (
                <div key={label} className="flex items-center gap-1">
                  <div className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: color }} />
                  <span className="text-[#9CA3AF] text-[9px]">{label}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </TenantLayout>
  )
}
