import { useState, useEffect } from 'react'
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

interface ApiQueue {
  id: string
  tenantId: string
  name: string
  strategy: string
  wrapUpTimeSeconds: number
  maxWaitSeconds: number
  musicOnHold: string
  overflowAction: string
  businessHours: string
  status: 'active' | 'inactive'
  waitingCalls: number
  avgWaitSeconds: number
  memberCount: number
  activeMembers: number
}

interface ApiAgentState {
  id: string
  agentId: string
  username: string
  email: string
  currentState: 'available' | 'in_call' | 'paused' | 'offline'
  stateChangedAt: string
}

interface QueueMember {
  id: string
  queueId: string
  agentId: string
  penalty: number
  agentUsername: string
  agentEmail: string
  agentState: string
}

const DEFAULT_TENANT_ID = '11111111-1111-1111-1111-111111111111'

const agentStateConfig: Record<string, { cls: string; dot: string; icon: ReactElement; label: string }> = {
  available: { cls: 'text-[#22C55E]', dot: 'bg-[#22C55E]', icon: <CheckCircle className="w-3 h-3" />, label: 'Available' },
  in_call: { cls: 'text-[#2563EB]', dot: 'bg-[#2563EB]', icon: <PhoneCall className="w-3 h-3" />, label: 'In Call' },
  paused: { cls: 'text-[#F59E0B]', dot: 'bg-[#F59E0B] animate-pulse', icon: <Pause className="w-3 h-3" />, label: 'Paused' },
  offline: { cls: 'text-[#9CA3AF]', dot: 'bg-[#D1D5DB]', icon: <WifiOff className="w-3 h-3" />, label: 'Offline' },
}

const strategyColor: Record<string, string> = {
  round_robin: 'bg-[#EFF6FF] text-[#2563EB] border-[#BFDBFE]',
  least_recent: 'bg-[#F0FDF4] text-[#15803D] border-[#BBF7D0]',
  ring_all: 'bg-[#FDF4FF] text-[#7E22CE] border-[#E9D5FF]',
  linear: 'bg-[#FFFBEB] text-[#A16207] border-[#FDE68A]',
}

const strategyDisplay: Record<string, string> = {
  round_robin: 'Round Robin',
  least_recent: 'Least Recent',
  ring_all: 'Ring All',
  linear: 'Linear',
}

