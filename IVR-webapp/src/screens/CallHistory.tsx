import { useState, useEffect } from 'react'
import type { ReactElement } from 'react'
import TenantLayout from '../components/TenantLayout'
import { aiApi } from '../api/aiApi'
import {
  Search, Filter, Calendar, ChevronDown, Phone, Mic, Download,
  Eye, X, Play, Pause, Clock, ArrowRight, CheckCircle,
  XCircle, PhoneOff, PhoneMissed, Users, FileText,
} from 'lucide-react'

interface CallRecord {
  id: string; caller: string; queue: string; agent: string
  agentAvatar: string; agentColor: string
  date: string; time: string; duration: string
  status: 'Answered' | 'Abandoned' | 'Busy' | 'No Answer' | 'Transferred' | 'Failed'
  disposition: string; recording: boolean; waited: string
  real?: boolean
}

const CALLS: CallRecord[] = [
  { id: 'CH-10042', caller: '+1 (415) 882-3301', queue: 'Support L1', agent: 'Natalie R.', agentAvatar: 'NR', agentColor: 'from-[#2563EB] to-[#7C3AED]', date: 'Jul 18, 2026', time: '14:32', duration: '4m 12s', status: 'Answered', disposition: 'Resolved', recording: true, waited: '0m 42s' },
  { id: 'CH-10041', caller: '+1 (312) 445-9921', queue: 'Billing', agent: '—', agentAvatar: '?', agentColor: 'from-[#D1D5DB] to-[#9CA3AF]', date: 'Jul 18, 2026', time: '14:18', duration: '0m 38s', status: 'Abandoned', disposition: 'Abandoned in Queue', recording: false, waited: '1m 22s' },
  { id: 'CH-10040', caller: '+1 (617) 230-0084', queue: 'Sales', agent: 'James K.', agentAvatar: 'JK', agentColor: 'from-[#059669] to-[#0891B2]', date: 'Jul 18, 2026', time: '13:55', duration: '8m 37s', status: 'Transferred', disposition: 'Escalated to L2', recording: true, waited: '0m 18s' },
  { id: 'CH-10039', caller: '+1 (929) 551-7742', queue: 'General', agent: 'AI Bot', agentAvatar: '🤖', agentColor: 'from-[#8B5CF6] to-[#2563EB]', date: 'Jul 18, 2026', time: '13:40', duration: '2m 04s', status: 'Answered', disposition: 'Self-Service (AI)', recording: true, waited: '0m 05s' },
  { id: 'CH-10038', caller: '+1 (503) 770-4423', queue: 'Support L1', agent: 'Priya N.', agentAvatar: 'PN', agentColor: 'from-[#D97706] to-[#DC2626]', date: 'Jul 18, 2026', time: '13:22', duration: '6m 51s', status: 'Answered', disposition: 'Resolved', recording: true, waited: '2m 11s' },
  { id: 'CH-10037', caller: '+1 (214) 380-1190', queue: 'Technical', agent: '—', agentAvatar: '?', agentColor: 'from-[#D1D5DB] to-[#9CA3AF]', date: 'Jul 17, 2026', time: '17:08', duration: '0m 12s', status: 'No Answer', disposition: 'No Agents Available', recording: false, waited: '5m 00s' },
  { id: 'CH-10036', caller: '+1 (832) 220-5541', queue: 'Billing', agent: 'Tom B.', agentAvatar: 'TB', agentColor: 'from-[#7C3AED] to-[#DB2777]', date: 'Jul 17, 2026', time: '16:45', duration: '3m 28s', status: 'Answered', disposition: 'Payment Processed', recording: true, waited: '0m 55s' },
  { id: 'CH-10035', caller: '+1 (646) 113-9902', queue: 'Sales', agent: 'Sofia A.', agentAvatar: 'SA', agentColor: 'from-[#0284C7] to-[#059669]', date: 'Jul 17, 2026', time: '15:30', duration: '12m 04s', status: 'Answered', disposition: 'Demo Booked', recording: true, waited: '0m 08s' },
]

