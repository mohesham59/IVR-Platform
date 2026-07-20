import { useState } from 'react'
import type { ReactElement } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Plus, Search, MoreHorizontal, X, List,
  Pencil, Trash2, ChevronLeft, ChevronRight, Settings,
  Clock, Users, PhoneCall,
  Headphones, GripVertical,
  AlertCircle, Pause, Activity, WifiOff,
  CheckCircle,
} from 'lucide-react'

interface Agent {
  id: number
  name: string
  ext: string
  avatar: string
  color: string
  state: 'Online' | 'Busy' | 'Paused' | 'Offline'
  calls: number
}

const allAgents: Agent[] = [
  { id: 1, name: 'Natalie Rodriguez', ext: '1001', avatar: 'NR', color: 'from-[#2563EB] to-[#7C3AED]', state: 'Busy', calls: 48 },
  { id: 2, name: 'James Kowalski', ext: '1002', avatar: 'JK', color: 'from-[#059669] to-[#0891B2]', state: 'Busy', calls: 42 },
  { id: 3, name: 'Priya Nair', ext: '1003', avatar: 'PN', color: 'from-[#D97706] to-[#DC2626]', state: 'Online', calls: 55 },
  { id: 4, name: 'Tom Brecker', ext: '1004', avatar: 'TB', color: 'from-[#7C3AED] to-[#DB2777]', state: 'Paused', calls: 36 },
  { id: 5, name: 'Sofia Alvarez', ext: '1005', avatar: 'SA', color: 'from-[#0284C7] to-[#059669]', state: 'Online', calls: 50 },
  { id: 6, name: 'Darius Okafor', ext: '1006', avatar: 'DO', color: 'from-[#BE185D] to-[#7C3AED]', state: 'Offline', calls: 0 },
  { id: 7, name: 'Lea Fontaine', ext: '1007', avatar: 'LF', color: 'from-[#1E40AF] to-[#0891B2]', state: 'Online', calls: 28 },
]

const queues = [
  { id: 1, name: 'Support L1', strategy: 'Round Robin', members: 4, current: 3, avgWait: '1m 24s', status: 'Active', agentIds: [1, 2, 3, 5] },
  { id: 2, name: 'Support L2', strategy: 'Least Recent', members: 2, current: 0, avgWait: '—', status: 'Active', agentIds: [3, 7] },
  { id: 3, name: 'Sales', strategy: 'Ring All', members: 3, current: 1, avgWait: '0m 48s', status: 'Active', agentIds: [2, 5, 7] },
  { id: 4, name: 'Billing', strategy: 'Round Robin', members: 2, current: 2, avgWait: '3m 12s', status: 'Active', agentIds: [1, 4] },
  { id: 5, name: 'VIP', strategy: 'Linear', members: 2, current: 0, avgWait: '—', status: 'Active', agentIds: [5, 7] },
  { id: 6, name: 'After-Hours', strategy: 'Round Robin', members: 0, current: 0, avgWait: '—', status: 'Inactive', agentIds: [] },
]

const agentStateConfig: Record<string, { cls: string; dot: string; icon: ReactElement; label: string }> = {
  Online: { cls: 'text-[#22C55E]', dot: 'bg-[#22C55E]', icon: <CheckCircle className="w-3 h-3" />, label: 'Available' },
  Busy: { cls: 'text-[#2563EB]', dot: 'bg-[#2563EB]', icon: <PhoneCall className="w-3 h-3" />, label: 'In Call' },
  Paused: { cls: 'text-[#F59E0B]', dot: 'bg-[#F59E0B] animate-pulse', icon: <Pause className="w-3 h-3" />, label: 'Paused' },
  Offline: { cls: 'text-[#9CA3AF]', dot: 'bg-[#D1D5DB]', icon: <WifiOff className="w-3 h-3" />, label: 'Offline' },
}

const strategyColor: Record<string, string> = {
  'Round Robin': 'bg-[#EFF6FF] text-[#2563EB] border-[#BFDBFE]',
  'Least Recent': 'bg-[#F0FDF4] text-[#15803D] border-[#BBF7D0]',
  'Ring All': 'bg-[#FDF4FF] text-[#7E22CE] border-[#E9D5FF]',
  Linear: 'bg-[#FFFBEB] text-[#A16207] border-[#FDE68A]',
}


