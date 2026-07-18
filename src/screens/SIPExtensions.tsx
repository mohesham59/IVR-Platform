import { useState } from 'react'
import type { ReactElement } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Plus, Search, MoreHorizontal, X, Server,
  Pencil, Trash2, RefreshCw, ChevronLeft, ChevronRight,
  PhoneCall, Wifi, WifiOff, Radio, Eye, EyeOff,
  List, Shield,
} from 'lucide-react'

const extensions = [
  { id: 1, ext: '1000', name: 'Marcus Webb', user: 'Marcus Webb', userColor: 'from-[#2563EB] to-[#7C3AED]', userAvatar: 'MW', reg: 'Registered', call: 'Idle', lastReg: '2 min ago', transport: 'TLS', codec: 'G.711, G.722', ip: '10.0.1.42' },
  { id: 2, ext: '1001', name: 'Natalie Rodriguez', user: 'Natalie Rodriguez', userColor: 'from-[#059669] to-[#0891B2]', userAvatar: 'NR', reg: 'Registered', call: 'In Call', lastReg: '5 min ago', transport: 'TLS', codec: 'G.711', ip: '10.0.1.43' },
  { id: 3, ext: '1002', name: 'James Kowalski', user: 'James Kowalski', userColor: 'from-[#D97706] to-[#DC2626]', userAvatar: 'JK', reg: 'Registered', call: 'In Call', lastReg: '3 min ago', transport: 'UDP', codec: 'G.711, Opus', ip: '10.0.1.44' },
  { id: 4, ext: '1003', name: 'Priya Nair', user: 'Priya Nair', userColor: 'from-[#7C3AED] to-[#DB2777]', userAvatar: 'PN', reg: 'Registered', call: 'Idle', lastReg: '1 min ago', transport: 'TLS', codec: 'G.722', ip: '10.0.1.45' },
  { id: 5, ext: '1004', name: 'Tom Brecker', user: 'Tom Brecker', userColor: 'from-[#0284C7] to-[#059669]', userAvatar: 'TB', reg: 'Registering', call: '—', lastReg: '8 min ago', transport: 'TLS', codec: 'G.711', ip: '10.0.1.46' },
  { id: 6, ext: '1005', name: 'Sofia Alvarez', user: 'Sofia Alvarez', userColor: 'from-[#BE185D] to-[#7C3AED]', userAvatar: 'SA', reg: 'Offline', call: '—', lastReg: '2h ago', transport: 'TLS', codec: 'G.711, G.722', ip: '—' },
  { id: 7, ext: '1006', name: 'Darius Okafor', user: 'Unassigned', userColor: 'from-[#6B7280] to-[#9CA3AF]', userAvatar: '—', reg: 'Offline', call: '—', lastReg: 'Never', transport: 'UDP', codec: 'G.711', ip: '—' },
  { id: 8, ext: '1007', name: 'Conference Room A', user: 'Unassigned', userColor: 'from-[#6B7280] to-[#9CA3AF]', userAvatar: '—', reg: 'Registered', call: 'Idle', lastReg: '10 min ago', transport: 'TLS', codec: 'G.711', ip: '10.0.1.50' },
]

const regHistory = [
  { event: 'Registered', time: 'Dec 12 — 10:21 AM', ip: '10.0.1.42', duration: 'Active for 2h 14m' },
  { event: 'Unregistered', time: 'Dec 11 — 06:00 PM', ip: '10.0.1.42', duration: 'Session lasted 9h 12m' },
  { event: 'Registered', time: 'Dec 11 — 08:48 AM', ip: '10.0.1.42', duration: 'Active' },
]