export default function QueueManagement({ onLogout }: { onLogout: () => void }) {
  const [queues, setQueues] = useState<ApiQueue[]>([])
  const [agents, setAgents] = useState<ApiAgentState[]>([])
  const [selectedQueue, setSelectedQueue] = useState<ApiQueue | null>(null)
  const [selectedQueueMembers, setSelectedQueueMembers] = useState<QueueMember[]>([])
  const [loading, setLoading] = useState(true)
  const [errorToast, setErrorToast] = useState<string | null>(null)

  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'details' | 'members'>('details')
  const [searchQuery, setSearchQuery] = useState('')

  // Create Modal State
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newQueueName, setNewQueueName] = useState('')
  const [newQueueStrategy, setNewQueueStrategy] = useState('round_robin')
  const [newWrapUpTime, setNewWrapUpTime] = useState(15)

  // Configure Modal State
  const [showConfigModal, setShowConfigModal] = useState(false)
  const [configWrapUpTime, setConfigWrapUpTime] = useState(15)
  const [configMaxWait, setConfigMaxWait] = useState(300)

  const fetchQueuesAndAgents = async () => {
    try {
      const [queuesRes, agentsRes] = await Promise.all([
        fetch('/api/v1/queues', { headers: { 'X-Tenant-ID': DEFAULT_TENANT_ID } }),
        fetch('/api/v1/agents', { headers: { 'X-Tenant-ID': DEFAULT_TENANT_ID } }),
      ])

      if (queuesRes.ok) {
        const qJson = await queuesRes.json()
        if (qJson.success) {
          setQueues(qJson.data || [])
          if (selectedQueue) {
            const updatedSelected = (qJson.data || []).find((q: ApiQueue) => q.id === selectedQueue.id)
            if (updatedSelected) setSelectedQueue(updatedSelected)
          }
        }
      }

      if (agentsRes.ok) {
        const aJson = await agentsRes.json()
        if (aJson.success) {
          setAgents(aJson.data || [])
        }
      }
    } catch (err) {
      console.error('Error fetching queue data:', err)
    } finally {
      setLoading(false)
    }
  }

  const fetchQueueDetail = async (queueId: string) => {
    try {
      const res = await fetch(`/api/v1/queues/${queueId}`, {
        headers: { 'X-Tenant-ID': DEFAULT_TENANT_ID },
      })
      if (res.ok) {
        const json = await res.json()
        if (json.success && json.data) {
          setSelectedQueue(json.data.queue)
          setSelectedQueueMembers(json.data.members || [])
        }
      }
    } catch (err) {
      console.error('Error fetching queue detail:', err)
    }
  }

  useEffect(() => {
    fetchQueuesAndAgents()
    const interval = setInterval(fetchQueuesAndAgents, 5000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    if (selectedQueue) {
      fetchQueueDetail(selectedQueue.id)
    }
  }, [selectedQueue?.id])

  const handleCreateQueue = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newQueueName.trim()) return

    try {
      const res = await fetch('/api/v1/queues', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': DEFAULT_TENANT_ID,
        },
        body: JSON.stringify({
          name: newQueueName.trim(),
          strategy: newQueueStrategy,
          wrapUpTimeSeconds: Number(newWrapUpTime),
        }),
      })

      if (res.ok) {
        setShowCreateModal(false)
        setNewQueueName('')
        fetchQueuesAndAgents()
      } else {
        const err = await res.json()
        setErrorToast(err.message || 'Failed to create queue')
      }
    } catch (err) {
      setErrorToast('Network error creating queue')
    }
  }

  const handleDeleteQueue = async (id: string) => {
    if (!confirm('Are you sure you want to delete this queue?')) return

    try {
      const res = await fetch(`/api/v1/queues/${id}`, {
        method: 'DELETE',
        headers: { 'X-Tenant-ID': DEFAULT_TENANT_ID },
      })

      if (res.ok) {
        if (selectedQueue?.id === id) setSelectedQueue(null)
        fetchQueuesAndAgents()
      }
    } catch (err) {
      setErrorToast('Failed to delete queue')
    }
  }

  const handleAgentStateChange = async (agentId: string, newState: 'available' | 'in_call' | 'paused' | 'offline') => {
    // Optimistic UI update
    setAgents(prev => prev.map(a => a.agentId === agentId ? { ...a, currentState: newState } : a))

    try {
      const res = await fetch(`/api/v1/agents/${agentId}/state`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': DEFAULT_TENANT_ID,
        },
        body: JSON.stringify({ state: newState }),
      })

      if (!res.ok) {
        fetchQueuesAndAgents() // rollback on failure
        setErrorToast('Failed to update agent state in Asterisk')
      }
    } catch (err) {
      fetchQueuesAndAgents()
      setErrorToast('Network error changing agent state')
    }
  }

  const handleAddMember = async (agentId: string) => {
    if (!selectedQueue) return

    try {
      const res = await fetch(`/api/v1/queues/${selectedQueue.id}/members`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': DEFAULT_TENANT_ID,
        },
        body: JSON.stringify({ agentId, penalty: 0 }),
      })

      if (res.ok) {
        fetchQueueDetail(selectedQueue.id)
        fetchQueuesAndAgents()
      }
    } catch (err) {
      setErrorToast('Error adding member to queue')
    }
  }

  const handleRemoveMember = async (agentId: string) => {
    if (!selectedQueue) return

    try {
      const res = await fetch(`/api/v1/queues/${selectedQueue.id}/members/${agentId}`, {
        method: 'DELETE',
        headers: { 'X-Tenant-ID': DEFAULT_TENANT_ID },
      })

      if (res.ok) {
        fetchQueueDetail(selectedQueue.id)
        fetchQueuesAndAgents()
      }
    } catch (err) {
      setErrorToast('Error removing member from queue')
    }
  }

  const filteredQueues = queues.filter(q => q.name.toLowerCase().includes(searchQuery.toLowerCase()))

  const totalWaiting = queues.reduce((s, q) => s + (q.waitingCalls || 0), 0)
  const availableAgents = agents.filter(a => a.currentState === 'available').length
  const busyAgents = agents.filter(a => a.currentState === 'in_call').length

  const headerActions = (
    <button
      onClick={() => setShowCreateModal(true)}
      className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20 cursor-pointer"
    >
      <Plus className="w-4 h-4" />
      Create Queue
    </button>
  )

  return (
    <TenantLayout
      activeNav="queues"
      onLogout={onLogout}
      pageTitle="Queue Management"
      pageSubtitle="Manage Asterisk call queues and live agent states"
      headerActions={headerActions}
    >
      {errorToast && (
        <div className="mb-4 p-3 rounded-lg bg-[#FEF2F2] border border-[#FCA5A5] text-[#991B1B] text-xs flex items-center justify-between">
          <span>{errorToast}</span>
          <button onClick={() => setErrorToast(null)}><X className="w-4 h-4" /></button>
        </div>
      )}

      <div className="space-y-4">
        {/* Dashboard Stat Cards */}
        <div className="grid grid-cols-4 gap-4">
          {[
            { label: 'Total Queues', value: queues.length, icon: <List className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF', sub: `${queues.filter(q => q.status === 'active').length} active` },
            { label: 'Waiting Calls', value: totalWaiting, icon: <Clock className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB', sub: 'Across all queues' },
            { label: 'Available Agents', value: availableAgents, icon: <Headphones className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4', sub: `${busyAgents} in call` },
            { label: 'Avg Wait Time', value: queues.length > 0 ? `${Math.round(queues.reduce((acc, q) => acc + (q.avgWaitSeconds || 0), 0) / (queues.length || 1))}s` : '0s', icon: <Activity className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF', sub: 'Platform average' },
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

        {/* Main Grid */}
        <div className="grid grid-cols-5 gap-4">
          {/* Queue List Table */}
          <div className="col-span-3 space-y-3">
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-5 py-4 border-b border-[#F3F4F6]">
                <h3 className="text-[#1F2937] font-semibold text-sm">Queue List</h3>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF]" />
                  <input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search queues…"
                    className="w-44 h-8 pl-8 pr-3 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] transition-all"
                  />
                </div>
              </div>

              {loading ? (
                <div className="p-8 text-center text-[#9CA3AF] text-xs">Loading queues from backend...</div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                      {['Queue', 'Strategy', 'Members', 'Waiting', 'Avg Wait', 'Status', ''].map((h) => (
                        <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#F3F4F6]">
                    {filteredQueues.map((q) => (
                      <tr
                        key={q.id}
                        onClick={() => { setSelectedQueue(q); setOpenMenu(null) }}
                        className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer group ${selectedQueue?.id === q.id ? 'bg-[#EFF6FF]' : ''}`}
                      >
                        <td className="px-4 py-3.5">
                          <div className="flex items-center gap-2.5">
                            <div className={`w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 ${q.status === 'active' ? 'bg-[#EFF6FF]' : 'bg-[#F3F4F6]'}`}>
                              <List className={`w-3.5 h-3.5 ${q.status === 'active' ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}`} />
                            </div>
                            <span className="text-[#1F2937] font-medium text-xs">{q.name}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3.5">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full border text-[10px] font-semibold ${strategyColor[q.strategy] || strategyColor.round_robin}`}>
                            {strategyDisplay[q.strategy] || q.strategy}
                          </span>
                        </td>
                        <td className="px-4 py-3.5">
                          <div className="flex items-center gap-1.5">
                            <Users className="w-3 h-3 text-[#9CA3AF]" />
                            <span className="text-[#6B7280] text-xs">{q.memberCount}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3.5">
                          {q.waitingCalls > 0 ? (
                            <span className="flex items-center gap-1 text-[#EF4444] text-xs font-semibold">
                              <AlertCircle className="w-3 h-3" />
                              {q.waitingCalls}
                            </span>
                          ) : (
                            <span className="text-[#9CA3AF] text-xs">0</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-[#6B7280] text-xs font-mono">{q.avgWaitSeconds}s</td>
                        <td className="px-4 py-3.5">
                          <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${q.status === 'active' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#F3F4F6] text-[#6B7280]'}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${q.status === 'active' ? 'bg-[#22C55E]' : 'bg-[#D1D5DB]'}`} />
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
                            {openMenu === q.id && (
                              <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1">
                                <button onClick={() => { handleDeleteQueue(q.id); setOpenMenu(null) }} className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium text-[#EF4444] hover:bg-[#F9FAFB] transition-colors">
                                  <Trash2 className="w-3.5 h-3.5" /> Delete Queue
                                </button>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
                <p className="text-[#9CA3AF] text-xs">{filteredQueues.length} queues configured</p>
                <div className="flex items-center gap-1">
                  <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF]"><ChevronLeft className="w-3.5 h-3.5" /></button>
                  <button className="w-7 h-7 rounded-lg border bg-[#2563EB] border-[#2563EB] text-white text-xs font-medium">1</button>
                  <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF]"><ChevronRight className="w-3.5 h-3.5" /></button>
                </div>
              </div>
            </div>

            {/* Agent Status Panel */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              <div className="px-5 py-4 border-b border-[#F3F4F6]">
                <h3 className="text-[#1F2937] font-semibold text-sm">Agent State Panel</h3>
                <p className="text-[#9CA3AF] text-xs mt-0.5">Click state button to change agent status in Asterisk via AMI</p>
              </div>
              <div className="p-5">
                <div className="grid grid-cols-4 gap-3">
                  {[
                    { state: 'available' as const, label: 'Available', color: '#22C55E', bg: '#F0FDF4', icon: <CheckCircle className="w-3.5 h-3.5" /> },
                    { state: 'in_call' as const, label: 'In Call', color: '#2563EB', bg: '#EFF6FF', icon: <PhoneCall className="w-3.5 h-3.5" /> },
                    { state: 'paused' as const, label: 'Paused', color: '#F59E0B', bg: '#FFFBEB', icon: <Pause className="w-3.5 h-3.5" /> },
                    { state: 'offline' as const, label: 'Offline', color: '#9CA3AF', bg: '#F3F4F6', icon: <WifiOff className="w-3.5 h-3.5" /> },
                  ].map((col) => {
                    const stateAgents = agents.filter(a => a.currentState === col.state)
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
                            <div key={agent.agentId} className="flex flex-col gap-1 p-2 rounded-lg bg-white border border-[#E5E7EB] hover:border-[#2563EB]/40 transition-all">
                              <div className="flex items-center gap-2">
                                <div className="w-6 h-6 rounded-full bg-[#2563EB] flex items-center justify-center text-white text-[9px] font-bold">
                                  {agent.username ? agent.username.substring(0, 2).toUpperCase() : 'AG'}
                                </div>
                                <span className="text-[#374151] text-[11px] font-medium truncate">{agent.username}</span>
                              </div>

                              {/* State switch buttons */}
                              <div className="flex gap-1 mt-1 pt-1 border-t border-[#F3F4F6]">
                                {(['available', 'paused', 'offline'] as const).map(targetState => (
                                  <button
                                    key={targetState}
                                    onClick={() => handleAgentStateChange(agent.agentId, targetState)}
                                    className={`px-1.5 py-0.5 text-[9px] rounded font-medium border transition-colors cursor-pointer ${
                                      agent.currentState === targetState ? 'bg-[#2563EB] text-white border-[#2563EB]' : 'bg-[#F9FAFB] text-[#6B7280] border-[#E5E7EB] hover:bg-[#F3F4F6]'
                                    }`}
                                  >
                                    {targetState === 'available' ? 'Avail' : targetState === 'paused' ? 'Pause' : 'Off'}
                                  </button>
                                ))}
                              </div>
                            </div>
                          ))}
                          {stateAgents.length === 0 && (
                            <p className="text-[#D1D5DB] text-[10px] text-center py-2">No agents</p>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Queue Detail Panel */}
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
                  <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full border text-[10px] font-semibold ${strategyColor[selectedQueue.strategy] || strategyColor.round_robin}`}>
                    {strategyDisplay[selectedQueue.strategy] || selectedQueue.strategy}
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
                  { id: 'members', label: `Members (${selectedQueueMembers.length})` },
                ].map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id as 'details' | 'members')}
                    className={`px-4 py-2.5 text-xs font-medium border-b-2 -mb-px transition-colors cursor-pointer ${activeTab === tab.id ? 'border-[#2563EB] text-[#2563EB]' : 'border-transparent text-[#6B7280] hover:text-[#374151]'}`}
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
                        { label: 'Waiting', value: String(selectedQueue.waitingCalls), color: selectedQueue.waitingCalls > 0 ? '#EF4444' : '#22C55E' },
                        { label: 'Avg Wait', value: `${selectedQueue.avgWaitSeconds}s`, color: '#F59E0B' },
                        { label: 'Members', value: String(selectedQueueMembers.length), color: '#2563EB' },
                      ].map((s) => (
                        <div key={s.label} className="bg-[#F9FAFB] rounded-lg p-3 text-center border border-[#F3F4F6]">
                          <p className="font-bold text-lg" style={{ color: s.color }}>{s.value}</p>
                          <p className="text-[#9CA3AF] text-[10px] mt-0.5">{s.label}</p>
                        </div>
                      ))}
                    </div>

                    {/* Queue config */}
                    <section>
                      <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Queue Configuration</h4>
                      <div className="space-y-2.5">
                        {[
                          { label: 'Strategy', value: strategyDisplay[selectedQueue.strategy] || selectedQueue.strategy },
                          { label: 'Wrap-up Time', value: `${selectedQueue.wrapUpTimeSeconds} seconds` },
                          { label: 'Max Wait', value: `${selectedQueue.maxWaitSeconds} seconds` },
                          { label: 'Music on Hold', value: selectedQueue.musicOnHold },
                          { label: 'Overflow Action', value: selectedQueue.overflowAction },
                        ].map((row) => (
                          <div key={row.label} className="flex items-center justify-between gap-2">
                            <span className="text-[#9CA3AF] text-xs">{row.label}</span>
                            <span className="text-[#1F2937] text-xs font-medium">{row.value}</span>
                          </div>
                        ))}
                      </div>
                    </section>
                  </div>
                )}

                {activeTab === 'members' && (
                  <div className="p-5 space-y-4">
                    <div className="flex items-center justify-between">
                      <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Queue Members</h4>
                    </div>

                    {/* Add member dropdown */}
                    <div className="flex gap-2">
                      <select
                        id="add-agent-select"
                        className="flex-1 h-8 px-2 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] outline-none"
                      >
                        <option value="">Select agent to add...</option>
                        {agents
                          .filter(a => !selectedQueueMembers.some(m => m.agentId === a.agentId))
                          .map(a => (
                            <option key={a.agentId} value={a.agentId}>{a.username} ({a.email})</option>
                          ))}
                      </select>
                      <button
                        onClick={() => {
                          const el = document.getElementById('add-agent-select') as HTMLSelectElement
                          if (el && el.value) {
                            handleAddMember(el.value)
                            el.value = ''
                          }
                        }}
                        className="px-3 py-1 rounded-lg bg-[#2563EB] text-white text-xs font-medium hover:bg-[#1E40AF] transition-colors cursor-pointer"
                      >
                        Add
                      </button>
                    </div>

                    {selectedQueueMembers.length > 0 ? (
                      <div className="space-y-2">
                        {selectedQueueMembers.map((member) => {
                          const sc = agentStateConfig[member.agentState] || agentStateConfig.available
                          return (
                            <div key={member.id} className="flex items-center gap-3 p-3 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] transition-colors bg-[#F9FAFB]">
                              <div className="w-8 h-8 rounded-full bg-[#2563EB] flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                                {member.agentUsername ? member.agentUsername.substring(0, 2).toUpperCase() : 'AG'}
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="text-[#1F2937] text-xs font-medium">{member.agentUsername}</p>
                                <p className="text-[#9CA3AF] text-[10px]">{member.agentEmail}</p>
                              </div>
                              <div className="flex items-center gap-2">
                                <span className={`w-1.5 h-1.5 rounded-full ${sc.dot}`} />
                                <span className={`text-[10px] font-medium ${sc.cls}`}>{sc.label}</span>
                                <button
                                  onClick={() => handleRemoveMember(member.agentId)}
                                  className="w-6 h-6 rounded flex items-center justify-center text-[#9CA3AF] hover:text-[#EF4444] transition-colors cursor-pointer"
                                >
                                  <X className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    ) : (
                      <div className="flex flex-col items-center justify-center py-8">
                        <Users className="w-6 h-6 text-[#D1D5DB] mb-2" />
                        <p className="text-[#374151] font-medium text-xs">No members assigned to this queue</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Create Queue Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-[#F3F4F6] pb-3">
              <h3 className="text-[#1F2937] font-bold text-base">Create New Call Queue</h3>
              <button onClick={() => setShowCreateModal(false)} className="text-[#9CA3AF] hover:text-[#1F2937]"><X className="w-4 h-4" /></button>
            </div>

            <form onSubmit={handleCreateQueue} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-[#374151] mb-1">Queue Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Technical Support L1"
                  value={newQueueName}
                  onChange={(e) => setNewQueueName(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] outline-none focus:border-[#2563EB]"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-[#374151] mb-1">Ring Strategy</label>
                <select
                  value={newQueueStrategy}
                  onChange={(e) => setNewQueueStrategy(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] outline-none focus:border-[#2563EB]"
                >
                  <option value="round_robin">Round Robin</option>
                  <option value="least_recent">Least Recent</option>
                  <option value="ring_all">Ring All</option>
                  <option value="linear">Linear</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-[#374151] mb-1">Wrap-up Time (seconds)</label>
                <input
                  type="number"
                  min={0}
                  max={300}
                  value={newWrapUpTime}
                  onChange={(e) => setNewWrapUpTime(Number(e.target.value))}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] outline-none focus:border-[#2563EB]"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-[#F3F4F6]">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-lg border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:bg-[#F9FAFB]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-medium hover:bg-[#1E40AF]"
                >
                  Create & Provision Queue
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </TenantLayout>
  )
}