function QueueActionMenu({ onClose }: { onClose: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1">
      {[
        { icon: <Settings className="w-3.5 h-3.5" />, label: 'Configure', color: '' },
        { icon: <Users className="w-3.5 h-3.5" />, label: 'Manage Members', color: '' },
        { icon: <Activity className="w-3.5 h-3.5" />, label: 'View Analytics', color: '' },
        { icon: <Pencil className="w-3.5 h-3.5" />, label: 'Edit Queue', color: '' },
        { icon: <Trash2 className="w-3.5 h-3.5" />, label: 'Delete Queue', color: 'text-[#EF4444]' },
      ].map((item) => (
        <button key={item.label} onClick={onClose} className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${item.color || 'text-[#374151]'}`}>
          {item.icon}{item.label}
        </button>
      ))}
    </div>
  )
}

export default function QueueManagement({ onLogout }: { onLogout: () => void }) {
  const [selectedQueue, setSelectedQueue] = useState<typeof queues[0] | null>(queues[0])
  const [openMenu, setOpenMenu] = useState<number | null>(null)
  const [activeTab, setActiveTab] = useState('details')

  const totalWaiting = queues.reduce((s, q) => s + q.current, 0)
  const availableAgents = allAgents.filter(a => a.state === 'Online').length
  const busyAgents = allAgents.filter(a => a.state === 'Busy').length

  const headerActions = (
    <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
      <Plus className="w-4 h-4" />
      Create Queue
    </button>
  )

  const queueAgents = selectedQueue
    ? allAgents.filter(a => selectedQueue.agentIds.includes(a.id))
    : []

  return (
    <TenantLayout
      activeNav="queues"
      onLogout={onLogout}
      pageTitle="Queue Management"
      pageSubtitle="Manage Asterisk call queues and agent assignments"
      headerActions={headerActions}
    >
      <div className="space-y-4">
        {/* Dashboard cards */}
        <div className="grid grid-cols-4 gap-4">
          {[
            { label: 'Total Queues', value: queues.length, icon: <List className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF', sub: `${queues.filter(q => q.status === 'Active').length} active` },
            { label: 'Waiting Calls', value: totalWaiting, icon: <Clock className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB', sub: 'Across all queues' },
            { label: 'Available Agents', value: availableAgents, icon: <Headphones className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4', sub: `${busyAgents} in call` },
            { label: 'Avg Wait Time', value: '1m 52s', icon: <Activity className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF', sub: 'Platform average' },
          ].map((s) => (
            <div key={s.label} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow">
              <div className="w-9 h-9 rounded-lg flex items-center justify-center mb-3" style={{ backgroundColor: s.bg, color: s.color }}>
                {s.icon}
              </div>
              <div className="text-[#1F2937] font-bold text-2xl leading-none">{s.value}</div>
              <div className="text-[#9CA3AF] text-xs mt-1">{s.label}</div>
              <div className="text-[#9CA3AF] text-[10px] mt-0.5">{s.sub}</div>
            </div>
          ))}
        </div>

        {/* Main grid */}
        <div className="grid grid-cols-5 gap-4">
          {/* Queue list */}
          <div className="col-span-3 space-y-3">
            {/* Queue table */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
                <h3 className="text-[#1F2937] font-semibold text-sm">Queue List</h3>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF]" />
                  <input placeholder="Search queues…" className="w-44 h-8 pl-8 pr-3 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] transition-all" />
                </div>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                    {['Queue', 'Strategy', 'Members', 'Waiting', 'Avg Wait', 'Status', ''].map((h) => (
                      <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#F3F4F6]">
                  {queues.map((q) => (
                    <tr
                      key={q.id}
                      onClick={() => { setSelectedQueue(q); setOpenMenu(null) }}
                      className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer group ${selectedQueue?.id === q.id ? 'bg-[#EFF6FF]' : ''}`}
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <div className={`w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 ${q.status === 'Active' ? 'bg-[#EFF6FF]' : 'bg-[#F3F4F6]'}`}>
                            <List className={`w-3.5 h-3.5 ${q.status === 'Active' ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}`} />
                          </div>
                          <span className="text-[#1F2937] font-medium text-xs">{q.name}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full border text-[10px] font-semibold ${strategyColor[q.strategy]}`}>
                          {q.strategy}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-1.5">
                          <Users className="w-3 h-3 text-[#9CA3AF]" />
                          <span className="text-[#6B7280] text-xs">{q.members}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        {q.current > 0 ? (
                          <span className="flex items-center gap-1 text-[#EF4444] text-xs font-semibold">
                            <AlertCircle className="w-3 h-3" />
                            {q.current}
                          </span>
                        ) : (
                          <span className="text-[#9CA3AF] text-xs">0</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-[#6B7280] text-xs font-mono">{q.avgWait}</td>
                      <td className="px-4 py-3.5">
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${q.status === 'Active' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#F3F4F6] text-[#6B7280]'}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${q.status === 'Active' ? 'bg-[#22C55E]' : 'bg-[#D1D5DB]'}`} />
                          {q.status}
                        </span>
                      </td>
                      <td className="px-4 py-3.5" onClick={(e) => e.stopPropagation()}>
                        <div className="relative">
                          <button
                            onClick={() => setOpenMenu(openMenu === q.id ? null : q.id)}
                            className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors opacity-0 group-hover:opacity-100"
                          >
                            <MoreHorizontal className="w-4 h-4" />
                          </button>
                          {openMenu === q.id && <QueueActionMenu onClose={() => setOpenMenu(null)} />}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
                <p className="text-[#9CA3AF] text-xs">{queues.length} queues configured</p>
                <div className="flex items-center gap-1">
                  <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF]"><ChevronLeft className="w-3.5 h-3.5" /></button>
                  <button className="w-7 h-7 rounded-lg border bg-[#2563EB] border-[#2563EB] text-white text-xs font-medium">1</button>
                  <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF]"><ChevronRight className="w-3.5 h-3.5" /></button>
                </div>
              </div>
            </div>

            {/* Agent status panel */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="px-5 py-4 border-b border-[#F3F4F6]">
                <h3 className="text-[#1F2937] font-semibold text-sm">Agent Panel</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">Drag agents between states — changes sync in real time</p>
              </div>
              <div className="p-5">
                <div className="grid grid-cols-4 gap-3">
                  {[
                    { state: 'Online' as const, label: 'Available', color: '#22C55E', bg: '#F0FDF4', icon: <CheckCircle className="w-3.5 h-3.5" /> },
                    { state: 'Busy' as const, label: 'In Call', color: '#2563EB', bg: '#EFF6FF', icon: <PhoneCall className="w-3.5 h-3.5" /> },
                    { state: 'Paused' as const, label: 'Paused', color: '#F59E0B', bg: '#FFFBEB', icon: <Pause className="w-3.5 h-3.5" /> },
                    { state: 'Offline' as const, label: 'Offline', color: '#9CA3AF', bg: '#F3F4F6', icon: <WifiOff className="w-3.5 h-3.5" /> },
                  ].map((col) => {
                    const stateAgents = allAgents.filter(a => a.state === col.state)
                    return (
                      <div key={col.state} className="rounded-lg border border-dashed border-[#E5E7EB] p-2.5 min-h-[120px]" style={{ backgroundColor: col.bg + '44' }}>
                        <div className="flex items-center gap-1.5 mb-2.5">
                          <span style={{ color: col.color }}>{col.icon}</span>
                          <span className="text-xs font-semibold" style={{ color: col.color }}>{col.label}</span>
                          <span className="ml-auto w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold" style={{ backgroundColor: col.bg, color: col.color }}>
                            {stateAgents.length}
                          </span>
                        </div>
                        <div className="space-y-1.5">
                          {stateAgents.map(agent => (
                            <div
                              key={agent.id}
                              className="flex items-center gap-1.5 p-1.5 rounded-lg bg-white border border-[#E5E7EB] cursor-grab active:cursor-grabbing hover:border-[#2563EB]/30 transition-all"
                            >
                              <GripVertical className="w-3 h-3 text-[#D1D5DB] flex-shrink-0" />
                              <div className={`w-5 h-5 rounded-full bg-gradient-to-br ${agent.color} flex items-center justify-center text-white text-[8px] font-bold flex-shrink-0`}>
                                {agent.avatar}
                              </div>
                              <span className="text-[#374151] text-[10px] font-medium truncate">{agent.name.split(' ')[0]}</span>
                            </div>
                          ))}
                          {stateAgents.length === 0 && (
                            <p className="text-[#D1D5DB] text-[10px] text-center py-2">Drop here</p>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Queue details panel */}
          {selectedQueue && (
            <div className="col-span-2 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col" style={{ maxHeight: 'calc(100vh - 200px)', position: 'sticky', top: 0 }}>
              <div className="flex items-start justify-between p-5 border-b border-[#F3F4F6]">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <div className="w-8 h-8 rounded-lg bg-[#EFF6FF] flex items-center justify-center">
                      <List className="w-4 h-4 text-[#2563EB]" />
                    </div>
                    <h3 className="text-[#1F2937] font-bold text-base">{selectedQueue.name}</h3>
                  </div>
                  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full border text-[10px] font-semibold ${strategyColor[selectedQueue.strategy]}`}>
                    {selectedQueue.strategy}
                  </span>
                </div>
                <button onClick={() => setSelectedQueue(null)} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Tabs */}
              <div className="flex border-b border-[#F3F4F6] px-2 pt-2">
                {[
                  { id: 'details', label: 'Details' },
                  { id: 'members', label: `Members (${selectedQueue.members})` },
                ].map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`px-4 py-2.5 text-xs font-medium border-b-2 -mb-px transition-colors ${activeTab === tab.id ? 'border-[#2563EB] text-[#2563EB]' : 'border-transparent text-[#6B7280] hover:text-[#374151]'}`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>

              <div className="flex-1 overflow-y-auto">
                {activeTab === 'details' && (
                  <div className="p-5 space-y-5">
                    {/* Live stats */}
                    <div className="grid grid-cols-3 gap-2">
                      {[
                        { label: 'Waiting', value: String(selectedQueue.current), color: selectedQueue.current > 0 ? '#EF4444' : '#22C55E' },
                        { label: 'Avg Wait', value: selectedQueue.avgWait, color: '#F59E0B' },
                        { label: 'Members', value: String(selectedQueue.members), color: '#2563EB' },
                      ].map((s) => (
                        <div key={s.label} className="bg-[#F9FAFB] rounded-lg p-3 text-center border border-[#F3F4F6]">
                          <p className="font-bold text-lg" style={{ color: s.color }}>{s.value}</p>
                          <p className="text-[#9CA3AF] text-[10px] mt-0.5">{s.label}</p>
                        </div>
                      ))}
                    </div>

                    {/* Queue configuration */}
                    <section>
                      <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Queue Configuration</h4>
                      <div className="space-y-2.5">
                        {[
                          { label: 'Strategy', value: selectedQueue.strategy },
                          { label: 'Wrap-up Time', value: '30 seconds' },
                          { label: 'Max Wait', value: '5 minutes' },
                          { label: 'Music on Hold', value: 'default-moh' },
                          { label: 'Overflow Action', value: 'Voicemail' },
                        ].map((row) => (
                          <div key={row.label} className="flex items-center justify-between gap-2">
                            <span className="text-[#9CA3AF] text-xs">{row.label}</span>
                            <span className="text-[#1F2937] text-xs font-medium">{row.value}</span>
                          </div>
                        ))}
                      </div>
                    </section>

                    {/* Business hours */}
                    <section>
                      <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Business Hours</h4>
                      <div className="space-y-1.5">
                        {[
                          { day: 'Mon–Fri', hours: '08:00 – 18:00', active: true },
                          { day: 'Saturday', hours: '09:00 – 14:00', active: true },
                          { day: 'Sunday', hours: 'Closed', active: false },
                        ].map((row) => (
                          <div key={row.day} className="flex items-center justify-between">
                            <span className="text-[#374151] text-xs">{row.day}</span>
                            <span className={`text-xs font-medium ${row.active ? 'text-[#1F2937]' : 'text-[#9CA3AF]'}`}>{row.hours}</span>
                          </div>
                        ))}
                      </div>
                    </section>

                    {/* Actions */}
                    <section className="space-y-2 pt-2">
                      <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE] text-[#2563EB] text-xs font-semibold hover:bg-[#DBEAFE] transition-colors">
                        <Settings className="w-3.5 h-3.5" /> Configure Queue
                      </button>
                      <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-semibold hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                        <Activity className="w-3.5 h-3.5" /> View Analytics
                      </button>
                    </section>
                  </div>
                )}

                {activeTab === 'members' && (
                  <div className="p-5 space-y-4">
                    <div className="flex items-center justify-between">
                      <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Queue Members</h4>
                      <button className="flex items-center gap-1 text-[#2563EB] text-xs font-medium hover:underline">
                        <Plus className="w-3 h-3" /> Add
                      </button>
                    </div>

                    {queueAgents.length > 0 ? (
                      <div className="space-y-2">
                        {queueAgents.map((agent, i) => {
                          const sc = agentStateConfig[agent.state]
                          return (
                            <div key={agent.id} className="flex items-center gap-3 p-3 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] transition-colors bg-[#F9FAFB]">
                              <div className={`w-8 h-8 rounded-full bg-gradient-to-br ${agent.color} flex items-center justify-center text-white text-xs font-bold flex-shrink-0`}>
                                {agent.avatar}
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="text-[#1F2937] text-xs font-medium">{agent.name}</p>
                                <p className="text-[#9CA3AF] text-[10px]">Ext. {agent.ext}</p>
                              </div>
                              <div className="text-right flex-shrink-0">
                                <div className="flex items-center gap-1 justify-end">
                                  <span className={`w-1.5 h-1.5 rounded-full ${sc.dot}`} />
                                  <span className={`text-[10px] font-medium ${sc.cls}`}>{sc.label}</span>
                                </div>
                                <div className="flex gap-2 mt-1 text-[#9CA3AF] text-[9px]">
                                  <span>P: {i === 0 ? 0 : 1}</span>
                                  <span>Pen: 0</span>
                                </div>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    ) : (
                      <div className="flex flex-col items-center justify-center py-10">
                        <div className="w-12 h-12 rounded-2xl bg-[#F3F4F6] flex items-center justify-center mb-3">
                          <Users className="w-6 h-6 text-[#D1D5DB]" />
                        </div>
                        <p className="text-[#374151] font-medium text-xs">No members assigned</p>
                        <button className="mt-3 text-[#2563EB] text-xs font-medium hover:underline">Add agents to this queue</button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </TenantLayout>
  )
}