const regConfig: Record<string, { cls: string; dotCls: string; icon: ReactElement }> = {
  Registered: {
    cls: 'bg-[#DCFCE7] text-[#15803D]',
    dotCls: 'bg-[#22C55E]',
    icon: <Wifi className="w-3 h-3" />,
  },
  Registering: {
    cls: 'bg-[#FEF9C3] text-[#A16207]',
    dotCls: 'bg-[#F59E0B] animate-pulse',
    icon: <Radio className="w-3 h-3" />,
  },
  Offline: {
    cls: 'bg-[#F3F4F6] text-[#6B7280]',
    dotCls: 'bg-[#D1D5DB]',
    icon: <WifiOff className="w-3 h-3" />,
  },
}

const callConfig: Record<string, { cls: string }> = {
  Idle: { cls: 'text-[#22C55E]' },
  'In Call': { cls: 'text-[#2563EB]' },
  '—': { cls: 'text-[#D1D5DB]' },
}

function ExtActionMenu({ onClose }: { onClose: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1 overflow-hidden">
      {[
        { icon: <Eye className="w-3.5 h-3.5" />, label: 'View Details', color: '' },
        { icon: <Pencil className="w-3.5 h-3.5" />, label: 'Edit Extension', color: '' },
        { icon: <RefreshCw className="w-3.5 h-3.5" />, label: 'Force Re-register', color: '' },
        { icon: <PhoneCall className="w-3.5 h-3.5" />, label: 'Test Call', color: '' },
        { icon: <Trash2 className="w-3.5 h-3.5" />, label: 'Delete', color: 'text-[#EF4444]' },
      ].map((item) => (
        <button key={item.label} onClick={onClose} className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${item.color || 'text-[#374151]'}`}>
          {item.icon}{item.label}
        </button>
      ))}
    </div>
  )
}

export default function SIPExtensions({ onLogout }: { onLogout: () => void }) {
  const [selected, setSelected] = useState<typeof extensions[0] | null>(null)
  const [openMenu, setOpenMenu] = useState<number | null>(null)
  const [showSecret, setShowSecret] = useState(false)
  const [search, setSearch] = useState('')
  const [regFilter, setRegFilter] = useState('All')

  const registered = extensions.filter(e => e.reg === 'Registered').length
  const registering = extensions.filter(e => e.reg === 'Registering').length
  const offline = extensions.filter(e => e.reg === 'Offline').length
  const busy = extensions.filter(e => e.call === 'In Call').length

  const filtered = extensions.filter((e) => {
    const mr = regFilter === 'All' || e.reg === regFilter
    const ms = e.ext.includes(search) || e.name.toLowerCase().includes(search.toLowerCase())
    return mr && ms
  })

  const headerActions = (
    <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
      <Plus className="w-4 h-4" />
      Add Extension
    </button>
  )

  return (
    <TenantLayout
      activeNav="sip"
      onLogout={onLogout}
      pageTitle="SIP Extensions"
      pageSubtitle="Manage SIP accounts connected to Asterisk"
      headerActions={headerActions}
    >
      <div className="flex gap-4">
        {/* Main */}
        <div className="flex-1 min-w-0 space-y-4">
          {/* Stats */}
          <div className="grid grid-cols-4 gap-4">
            {[
              { label: 'Total Extensions', value: extensions.length, icon: <Server className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF', sub: 'Configured' },
              { label: 'Registered', value: registered, icon: <Wifi className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4', sub: 'Online now' },
              { label: 'Registering', value: registering, icon: <Radio className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB', sub: 'Connecting' },
              { label: 'Offline', value: offline, icon: <WifiOff className="w-5 h-5" />, color: '#EF4444', bg: '#FEF2F2', sub: 'Unreachable' },
              { label: 'Busy / In Call', value: busy, icon: <PhoneCall className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF', sub: 'Active calls' },
              { label: 'TLS Secured', value: extensions.filter(e => e.transport === 'TLS').length, icon: <Shield className="w-5 h-5" />, color: '#06B6D4', bg: '#ECFEFF', sub: 'Encrypted' },
            ].map((s) => (
              <div key={s.label} className={`bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow ${s.label === 'Busy / In Call' || s.label === 'TLS Secured' ? 'hidden xl:block' : ''}`}>
                <div className="w-9 h-9 rounded-lg flex items-center justify-center mb-3" style={{ backgroundColor: s.bg, color: s.color }}>
                  {s.icon}
                </div>
                <div className="text-[#1F2937] font-bold text-2xl leading-none">{s.value}</div>
                <div className="text-[#9CA3AF] text-xs mt-1">{s.label}</div>
              </div>
            ))}
          </div>

          {/* Filter bar */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-4">
            <div className="flex items-center gap-3 flex-wrap">
              <div className="relative flex-1 min-w-[200px]">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search by extension or display name…"
                  className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
                />
              </div>
              <div className="flex gap-1.5">
                {['All', 'Registered', 'Registering', 'Offline'].map((opt) => (
                  <button
                    key={opt}
                    onClick={() => setRegFilter(opt)}
                    className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${regFilter === opt ? 'bg-[#2563EB] text-white' : 'bg-[#F3F4F6] text-[#6B7280] hover:bg-[#E5E7EB]'}`}
                  >
                    {opt}
                  </button>
                ))}
              </div>
              <div className="ml-auto text-xs text-[#9CA3AF]">{filtered.length} extensions</div>
            </div>
          </div>

          {/* Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                  {['Extension', 'Display Name', 'Assigned User', 'Registration', 'Call Status', 'Last Seen', ''].map((h) => (
                    <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {filtered.map((ext) => {
                  const rc = regConfig[ext.reg]
                  const cc = callConfig[ext.call]
                  return (
                    <tr
                      key={ext.id}
                      onClick={() => { setSelected(ext); setOpenMenu(null) }}
                      className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer group ${selected?.id === ext.id ? 'bg-[#EFF6FF]' : ''}`}
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2">
                          <div className={`w-2 h-2 rounded-full flex-shrink-0 ${rc.dotCls}`} />
                          <span className="text-[#1F2937] font-bold text-sm font-mono">{ext.ext}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 text-[#374151] text-xs font-medium">{ext.name}</td>
                      <td className="px-4 py-3.5">
                        {ext.user !== 'Unassigned' ? (
                          <div className="flex items-center gap-2">
                            <div className={`w-6 h-6 rounded-full bg-gradient-to-br ${ext.userColor} flex items-center justify-center text-white text-[9px] font-bold flex-shrink-0`}>
                              {ext.userAvatar}
                            </div>
                            <span className="text-[#374151] text-xs">{ext.user}</span>
                          </div>
                        ) : (
                          <span className="text-[#9CA3AF] text-xs italic">Unassigned</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold ${rc.cls}`}>
                          {rc.icon}
                          {ext.reg}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className={`text-xs font-medium ${cc.cls}`}>{ext.call}</span>
                      </td>
                      <td className="px-4 py-3.5 text-[#9CA3AF] text-xs">{ext.lastReg}</td>
                      <td className="px-4 py-3.5" onClick={(e) => e.stopPropagation()}>
                        <div className="relative">
                          <button
                            onClick={() => setOpenMenu(openMenu === ext.id ? null : ext.id)}
                            className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors opacity-0 group-hover:opacity-100"
                          >
                            <MoreHorizontal className="w-4 h-4" />
                          </button>
                          {openMenu === ext.id && <ExtActionMenu onClose={() => setOpenMenu(null)} />}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>

            <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
              <p className="text-[#9CA3AF] text-xs">Showing {filtered.length} of {extensions.length} extensions</p>
              <div className="flex items-center gap-1">
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] transition-colors">
                  <ChevronLeft className="w-3.5 h-3.5" />
                </button>
                <button className="w-7 h-7 rounded-lg border bg-[#2563EB] border-[#2563EB] text-white text-xs font-medium">1</button>
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] transition-colors">
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Side panel */}
        {selected && (
          <div className="w-72 flex-shrink-0 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col" style={{ maxHeight: 'calc(100vh - 160px)', position: 'sticky', top: 0 }}>
            {/* Header */}
            <div className="flex items-start justify-between p-5 border-b border-[#F3F4F6]">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-[#1F2937] font-bold text-2xl font-mono">{selected.ext}</span>
                  <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold ${regConfig[selected.reg].cls}`}>
                    {regConfig[selected.reg].icon}
                    {selected.reg}
                  </span>
                </div>
                <p className="text-[#6B7280] text-xs">{selected.name}</p>
              </div>
              <button onClick={() => setSelected(null)} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-5 space-y-6">
              {/* Extension details */}
              <section>
                <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Extension Details</h4>
                <div className="space-y-2.5">
                  {[
                    { label: 'Assigned User', value: selected.user },
                    { label: 'Transport', value: selected.transport },
                    { label: 'Codec(s)', value: selected.codec },
                    { label: 'IP Address', value: selected.ip, mono: true },
                    { label: 'Call Status', value: selected.call },
                  ].map((row) => (
                    <div key={row.label} className="flex items-center justify-between gap-2">
                      <span className="text-[#9CA3AF] text-xs">{row.label}</span>
                      <span className={`text-[#1F2937] text-xs font-medium ${row.mono ? 'font-mono' : ''} text-right`}>{row.value}</span>
                    </div>
                  ))}

                  {/* Secret field */}
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[#9CA3AF] text-xs">Secret</span>
                    <div className="flex items-center gap-1.5">
                      <span className="text-[#1F2937] text-xs font-mono">{showSecret ? 'p@ssw0rd!42' : '••••••••••••'}</span>
                      <button onClick={() => setShowSecret(!showSecret)} className="text-[#9CA3AF] hover:text-[#374151] transition-colors">
                        {showSecret ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                      </button>
                    </div>
                  </div>
                </div>
              </section>

              {/* Registration history */}
              <section>
                <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Registration History</h4>
                <div className="space-y-2.5">
                  {regHistory.map((h, i) => (
                    <div key={i} className="flex items-start gap-2.5">
                      <div className={`w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 ${h.event === 'Registered' ? 'bg-[#DCFCE7]' : 'bg-[#FEE2E2]'}`}>
                        {h.event === 'Registered'
                          ? <Wifi className="w-3 h-3 text-[#22C55E]" />
                          : <WifiOff className="w-3 h-3 text-[#EF4444]" />}
                      </div>
                      <div>
                        <p className="text-[#374151] text-xs font-medium">{h.event}</p>
                        <p className="text-[#9CA3AF] text-[10px]">{h.time}</p>
                        <p className="text-[#9CA3AF] text-[10px]">{h.duration}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </section>

              {/* Assigned queue */}
              <section>
                <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Assigned Queue</h4>
                {selected.user !== 'Unassigned' ? (
                  <div className="flex items-center gap-2.5 p-3 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE]">
                    <div className="w-7 h-7 rounded-lg bg-[#2563EB] flex items-center justify-center flex-shrink-0">
                      <List className="w-3.5 h-3.5 text-white" />
                    </div>
                    <div>
                      <p className="text-[#2563EB] text-xs font-semibold">Support L1</p>
                      <p className="text-[#93C5FD] text-[10px]">Penalty: 0 · Priority: High</p>
                    </div>
                  </div>
                ) : (
                  <p className="text-[#9CA3AF] text-xs italic">No queue assigned</p>
                )}
              </section>

              {/* Actions */}
              <section className="space-y-2 pt-2">
                <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE] text-[#2563EB] text-xs font-semibold hover:bg-[#DBEAFE] transition-colors">
                  <Pencil className="w-3.5 h-3.5" /> Edit Extension
                </button>
                <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-semibold hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                  <RefreshCw className="w-3.5 h-3.5" /> Force Re-register
                </button>
                <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-semibold hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                  <PhoneCall className="w-3.5 h-3.5" /> Test Call
                </button>
              </section>
            </div>
          </div>
        )}
      </div>
    </TenantLayout>
  )
}