const statusStyle: Record<string, { cls: string; icon: ReactElement }> = {
  Answered: { cls: 'bg-[#DCFCE7] text-[#15803D]', icon: <CheckCircle className="w-3 h-3" /> },
  Abandoned: { cls: 'bg-[#FEF2F2] text-[#DC2626]', icon: <PhoneMissed className="w-3 h-3" /> },
  'No Answer': { cls: 'bg-[#FEF9C3] text-[#A16207]', icon: <PhoneOff className="w-3 h-3" /> },
  Transferred: { cls: 'bg-[#EFF6FF] text-[#2563EB]', icon: <ArrowRight className="w-3 h-3" /> },
  Busy: { cls: 'bg-[#F5F3FF] text-[#7C3AED]', icon: <XCircle className="w-3 h-3" /> },
  Failed: { cls: 'bg-[#FEF2F2] text-[#DC2626]', icon: <XCircle className="w-3 h-3" /> },
}

function formatCallDate(iso: string): { date: string; time: string } {
  const d = new Date(iso.replace(' ', 'T') + 'Z')
  return {
    date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }),
    time: d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false }),
  }
}

function formatCallDuration(sec: number): string {
  const s = Math.round(sec || 0)
  if (s <= 0) return '0s'
  const m = Math.floor(s / 60)
  const r = s % 60
  return m > 0 ? `${m}m ${r.toString().padStart(2, '0')}s` : `${r}s`
}

function cdrToCall(c: { uniqueId: string; caller: string; callee: string; start: string; durationSec: number; disposition: string; status: string }): CallRecord {
  const { date, time } = formatCallDate(c.start || new Date().toISOString())
  return {
    id: `CH-${(c.uniqueId || '')}`,
    caller: c.caller || 'Unknown',
    queue: `Ext ${c.callee || '—'}`,
    agent: 'IVR',
    agentAvatar: 'IVR',
    agentColor: 'from-[#2563EB] to-[#7C3AED]',
    date,
    time,
    duration: formatCallDuration(c.durationSec),
    status: (['Answered', 'No Answer', 'Busy', 'Failed'].includes(c.status) ? c.status : 'No Answer') as CallRecord['status'],
    disposition: c.disposition || c.status,
    recording: false,
    waited: '—',
    real: true,
  }
}

const IVR_PATH = ['Greeting', 'Main Menu', 'Support Branch', 'Queue: Support L1', 'Agent Transfer', 'Survey', 'Hangup']
const DTMF_INPUTS = [{ time: '00:08', key: '2', node: 'Main Menu' }, { time: '00:22', key: '1', node: 'Support Branch' }, { time: '01:45', key: '5', node: 'Survey' }]
const TIMELINE = [
  { time: '14:32:00', event: 'Call Connected', icon: <Phone className="w-3 h-3" />, color: '#22C55E' },
  { time: '14:32:08', event: 'Greeting Played', icon: <Play className="w-3 h-3" />, color: '#2563EB' },
  { time: '14:32:16', event: 'DTMF "2" Received → Support', icon: <CheckCircle className="w-3 h-3" />, color: '#2563EB' },
  { time: '14:32:54', event: 'Entered Queue: Support L1', icon: <Users className="w-3 h-3" />, color: '#F59E0B' },
  { time: '14:33:36', event: 'Agent Natalie R. Connected', icon: <CheckCircle className="w-3 h-3" />, color: '#22C55E' },
  { time: '14:37:44', event: 'Survey Prompt', icon: <FileText className="w-3 h-3" />, color: '#8B5CF6' },
  { time: '14:38:01', event: 'Call Ended', icon: <PhoneOff className="w-3 h-3" />, color: '#EF4444' },
]

function DrawerSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h5 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">{title}</h5>
      {children}
    </div>
  )
}

