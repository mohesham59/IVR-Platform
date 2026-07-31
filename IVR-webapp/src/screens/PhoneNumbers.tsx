import { useState } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Plus, Upload, Search, ChevronDown, MoreHorizontal, GitBranch,
  PhoneOff, EyeOff, BarChart2, ChevronLeft, ChevronRight,
  Filter, CheckCircle, AlertCircle, TrendingUp, Phone,
  ExternalLink, RefreshCw,
} from 'lucide-react'

const numbers = [
  { id: 1, number: '+1 (415) 882-3301', country: 'US', flag: '🇺🇸', ivr: 'Main Support IVR', ivrColor: '#2563EB', status: 'Active', provider: 'Twilio', usage: 1284, cap: 2000 },
  { id: 2, number: '+1 (312) 445-9921', country: 'US', flag: '🇺🇸', ivr: 'Sales IVR', ivrColor: '#22C55E', status: 'Active', provider: 'Twilio', usage: 642, cap: 1500 },
  { id: 3, number: '+1 (617) 230-0084', country: 'US', flag: '🇺🇸', ivr: 'Billing IVR', ivrColor: '#F59E0B', status: 'Active', provider: 'Vonage', usage: 389, cap: 1000 },
  { id: 4, number: '+44 20 7946 0841', country: 'UK', flag: '🇬🇧', ivr: 'UK Support IVR', ivrColor: '#8B5CF6', status: 'Active', provider: 'Twilio', usage: 210, cap: 800 },
  { id: 5, number: '+49 30 1234 5678', country: 'DE', flag: '🇩🇪', ivr: '—', ivrColor: '', status: 'Unassigned', provider: 'Bandwidth', usage: 0, cap: 500 },
  { id: 6, number: '+1 (929) 551-7742', country: 'US', flag: '🇺🇸', ivr: 'After-Hours IVR', ivrColor: '#EC4899', status: 'Disabled', provider: 'Twilio', usage: 0, cap: 1000 },
  { id: 7, number: '+1 (214) 380-1190', country: 'US', flag: '🇺🇸', ivr: 'Technical Support', ivrColor: '#06B6D4', status: 'Active', provider: 'Vonage', usage: 927, cap: 2000 },
  { id: 8, number: '+1 (503) 770-4423', country: 'US', flag: '🇺🇸', ivr: '—', ivrColor: '', status: 'Unassigned', provider: 'Twilio', usage: 0, cap: 500 },
]

const stats = [
  { label: 'Total Numbers', value: '8', delta: '+2 this month', color: '#2563EB', bg: '#EFF6FF', icon: <Phone className="w-5 h-5" /> },
  { label: 'Active', value: '5', delta: '62.5% of total', color: '#22C55E', bg: '#F0FDF4', icon: <CheckCircle className="w-5 h-5" /> },
  { label: 'Unassigned', value: '2', delta: 'Need IVR', color: '#F59E0B', bg: '#FFFBEB', icon: <AlertCircle className="w-5 h-5" /> },
  { label: "Today's Inbound", value: '3,452', delta: '+8.2% vs yesterday', color: '#8B5CF6', bg: '#F5F3FF', icon: <TrendingUp className="w-5 h-5" /> },
]

const statusConfig: Record<string, { label: string; cls: string; dot: string }> = {
  Active: { label: 'Active', cls: 'bg-[#DCFCE7] text-[#15803D]', dot: 'bg-[#22C55E]' },
  Unassigned: { label: 'Unassigned', cls: 'bg-[#FEF9C3] text-[#A16207]', dot: 'bg-[#F59E0B]' },
  Disabled: { label: 'Disabled', cls: 'bg-[#F3F4F6] text-[#6B7280]', dot: 'bg-[#D1D5DB]' },
}

