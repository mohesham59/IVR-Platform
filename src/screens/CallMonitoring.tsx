import { useState, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  PhoneCall, Clock, Headphones, Users, Mic, MicOff,
  Phone, PhoneOff, Eye, MessageSquare,
  Pause, WifiOff, CheckCircle, Radio, AlertCircle,
  RefreshCw,
} from 'lucide-react'

const QUEUES = [
  { name: 'Support L1', waiting: 3, available: 2, busy: 3, longest: '3m 42s', color: '#2563EB' },
  { name: 'Billing', waiting: 2, available: 1, busy: 2, longest: '5m 12s', color: '#F59E0B' },
  { name: 'Sales', waiting: 1, available: 2, busy: 1, longest: '1m 08s', color: '#22C55E' },
  { name: 'Technical', waiting: 0, available: 1, busy: 1, longest: '—', color: '#8B5CF6' },
  { name: 'General', waiting: 5, available: 0, busy: 2, longest: '8m 31s', color: '#EC4899' },
]

interface LiveCall {
  id: string; caller: string; queue: string; node: string
  agent: string; agentColor: string; agentAvatar: string
  duration: string; secs: number; recording: boolean; status: 'Active' | 'On Hold' | 'Ringing'
}

const LIVE_CALLS: LiveCall[] = [
  { id: 'c1', caller: '+1 (415) 882-3301', queue: 'Support L1', node: 'Queue — Waiting', agent: 'Natalie R.', agentColor: 'from-[#2563EB] to-[#7C3AED]', agentAvatar: 'NR', duration: '4:12', secs: 252, recording: true, status: 'Active' },
  { id: 'c2', caller: '+1 (312) 445-9921', queue: 'Billing', node: 'DTMF Menu', agent: '—', agentColor: 'from-[#9CA3AF] to-[#6B7280]', agentAvatar: '?', duration: '1:04', secs: 64, recording: false, status: 'Ringing' },
  { id: 'c3', caller: '+1 (617) 230-0084', queue: 'Sales', node: 'Agent Transfer', agent: 'James K.', agentColor: 'from-[#059669] to-[#0891B2]', agentAvatar: 'JK', duration: '8:37', secs: 517, recording: true, status: 'Active' },
  { id: 'c4', caller: '+1 (929) 551-7742', queue: 'General', node: 'AI Assistant', agent: 'AI Bot', agentColor: 'from-[#8B5CF6] to-[#2563EB]', agentAvatar: '🤖', duration: '2:18', secs: 138, recording: true, status: 'Active' },
  { id: 'c5', caller: '+1 (503) 770-4423', queue: 'Support L1', node: 'Queue — Waiting', agent: 'Priya N.', agentColor: 'from-[#D97706] to-[#DC2626]', agentAvatar: 'PN', duration: '0:42', secs: 42, recording: false, status: 'On Hold' },
  { id: 'c6', caller: '+1 (214) 380-1190', queue: 'Billing', node: 'Greeting', agent: '—', agentColor: 'from-[#9CA3AF] to-[#6B7280]', agentAvatar: '?', duration: '0:08', secs: 8, recording: false, status: 'Ringing' },
]

const AGENTS = [
  { name: 'Natalie Rodriguez', ext: '1001', avatar: 'NR', color: 'from-[#2563EB] to-[#7C3AED]', state: 'Busy' as const, call: '+1 (415) 882-3301', queue: 'Support L1' },
  { name: 'James Kowalski', ext: '1002', avatar: 'JK', color: 'from-[#059669] to-[#0891B2]', state: 'Busy' as const, call: '+1 (617) 230-0084', queue: 'Sales' },
  { name: 'Priya Nair', ext: '1003', avatar: 'PN', color: 'from-[#D97706] to-[#DC2626]', state: 'Busy' as const, call: '+1 (503) 770-4423', queue: 'Support L1' },
  { name: 'Tom Brecker', ext: '1004', avatar: 'TB', color: 'from-[#7C3AED] to-[#DB2777]', state: 'Paused' as const, call: '—', queue: '—' },
  { name: 'Sofia Alvarez', ext: '1005', avatar: 'SA', color: 'from-[#0284C7] to-[#059669]', state: 'Online' as const, call: '—', queue: 'Sales' },
  { name: 'Darius Okafor', ext: '1006', avatar: 'DO', color: 'from-[#BE185D] to-[#7C3AED]', state: 'Offline' as const, call: '—', queue: '—' },
  { name: 'Lea Fontaine', ext: '1007', avatar: 'LF', color: 'from-[#1E40AF] to-[#0891B2]', state: 'Online' as const, call: '—', queue: 'General' },
]

const agentStateConfig = {
  Online: { cls: 'text-[#22C55E]', dot: 'bg-[#22C55E]', icon: <CheckCircle className="w-3.5 h-3.5" />, label: 'Available' },
  Busy: { cls: 'text-[#2563EB]', dot: 'bg-[#2563EB]', icon: <PhoneCall className="w-3.5 h-3.5" />, label: 'In Call' },
  Paused: { cls: 'text-[#F59E0B]', dot: 'bg-[#F59E0B] animate-pulse', icon: <Pause className="w-3.5 h-3.5" />, label: 'Paused' },
  Offline: { cls: 'text-[#9CA3AF]', dot: 'bg-[#D1D5DB]', icon: <WifiOff className="w-3.5 h-3.5" />, label: 'Offline' },
}