export default function CallHistory({ onLogout }: { onLogout: () => void }) {
  const [selectedCall, setSelectedCall] = useState<CallRecord | null>(null)
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(34)
  const [aiSummary, setAiSummary] = useState<string>('')
  const [calls, setCalls] = useState<CallRecord[]>(CALLS)
  const [totalCalls, setTotalCalls] = useState<number>(8342)

  useEffect(() => {
    let cancelled = false
    aiApi.fetchCdrCalls()
      .then((cdr) => {
        if (cancelled) return
        if (Array.isArray(cdr) && cdr.length > 0) {
          setCalls(cdr.map(cdrToCall))
          setTotalCalls(cdr.length)
        }
      })
      .catch(() => { /* keep mock data when CDR is unavailable */ })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (selectedCall) {
      aiApi.summarizeConversation([{ content: `Call with ${selectedCall.caller} on ${selectedCall.queue}` }])
        .then(res => setAiSummary(res.summary))
        .catch(() => setAiSummary('Customer called regarding ' + selectedCall.queue + '. Call resolved cleanly.'))
    }
  }, [selectedCall])

  const headerActions = (
    <div className="flex gap-2">
      <button className="flex items-center gap-2 px-3.5 py-2 rounded-lg border border-[#E5E7EB] bg-white text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Download className="w-4 h-4" /> Export CSV
      </button>
    </div>
  )

  return (
    <TenantLayout activeNav="history" onLogout={onLogout}
      pageTitle="Call History" pageSubtitle={`${totalCalls.toLocaleString()} calls recorded`}
      headerActions={headerActions}>
      <div className="space-y-4">
        {/* Filter bar */}
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm px-4 py-3">
          <div className="flex items-center gap-3 flex-wrap">
            <div className="flex items-center gap-2 flex-1 min-w-[180px] bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-3 py-2">
              <Search className="w-4 h-4 text-[#9CA3AF]" />
              <input className="bg-transparent text-sm text-[#1F2937] placeholder:text-[#9CA3AF] outline-none flex-1" placeholder="Search caller number or ID..." />
            </div>
            {[
              { label: 'Date Range', icon: <Calendar className="w-3.5 h-3.5" /> },
              { label: 'Queue', icon: <Users className="w-3.5 h-3.5" /> },
              { label: 'Agent', icon: null },
              { label: 'Status', icon: null },
              { label: 'Disposition', icon: null },
            ].map(f => (
              <button key={f.label} className="flex items-center gap-1.5 px-3 py-2 bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg text-[#374151] text-sm hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                {f.icon}{f.label}<ChevronDown className="w-3.5 h-3.5 text-[#9CA3AF]" />
              </button>
            ))}
            <button className="flex items-center gap-1.5 px-3 py-2 bg-[#2563EB] text-white rounded-lg text-sm font-medium hover:bg-[#1D4ED8] transition-colors shadow-sm">
              <Filter className="w-3.5 h-3.5" /> Apply
            </button>
          </div>
        </div>

        {/* Main layout */}
        <div className={`flex gap-4 transition-all ${selectedCall ? '' : ''}`}>
          {/* Table */}
          <div className={`flex-1 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden transition-all ${selectedCall ? 'min-w-0' : ''}`}>
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                  {['ID', 'Caller', 'Queue', 'Agent', 'Date/Time', 'Duration', 'Waited', 'Status', 'Disposition', 'Rec.', ''].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {calls.map(call => {
                  const ss = statusStyle[call.status]
                  const isSelected = selectedCall?.id === call.id
                  return (
                    <tr key={call.id}
                      onClick={() => setSelectedCall(isSelected ? null : call)}
                      className={`cursor-pointer transition-colors group ${isSelected ? 'bg-[#EFF6FF]' : 'hover:bg-[#F9FAFB]'}`}>
                      <td className="px-4 py-3">
                        <span className="text-[#2563EB] text-xs font-mono font-medium">{call.id}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-[#1F2937] font-mono text-xs">{call.caller}</span>
                      </td>
                      <td className="px-4 py-3"><span className="text-[#374151] text-xs">{call.queue}</span></td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          <div className={`w-5 h-5 rounded-full bg-gradient-to-br ${call.agentColor} flex items-center justify-center text-white text-[8px] font-bold flex-shrink-0`}>
                            {call.agentAvatar}
                          </div>
                          <span className="text-[#374151] text-xs">{call.agent}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="text-[#374151] text-xs">{call.date}</div>
                        <div className="text-[#9CA3AF] text-[10px]">{call.time}</div>
                      </td>
                      <td className="px-4 py-3"><span className="text-[#374151] text-xs font-mono">{call.duration}</span></td>
                      <td className="px-4 py-3"><span className="text-[#9CA3AF] text-xs font-mono">{call.waited}</span></td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${ss.cls}`}>
                          {ss.icon}{call.status}
                        </span>
                      </td>
                      <td className="px-4 py-3"><span className="text-[#6B7280] text-xs">{call.disposition}</span></td>
                      <td className="px-4 py-3">
                        {call.recording
                          ? <button className="w-6 h-6 rounded-md bg-[#EFF6FF] text-[#2563EB] flex items-center justify-center hover:bg-[#DBEAFE] transition-colors"><Mic className="w-3 h-3" /></button>
                          : <span className="text-[#D1D5DB] text-xs">—</span>}
                      </td>
                      <td className="px-4 py-3">
                        <button className="opacity-0 group-hover:opacity-100 w-6 h-6 rounded-md bg-[#F3F4F6] text-[#6B7280] flex items-center justify-center hover:bg-[#E5E7EB] transition-all" onClick={e => { e.stopPropagation(); setSelectedCall(call) }}>
                          <Eye className="w-3 h-3" />
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <div className="px-4 py-3 border-t border-[#F3F4F6] flex items-center justify-between">
              <span className="text-[#9CA3AF] text-xs">Showing {calls.length} of {totalCalls.toLocaleString()} calls</span>
              <div className="flex gap-1">
                {['Previous', '1', '2', '3', '...', '834', 'Next'].map((p, i) => (
                  <button key={i} className={`px-2.5 py-1 rounded-md text-xs ${p === '1' ? 'bg-[#2563EB] text-white' : 'text-[#374151] hover:bg-[#F3F4F6]'} transition-colors`}>{p}</button>
                ))}
              </div>
            </div>
          </div>

          {/* Details drawer */}
          {selectedCall && (
            <div className="w-80 flex-shrink-0 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col">
              <div className="px-4 py-3 border-b border-[#F3F4F6] flex items-center justify-between">
                <div>
                  <p className="text-[#1F2937] font-semibold text-sm">{selectedCall.caller}</p>
                  <p className="text-[#9CA3AF] text-xs">{selectedCall.id} · {selectedCall.date} {selectedCall.time}</p>
                </div>
                <button onClick={() => setSelectedCall(null)} className="w-7 h-7 rounded-lg hover:bg-[#F3F4F6] flex items-center justify-center transition-colors">
                  <X className="w-4 h-4 text-[#9CA3AF]" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto p-4 space-y-5">
                {/* Recording player */}
                {selectedCall.recording && (
                  <div className="bg-[#F9FAFB] rounded-xl p-3">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs text-[#374151] font-medium">Recording</span>
                      <button className="text-xs text-[#2563EB] flex items-center gap-1 hover:underline"><Download className="w-3 h-3" />Download</button>
                    </div>
                    <div className="flex items-center gap-2 mb-2">
                      <button onClick={() => setPlaying(!playing)} className="w-7 h-7 rounded-full bg-[#2563EB] text-white flex items-center justify-center hover:bg-[#1D4ED8] transition-colors">
                        {playing ? <Pause className="w-3 h-3" /> : <Play className="w-3 h-3" />}
                      </button>
                      <div className="flex-1 h-1 bg-[#E5E7EB] rounded-full relative cursor-pointer" onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); setProgress(Math.round((e.clientX - r.left) / r.width * 100)) }}>
                        <div className="h-full bg-[#2563EB] rounded-full" style={{ width: `${progress}%` }} />
                        <div className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-[#2563EB] border-2 border-white shadow" style={{ left: `calc(${progress}% - 6px)` }} />
                      </div>
                      <span className="text-[#9CA3AF] font-mono text-[10px]">4:12</span>
                    </div>
                    <div className="flex gap-[2px]">
                      {Array.from({ length: 48 }, (_, i) => (
                        <div key={i} className="w-1 rounded-sm" style={{ height: `${12 + Math.abs(Math.sin(i * 0.6)) * 18}px`, backgroundColor: i / 48 * 100 <= progress ? '#2563EB' : '#E5E7EB' }} />
                      ))}
                    </div>
                  </div>
                )}

                {aiSummary && (
                  <DrawerSection title="AI Summary">
                    <p className="text-[#374151] text-xs leading-relaxed bg-[#F9FAFB] p-2.5 rounded-lg border border-[#E5E7EB]">{aiSummary}</p>
                  </DrawerSection>
                )}

                {!selectedCall.real && (
                  <>
                    <DrawerSection title="Timeline">
                      <div className="relative">
                        <div className="absolute left-[11px] top-0 bottom-0 w-px bg-[#E5E7EB]" />
                        <div className="space-y-3">
                          {TIMELINE.map((e, i) => (
                            <div key={i} className="flex gap-3">
                              <div className="w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 relative z-10" style={{ backgroundColor: `${e.color}20`, color: e.color }}>{e.icon}</div>
                              <div>
                                <p className="text-[#1F2937] text-xs">{e.event}</p>
                                <p className="text-[#9CA3AF] text-[10px] font-mono">{e.time}</p>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </DrawerSection>

                    <DrawerSection title="IVR Path">
                      <div className="flex items-center flex-wrap gap-1">
                        {IVR_PATH.map((node, i) => (
                          <div key={i} className="flex items-center gap-1">
                            <span className="inline-flex px-2 py-0.5 rounded-md bg-[#EFF6FF] text-[#2563EB] text-[10px] font-medium">{node}</span>
                            {i < IVR_PATH.length - 1 && <ArrowRight className="w-3 h-3 text-[#D1D5DB]" />}
                          </div>
                        ))}
                      </div>
                    </DrawerSection>

                    <DrawerSection title="DTMF Inputs">
                      <div className="space-y-1.5">
                        {DTMF_INPUTS.map((d, i) => (
                          <div key={i} className="flex items-center gap-2 p-2 bg-[#F9FAFB] rounded-lg">
                            <span className="w-6 h-6 rounded-full bg-[#2563EB] text-white text-xs font-bold flex items-center justify-center flex-shrink-0">{d.key}</span>
                            <div>
                              <p className="text-[#374151] text-xs">{d.node}</p>
                              <p className="text-[#9CA3AF] text-[10px] font-mono">at {d.time}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    </DrawerSection>
                  </>
                )}

                <DrawerSection title="Queue Wait Time">
                  <div className="flex items-center gap-3 p-2 bg-[#FEF9C3] rounded-lg">
                    <Clock className="w-4 h-4 text-[#A16207]" />
                    <div>
                      <p className="text-[#92400E] text-xs font-semibold">{selectedCall.waited}</p>
                      <p className="text-[#A16207] text-[10px]">in {selectedCall.queue}</p>
                    </div>
                  </div>
                </DrawerSection>

                <DrawerSection title="Agent Notes">
                  <textarea rows={3} defaultValue="Customer called about billing discrepancy on June invoice. Resolved — applied $12 credit. Follow up in 5 business days." className="w-full p-2.5 rounded-lg border border-[#E5E7EB] text-xs text-[#374151] resize-none outline-none focus:border-[#2563EB] transition-colors bg-[#F9FAFB]" />
                  <button className="mt-1.5 text-xs text-[#2563EB] hover:underline">Save note</button>
                </DrawerSection>
              </div>
            </div>
          )}
        </div>
      </div>
    </TenantLayout>
  )
}