const providerColor: Record<string, string> = {
  Twilio: 'bg-[#F0F9FF] text-[#0369A1] border-[#BAE6FD]',
  Vonage: 'bg-[#FDF4FF] text-[#7E22CE] border-[#E9D5FF]',
  Bandwidth: 'bg-[#FFF7ED] text-[#C2410C] border-[#FED7AA]',
}

function UsageBar({ used, cap }: { used: number; cap: number }) {
  const pct = Math.round((used / cap) * 100)
  const color = pct > 80 ? '#EF4444' : pct > 60 ? '#F59E0B' : '#22C55E'
  return (
    <div className="flex items-center gap-2">
      <div className="w-20 h-1.5 rounded-full bg-[#F3F4F6] overflow-hidden">
        <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: color }} />
      </div>
      <span className="text-[#9CA3AF] text-[10px] font-mono">{used.toLocaleString()}</span>
    </div>
  )
}

function QuickActionMenu({ onClose }: { onClose: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1 overflow-hidden">
      {[
        { icon: <GitBranch className="w-3.5 h-3.5" />, label: 'Assign IVR', color: '' },
        { icon: <BarChart2 className="w-3.5 h-3.5" />, label: 'View Analytics', color: '' },
        { icon: <RefreshCw className="w-3.5 h-3.5" />, label: 'Re-route', color: '' },
        { icon: <EyeOff className="w-3.5 h-3.5" />, label: 'Disable', color: 'text-[#F59E0B]' },
        { icon: <PhoneOff className="w-3.5 h-3.5" />, label: 'Release Number', color: 'text-[#EF4444]' },
      ].map((item) => (
        <button
          key={item.label}
          onClick={onClose}
          className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${item.color || 'text-[#374151]'}`}
        >
          {item.icon}
          {item.label}
        </button>
      ))}
    </div>
  )
}

export default function PhoneNumbers({ onLogout }: { onLogout: () => void }) {
  const [openMenu, setOpenMenu] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('All Status')
  const [providerFilter, setProviderFilter] = useState('All Providers')

  const filtered = numbers.filter((n) => {
    const ms = statusFilter === 'All Status' || n.status === statusFilter
    const mp = providerFilter === 'All Providers' || n.provider === providerFilter
    const mq = n.number.includes(search) || n.ivr.toLowerCase().includes(search.toLowerCase())
    return ms && mp && mq
  })

  const headerActions = (
    <>
      <button className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Upload className="w-4 h-4" />
        Import Numbers
      </button>
      <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
        <Plus className="w-4 h-4" />
        Assign Number
      </button>
    </>
  )

  return (
    <TenantLayout
      activeNav="phone-numbers"
      onLogout={onLogout}
      pageTitle="Phone Numbers"
      pageSubtitle="Manage inbound DIDs and IVR assignments"
      headerActions={headerActions}
    >
      <div className="space-y-4">
        {/* Stats */}
        <div className="grid grid-cols-4 gap-4">
          {stats.map((s) => (
            <div key={s.label} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-3">
                <div className="w-9 h-9 rounded-lg flex items-center justify-center" style={{ backgroundColor: s.bg, color: s.color }}>
                  {s.icon}
                </div>
              </div>
              <div className="text-[#1F2937] font-bold text-2xl leading-none">{s.value}</div>
              <div className="text-[#9CA3AF] text-xs mt-1">{s.label}</div>
              <div className="text-[#9CA3AF] text-[10px] mt-0.5">{s.delta}</div>
            </div>
          ))}
        </div>

        {/* Filter bar */}
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-4">
          <div className="flex items-center gap-3 flex-wrap">
            <div className="relative flex-1 min-w-[220px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by number or IVR…"
                className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
              />
            </div>
            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-[#9CA3AF]" />
              {[
                { value: statusFilter, setter: setStatusFilter, opts: ['All Status', 'Active', 'Unassigned', 'Disabled'] },
                { value: providerFilter, setter: setProviderFilter, opts: ['All Providers', 'Twilio', 'Vonage', 'Bandwidth'] },
              ].map((f, i) => (
                <div key={i} className="relative">
                  <select
                    value={f.value}
                    onChange={(e) => f.setter(e.target.value)}
                    className="h-9 pl-3 pr-8 rounded-lg border border-[#E5E7EB] bg-white text-sm text-[#374151] font-medium outline-none focus:border-[#2563EB] appearance-none cursor-pointer hover:border-[#2563EB] transition-colors"
                  >
                    {f.opts.map((o) => <option key={o}>{o}</option>)}
                  </select>
                  <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
                </div>
              ))}
            </div>
            <div className="ml-auto text-xs text-[#9CA3AF]">{filtered.length} of {numbers.length} numbers</div>
          </div>
        </div>

        {/* Table */}
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                <th className="w-10 px-4 py-3">
                  <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]" />
                </th>
                {['Phone Number', 'Country', 'Assigned IVR', 'Status', 'Provider', 'Monthly Usage', ''].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#F3F4F6]">
              {filtered.map((num) => {
                const sc = statusConfig[num.status]
                return (
                  <tr key={num.id} className="hover:bg-[#F9FAFB] transition-colors group">
                    <td className="px-4 py-3.5">
                      <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]" />
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-lg bg-[#F3F4F6] flex items-center justify-center flex-shrink-0">
                          <Phone className="w-3.5 h-3.5 text-[#6B7280]" />
                        </div>
                        <span className="text-[#1F2937] font-medium text-xs font-mono tracking-tight">{num.number}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-1.5">
                        <span className="text-lg">{num.flag}</span>
                        <span className="text-[#6B7280] text-xs">{num.country}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      {num.ivr !== '—' ? (
                        <div className="flex items-center gap-2">
                          <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: num.ivrColor }} />
                          <span className="text-[#374151] text-xs font-medium">{num.ivr}</span>
                          <ExternalLink className="w-3 h-3 text-[#9CA3AF] opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                      ) : (
                        <button className="flex items-center gap-1.5 text-[#2563EB] text-xs font-medium hover:underline">
                          <Plus className="w-3 h-3" />
                          Assign IVR
                        </button>
                      )}
                    </td>
                    <td className="px-4 py-3.5">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold ${sc.cls}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${sc.dot}`} />
                        {sc.label}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-md border text-[10px] font-semibold ${providerColor[num.provider]}`}>
                        {num.provider}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      <UsageBar used={num.usage} cap={num.cap} />
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="relative">
                        <button
                          onClick={() => setOpenMenu(openMenu === num.id ? null : num.id)}
                          className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] hover:text-[#374151] transition-colors opacity-0 group-hover:opacity-100"
                        >
                          <MoreHorizontal className="w-4 h-4" />
                        </button>
                        {openMenu === num.id && <QuickActionMenu onClose={() => setOpenMenu(null)} />}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {/* Empty state */}
          {filtered.length === 0 && (
            <div className="flex flex-col items-center justify-center py-16">
              <div className="w-14 h-14 rounded-2xl bg-[#F3F4F6] flex items-center justify-center mb-4">
                <Phone className="w-7 h-7 text-[#D1D5DB]" />
              </div>
              <p className="text-[#374151] font-medium text-sm">No phone numbers found</p>
              <p className="text-[#9CA3AF] text-xs mt-1">Try adjusting your search or filters</p>
            </div>
          )}

          {/* Pagination */}
          <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
            <p className="text-[#9CA3AF] text-xs">Showing {filtered.length} of {numbers.length} numbers</p>
            <div className="flex items-center gap-1">
              <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] transition-colors">
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
              <button className="w-7 h-7 rounded-lg border bg-[#2563EB] border-[#2563EB] text-white text-xs font-medium">1</button>
              <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white text-[#6B7280] text-xs hover:border-[#2563EB] transition-colors">2</button>
              <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] transition-colors">
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </TenantLayout>
  )
}