const callStatusStyle = {
  Active: 'bg-[#DCFCE7] text-[#15803D]',
  'On Hold': 'bg-[#FEF9C3] text-[#A16207]',
  Ringing: 'bg-[#EFF6FF] text-[#2563EB]',
}

function DurationTimer({ secs }: { secs: number }) {
  const [s, setS] = useState(secs)
  useEffect(() => {
    const id = setInterval(() => setS(v => v + 1), 1000)
    return () => clearInterval(id)
  }, [])
  const m = Math.floor(s / 60), sec = s % 60
  return <span className="font-mono text-xs text-[#374151]">{m}:{sec.toString().padStart(2, '0')}</span>
}

export default function CallMonitoring({ onLogout }: { onLogout: () => void }) {
  const [refreshing, setRefreshing] = useState(false)

  const liveCalls = LIVE_CALLS.length
  const queued = QUEUES.reduce((a, q) => a + q.waiting, 0)
  const available = AGENTS.filter(a => a.state === 'Online').length
  const busy = AGENTS.filter(a => a.state === 'Busy').length

  const handleRefresh = () => {
    setRefreshing(true)
    setTimeout(() => setRefreshing(false), 800)
  }

  const headerActions = (
    <button onClick={handleRefresh}
      className={`flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm ${refreshing ? 'text-[#2563EB] border-[#2563EB]' : ''}`}>
      <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} /> Refresh
    </button>
  )

  return (
    <TenantLayout activeNav="monitoring" onLogout={onLogout}
      pageTitle="Live Call Monitoring" pageSubtitle="Real-time view — updates every 5 seconds"
      headerActions={headerActions} liveCount={liveCalls}>
      <div className="space-y-4">
        {/* KPI cards */}
        <div className="grid grid-cols-6 gap-3">
          {[
            { label: 'Live Calls', value: liveCalls, icon: <Radio className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF', pulse: true },
            { label: 'Queued', value: queued, icon: <Clock className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB', pulse: false },
            { label: 'Available', value: available, icon: <Headphones className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4', pulse: false },
            { label: 'Busy Agents', value: busy, icon: <PhoneCall className="w-5 h-5" />, color: '#6366F1', bg: '#EEF2FF', pulse: false },
            { label: 'Avg Wait', value: '2m 14s', icon: <Clock className="w-5 h-5" />, color: '#EC4899', bg: '#FDF2F8', pulse: false },
            { label: 'Avg Handle', value: '4m 38s', icon: <Users className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF', pulse: false },
          ].map(k => (
            <div key={k.label} className="bg-white rounded-xl border border-[#E5E7EB] p-3.5 shadow-sm hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-2.5">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ backgroundColor: k.bg, color: k.color }}>{k.icon}</div>
                {k.pulse && <span className="w-2 h-2 rounded-full bg-[#22C55E] animate-pulse" />}
              </div>
              <div className="text-[#1F2937] font-bold text-xl leading-none">{k.value}</div>
              <div className="text-[#9CA3AF] text-[10px] mt-1">{k.label}</div>
            </div>
          ))}
        </div>

        {/* Main grid */}
        <div className="grid grid-cols-3 gap-4">
          {/* Live calls table — spans 2 cols */}
          <div className="col-span-2 space-y-4">
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#22C55E] animate-pulse" />
                  <h3 className="text-[#1F2937] font-semibold text-sm">Live Calls</h3>
                  <span className="bg-[#EFF6FF] text-[#2563EB] text-[10px] font-bold px-1.5 py-0.5 rounded-full">{liveCalls}</span>
                </div>
                <div className="flex gap-2">
                  <span className="text-[#9CA3AF] text-xs">Auto-refresh: 5s</span>
                </div>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                    {['Caller', 'Queue', 'Current Node', 'Agent', 'Duration', 'Rec.', 'Status', ''].map(h => (
                      <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#F3F4F6]">
                  {LIVE_CALLS.map(call => (
                    <tr key={call.id} className="hover:bg-[#F9FAFB] transition-colors group">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-6 h-6 rounded-full bg-[#EFF6FF] flex items-center justify-center flex-shrink-0">
                            <Phone className="w-3 h-3 text-[#2563EB]" />
                          </div>
                          <span className="text-[#1F2937] font-mono text-xs">{call.caller}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-[#374151] text-xs">{call.queue}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex px-2 py-0.5 rounded-md bg-[#F3F4F6] text-[#374151] text-[10px] font-medium">{call.node}</span>
                      </td>
                      <td className="px-4 py-3">
                        {call.agent !== '—' ? (
                          <div className="flex items-center gap-1.5">
                            <div className={`w-5 h-5 rounded-full bg-gradient-to-br ${call.agentColor} flex items-center justify-center text-white text-[9px] font-bold flex-shrink-0`}>
                              {call.agentAvatar}
                            </div>
                            <span className="text-[#374151] text-xs">{call.agent}</span>
                          </div>
                        ) : (
                          <span className="text-[#9CA3AF] text-xs">IVR</span>
                        )}
                      </td>
                      <td className="px-4 py-3"><DurationTimer secs={call.secs} /></td>
                      <td className="px-4 py-3">
                        {call.recording
                          ? <span className="flex items-center gap-1 text-[#EF4444] text-[10px] font-medium"><Mic className="w-3 h-3" />REC</span>
                          : <MicOff className="w-3.5 h-3.5 text-[#D1D5DB]" />}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${callStatusStyle[call.status]}`}>
                          {call.status === 'Active' && <span className="w-1.5 h-1.5 rounded-full bg-[#22C55E] animate-pulse" />}
                          {call.status}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button className="w-6 h-6 rounded-md flex items-center justify-center bg-[#EFF6FF] text-[#2563EB] hover:bg-[#DBEAFE] transition-colors" title="Monitor">
                            <Eye className="w-3 h-3" />
                          </button>
                          <button className="w-6 h-6 rounded-md flex items-center justify-center bg-[#F3F4F6] text-[#6B7280] hover:bg-[#E5E7EB] transition-colors" title="Whisper">
                            <MessageSquare className="w-3 h-3" />
                          </button>
                          <button className="w-6 h-6 rounded-md flex items-center justify-center bg-[#FEF2F2] text-[#EF4444] hover:bg-[#FEE2E2] transition-colors" title="End Call">
                            <PhoneOff className="w-3 h-3" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Queue status grid */}
            <div className="grid grid-cols-3 gap-3">
              {QUEUES.map(q => (
                <div key={q.name} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow">
                  <div className="flex items-center gap-2 mb-3">
                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: q.color }} />
                    <p className="text-[#1F2937] font-semibold text-sm">{q.name}</p>
                  </div>
                  <div className="grid grid-cols-3 gap-2 mb-3">
                    {[
                      { label: 'Wait', value: q.waiting, color: q.waiting > 0 ? '#EF4444' : '#22C55E' },
                      { label: 'Avail', value: q.available, color: '#22C55E' },
                      { label: 'Busy', value: q.busy, color: '#2563EB' },
                    ].map(s => (
                      <div key={s.label} className="text-center p-1.5 rounded-lg bg-[#F9FAFB]">
                        <p className="font-bold text-sm" style={{ color: s.color }}>{s.value}</p>
                        <p className="text-[#9CA3AF] text-[9px]">{s.label}</p>
                      </div>
                    ))}
                  </div>
                  {q.waiting > 0 && (
                    <div className="flex items-center gap-1 text-[10px] text-[#EF4444]">
                      <AlertCircle className="w-3 h-3" />
                      <span>Longest: {q.longest}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Right: Agent panel */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col">
            <div className="px-4 py-4 border-b border-[#F3F4F6]">
              <h3 className="text-[#1F2937] font-semibold text-sm">Agent Status</h3>
              <p className="text-[#9CA3AF] text-xs mt-0.5">{AGENTS.length} agents · {busy} in call</p>
            </div>
            <div className="flex-1 overflow-y-auto divide-y divide-[#F3F4F6]">
              {AGENTS.map(agent => {
                const sc = agentStateConfig[agent.state]
                return (
                  <div key={agent.ext} className="flex items-center gap-3 px-4 py-3 hover:bg-[#F9FAFB] transition-colors">
                    <div className="relative">
                      <div className={`w-8 h-8 rounded-full bg-gradient-to-br ${agent.color} flex items-center justify-center text-white text-xs font-bold flex-shrink-0`}>
                        {agent.avatar}
                      </div>
                      <span className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-white ${sc.dot}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-[#1F2937] text-xs font-medium truncate">{agent.name}</p>
                      <p className="text-[#9CA3AF] text-[10px]">Ext. {agent.ext} · {agent.queue}</p>
                      {agent.state === 'Busy' && (
                        <p className="text-[#2563EB] text-[10px] font-mono truncate">{agent.call}</p>
                      )}
                    </div>
                    <div className="flex items-center gap-1 flex-shrink-0">
                      <span className={sc.cls}>{sc.icon}</span>
                    </div>
                  </div>
                )
              })}
            </div>

            {/* Summary bar */}
            <div className="px-4 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB] grid grid-cols-4 gap-2">
              {[
                { label: 'Online', count: AGENTS.filter(a => a.state === 'Online').length, color: '#22C55E' },
                { label: 'Busy', count: busy, color: '#2563EB' },
                { label: 'Paused', count: AGENTS.filter(a => a.state === 'Paused').length, color: '#F59E0B' },
                { label: 'Off', count: AGENTS.filter(a => a.state === 'Offline').length, color: '#D1D5DB' },
              ].map(s => (
                <div key={s.label} className="text-center">
                  <p className="font-bold text-sm" style={{ color: s.color }}>{s.count}</p>
                  <p className="text-[#9CA3AF] text-[9px]">{s.label}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </TenantLayout>
  )
}
