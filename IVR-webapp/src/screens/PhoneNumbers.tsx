import { useState, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import { backendUrl } from '../api/backendUrl'
import {
  Plus, Upload, Search, ChevronDown, MoreHorizontal, GitBranch,
  PhoneOff, EyeOff, BarChart2, ChevronLeft, ChevronRight,
  Filter, CheckCircle, AlertCircle, TrendingUp, Phone,
  ExternalLink, RefreshCw, X
} from 'lucide-react'

interface PhoneNumberItem {
  id: string
  phoneNumber: string
  country: string
  provider: string
  assignedFlowId?: string
  assignedFlowName?: string
  status: 'ACTIVE' | 'UNASSIGNED' | 'DISABLED'
}

interface PublishedFlowItem {
  flowId: string
  flowName: string
  businessName: string
}

const statusConfig: Record<string, { label: string; cls: string; dot: string }> = {
  ACTIVE: { label: 'Active', cls: 'bg-[#DCFCE7] text-[#15803D]', dot: 'bg-[#22C55E]' },
  Active: { label: 'Active', cls: 'bg-[#DCFCE7] text-[#15803D]', dot: 'bg-[#22C55E]' },
  UNASSIGNED: { label: 'Unassigned', cls: 'bg-[#FEF9C3] text-[#A16207]', dot: 'bg-[#F59E0B]' },
  Unassigned: { label: 'Unassigned', cls: 'bg-[#FEF9C3] text-[#A16207]', dot: 'bg-[#F59E0B]' },
  DISABLED: { label: 'Disabled', cls: 'bg-[#F3F4F6] text-[#6B7280]', dot: 'bg-[#D1D5DB]' },
  Disabled: { label: 'Disabled', cls: 'bg-[#F3F4F6] text-[#6B7280]', dot: 'bg-[#D1D5DB]' },
}

const providerColor: Record<string, string> = {
  Twilio: 'bg-[#F0F9FF] text-[#0369A1] border-[#BAE6FD]',
  Vonage: 'bg-[#FDF4FF] text-[#7E22CE] border-[#E9D5FF]',
  Bandwidth: 'bg-[#FFF7ED] text-[#C2410C] border-[#FED7AA]',
}

const countryFlags: Record<string, string> = {
  US: '🇺🇸',
  UK: '🇬🇧',
  DE: '🇩🇪',
  CA: '🇨🇦',
  EG: '🇪🇬',
}

function UsageBar({ used = 124, cap = 1000 }: { used?: number; cap?: number }) {
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

function QuickActionMenu({ onAssignClick, onClose }: { onAssignClick: () => void; onClose: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1 overflow-hidden">
      <button
        onClick={() => { onAssignClick(); onClose(); }}
        className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] text-[#374151] transition-colors"
      >
        <GitBranch className="w-3.5 h-3.5" />
        Assign IVR
      </button>
      <button
        onClick={onClose}
        className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] text-[#374151] transition-colors"
      >
        <BarChart2 className="w-3.5 h-3.5" />
        View Analytics
      </button>
      <button
        onClick={onClose}
        className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] text-[#F59E0B] transition-colors"
      >
        <EyeOff className="w-3.5 h-3.5" />
        Disable
      </button>
    </div>
  )
}

export default function PhoneNumbers({ onLogout }: { onLogout: () => void }) {
  const [numbers, setNumbers] = useState<PhoneNumberItem[]>([])
  const [publishedFlows, setPublishedFlows] = useState<PublishedFlowItem[]>([])
  const [loading, setLoading] = useState(true)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('All Status')
  const [providerFilter, setProviderFilter] = useState('All Providers')

  // Modals state
  const [showAddModal, setShowAddModal] = useState(false)
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [selectedNumForAssign, setSelectedNumForAssign] = useState<PhoneNumberItem | null>(null)
  const [selectedFlowId, setSelectedFlowId] = useState('')

  // Add DID form state
  const [newNumber, setNewNumber] = useState('')
  const [newCountry, setNewCountry] = useState('US')
  const [newProvider, setNewProvider] = useState('Twilio')

  // Status & error messages
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionSuccess, setActionSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const [todaysInbound, setTodaysInbound] = useState(0)

  const fetchPhoneNumbers = async () => {
    try {
      setLoading(true)
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      let res = await fetch('/api/v1/tenant/phone-numbers', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/tenant/phone-numbers'), { headers })
      }
      const json = await res.json()
      if (json.success && Array.isArray(json.data)) {
        setNumbers(json.data)
      } else if (Array.isArray(json)) {
        setNumbers(json)
      }
    } catch (e) {
      console.error('Failed to fetch phone numbers', e)
    } finally {
      setLoading(false)
    }
  }

  const fetchPhoneNumbersStats = async () => {
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      let res = await fetch('/api/v1/tenant/phone-numbers/stats', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/tenant/phone-numbers/stats'), { headers })
      }
      const json = await res.json()
      if (json.success && json.data) {
        setTodaysInbound(json.data.todaysInbound || 0)
      }
    } catch (e) {
      console.error('Failed to fetch phone numbers stats', e)
    }
  }

  const fetchPublishedFlows = async () => {
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      let res = await fetch('/api/v1/tenant/published-flows', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/tenant/published-flows'), { headers })
      }
      const json = await res.json()
      if (json.success && Array.isArray(json.data)) {
        setPublishedFlows(json.data)
      }
    } catch (e) {
      console.error('Failed to fetch published flows', e)
    }
  }

  useEffect(() => {
    fetchPhoneNumbers()
    fetchPhoneNumbersStats()
    fetchPublishedFlows()

    const interval = setInterval(() => {
      fetchPhoneNumbers()
      fetchPhoneNumbersStats()
    }, 5000)

    return () => clearInterval(interval)
  }, [])

  const handleAddNumberSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newNumber.trim()) return

    setSubmitting(true)
    setActionError(null)

    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      const body = JSON.stringify({
        phoneNumber: newNumber.trim(),
        country: newCountry,
        provider: newProvider
      })
      let res = await fetch('/api/v1/tenant/phone-numbers', { method: 'POST', headers, body }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/tenant/phone-numbers'), { method: 'POST', headers, body })
      }
      const json = await res.json()
      if (json.success || json.id) {
        setActionSuccess('Phone number added successfully!')
        setNewNumber('')
        setShowAddModal(false)
        fetchPhoneNumbers()
      } else {
        setActionError(json.message || json.error || 'Failed to add phone number')
      }
    } catch (err: any) {
      setActionError(err.message || 'Error adding phone number')
    } finally {
      setSubmitting(false)
    }
  }

  const handleAssignIvrSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedNumForAssign || !selectedFlowId) return

    setSubmitting(true)
    setActionError(null)
    setActionSuccess(null)

    const targetFlow = publishedFlows.find(f => f.flowId === selectedFlowId)
    const flowName = targetFlow ? targetFlow.flowName : selectedFlowId

    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      const body = JSON.stringify({
        flowId: selectedFlowId,
        flowName: flowName
      })
      let res = await fetch(`/api/v1/tenant/phone-numbers/${selectedNumForAssign.id}/assign-ivr`, {
        method: 'PUT',
        headers,
        body
      }).catch(() => null)

      if (!res || !res.ok) {
        res = await fetch(backendUrl(`/api/v1/tenant/phone-numbers/${selectedNumForAssign.id}/assign-ivr`), {
          method: 'PUT',
          headers,
          body
        })
      }

      const json = await res.json()

      if (json.success || json.data) {
        setActionSuccess(`Flow "${flowName}" assigned & Asterisk dialplan provisioned!`)
        setShowAssignModal(false)
        setSelectedNumForAssign(null)
        setSelectedFlowId('')
        fetchPhoneNumbers()
      } else {
        setActionError(json.message || json.error || 'Assignment failed')
      }
    } catch (err: any) {
      setActionError(err.message || 'Failed to assign IVR flow')
    } finally {
      setSubmitting(false)
    }
  }

  const openAssignModal = (num: PhoneNumberItem) => {
    setSelectedNumForAssign(num)
    setSelectedFlowId(publishedFlows[0]?.flowId || '')
    setActionError(null)
    setShowAssignModal(true)
  }

  const filtered = numbers.filter((n) => {
    const statusMatch = statusFilter === 'All Status' ||
      n.status.toUpperCase() === statusFilter.toUpperCase()
    const providerMatch = providerFilter === 'All Providers' || n.provider === providerFilter
    const searchQuery = search.toLowerCase()
    const numberMatch = n.phoneNumber.toLowerCase().includes(searchQuery) ||
      (n.assignedFlowName && n.assignedFlowName.toLowerCase().includes(searchQuery))
    return statusMatch && providerMatch && numberMatch
  })

  const totalCount = numbers.length
  const activeCount = numbers.filter(n => n.status.toUpperCase() === 'ACTIVE').length
  const unassignedCount = numbers.filter(n => n.status.toUpperCase() === 'UNASSIGNED').length

  const stats = [
    { label: 'Total Numbers', value: totalCount.toString(), delta: 'Provisioned DIDs', color: '#2563EB', bg: '#EFF6FF', icon: <Phone className="w-5 h-5" /> },
    { label: 'Active', value: activeCount.toString(), delta: `${totalCount > 0 ? Math.round((activeCount / totalCount) * 100) : 0}% of total`, color: '#22C55E', bg: '#F0FDF4', icon: <CheckCircle className="w-5 h-5" /> },
    { label: 'Unassigned', value: unassignedCount.toString(), delta: 'Need IVR flow', color: '#F59E0B', bg: '#FFFBEB', icon: <AlertCircle className="w-5 h-5" /> },
    { label: "Today's Inbound", value: todaysInbound.toLocaleString(), delta: 'Active Asterisk route', color: '#8B5CF6', bg: '#F5F3FF', icon: <TrendingUp className="w-5 h-5" /> },
  ]

  const headerActions = (
    <>
      <button
        onClick={() => setShowAddModal(true)}
        className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20"
      >
        <Plus className="w-4 h-4" />
        Add Number
      </button>
    </>
  )

  return (
    <TenantLayout
      activeNav="phone-numbers"
      onLogout={onLogout}
      pageTitle="Phone Numbers"
      pageSubtitle="Manage inbound DIDs and Asterisk IVR flow assignments"
      headerActions={headerActions}
    >
      <div className="space-y-4">
        {/* Banner Messages */}
        {actionSuccess && (
          <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-lg text-xs font-semibold flex items-center justify-between">
            <span>✓ {actionSuccess}</span>
            <button onClick={() => setActionSuccess(null)}><X className="w-4 h-4" /></button>
          </div>
        )}
        {actionError && (
          <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-xs font-semibold flex items-center justify-between">
            <span>⚠️ {actionError}</span>
            <button onClick={() => setActionError(null)}><X className="w-4 h-4" /></button>
          </div>
        )}

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
                placeholder="Search by number or assigned IVR…"
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
          {loading ? (
            <div className="flex items-center justify-center py-12 text-[#9CA3AF] text-sm">
              <RefreshCw className="w-5 h-5 animate-spin mr-2" />
              Loading phone numbers…
            </div>
          ) : (
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
                  const statusKey = num.status || 'UNASSIGNED'
                  const sc = statusConfig[statusKey] || statusConfig['UNASSIGNED']
                  const flag = countryFlags[num.country] || '🇺🇸'
                  const hasAssigned = num.assignedFlowName && num.assignedFlowName.trim().length > 0 && num.assignedFlowName !== '—'

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
                          <span className="text-[#1F2937] font-medium text-xs font-mono tracking-tight">{num.phoneNumber}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-1.5">
                          <span className="text-lg">{flag}</span>
                          <span className="text-[#6B7280] text-xs">{num.country}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        {hasAssigned ? (
                          <div className="flex items-center gap-2">
                            <div className="w-2 h-2 rounded-full flex-shrink-0 bg-[#2563EB]" />
                            <span className="text-[#374151] text-xs font-medium">{num.assignedFlowName}</span>
                            <button
                              onClick={() => openAssignModal(num)}
                              className="text-[#9CA3AF] hover:text-[#2563EB] text-[10px] ml-1 underline"
                            >
                              Change
                            </button>
                          </div>
                        ) : (
                          <button
                            onClick={() => openAssignModal(num)}
                            className="flex items-center gap-1.5 text-[#2563EB] text-xs font-medium hover:underline"
                          >
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
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-md border text-[10px] font-semibold ${providerColor[num.provider] || 'bg-gray-100 text-gray-800'}`}>
                          {num.provider || 'Twilio'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <UsageBar used={120} cap={1000} />
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="relative">
                          <button
                            onClick={() => setOpenMenu(openMenu === num.id ? null : num.id)}
                            className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] hover:text-[#374151] transition-colors"
                          >
                            <MoreHorizontal className="w-4 h-4" />
                          </button>
                          {openMenu === num.id && (
                            <QuickActionMenu
                              onAssignClick={() => openAssignModal(num)}
                              onClose={() => setOpenMenu(null)}
                            />
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}

          {/* Empty state */}
          {!loading && filtered.length === 0 && (
            <div className="flex flex-col items-center justify-center py-16">
              <div className="w-14 h-14 rounded-2xl bg-[#F3F4F6] flex items-center justify-center mb-4">
                <Phone className="w-7 h-7 text-[#D1D5DB]" />
              </div>
              <p className="text-[#374151] font-medium text-sm">No phone numbers found</p>
              <p className="text-[#9CA3AF] text-xs mt-1">Try adding a new DID number</p>
            </div>
          )}
        </div>
      </div>

      {/* Add Number Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="text-base font-bold text-[#1F2937]">Add DID Phone Number</h3>
              <button onClick={() => setShowAddModal(false)}><X className="w-5 h-5 text-[#9CA3AF]" /></button>
            </div>
            <form onSubmit={handleAddNumberSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#374151] mb-1">Phone Number (E.164 format)</label>
                <input
                  type="text"
                  required
                  placeholder="+1 (415) 882-3301"
                  value={newNumber}
                  onChange={(e) => setNewNumber(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#374151] mb-1">Country</label>
                  <select
                    value={newCountry}
                    onChange={(e) => setNewCountry(e.target.value)}
                    className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                  >
                    <option value="US">United States (🇺🇸)</option>
                    <option value="UK">United Kingdom (🇬🇧)</option>
                    <option value="DE">Germany (🇩🇪)</option>
                    <option value="CA">Canada (🇨🇦)</option>
                    <option value="EG">Egypt (🇪🇬)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#374151] mb-1">Provider</label>
                  <select
                    value={newProvider}
                    onChange={(e) => setNewProvider(e.target.value)}
                    className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                  >
                    <option value="Twilio">Twilio</option>
                    <option value="Vonage">Vonage</option>
                    <option value="Bandwidth">Bandwidth</option>
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2 border-t">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-[#6B7280] hover:bg-[#F3F4F6] rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 text-xs font-semibold text-white bg-[#2563EB] hover:bg-[#1E40AF] rounded-lg shadow-md disabled:opacity-50"
                >
                  {submitting ? 'Adding…' : 'Add Number'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Assign IVR Flow Modal */}
      {showAssignModal && selectedNumForAssign && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <div>
                <h3 className="text-base font-bold text-[#1F2937]">Assign Published IVR Flow</h3>
                <p className="text-xs text-[#6B7280]">DID: {selectedNumForAssign.phoneNumber}</p>
              </div>
              <button onClick={() => setShowAssignModal(false)}><X className="w-5 h-5 text-[#9CA3AF]" /></button>
            </div>

            {publishedFlows.length === 0 ? (
              <div className="p-4 bg-amber-50 border border-amber-200 text-amber-800 rounded-lg text-xs space-y-2">
                <p className="font-bold">⚠️ No published IVR flows available</p>
                <p>You must build and publish a flow in the <strong>IVR Builder</strong> screen first before assigning it to a phone number.</p>
              </div>
            ) : (
              <form onSubmit={handleAssignIvrSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-[#374151] mb-1">Select Published Flow</label>
                  <select
                    value={selectedFlowId}
                    onChange={(e) => setSelectedFlowId(e.target.value)}
                    className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                  >
                    {publishedFlows.map(f => (
                      <option key={f.flowId} value={f.flowId}>{f.flowName} ({f.businessName}.vxml)</option>
                    ))}
                  </select>
                </div>
                <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-[11px] text-blue-800">
                  ℹ Assigning this flow will trigger <code>add_extension.sh</code> to register extension route <strong>{selectedNumForAssign.phoneNumber.replaceAll(/[^0-9]/g, '') || '1000'}</strong> in Asterisk dialplan.
                </div>
                <div className="flex justify-end gap-2 pt-2 border-t">
                  <button
                    type="button"
                    onClick={() => setShowAssignModal(false)}
                    className="px-4 py-2 text-xs font-semibold text-[#6B7280] hover:bg-[#F3F4F6] rounded-lg"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={submitting || !selectedFlowId}
                    className="px-4 py-2 text-xs font-semibold text-white bg-[#2563EB] hover:bg-[#1E40AF] rounded-lg shadow-md disabled:opacity-50"
                  >
                    {submitting ? 'Provisioning Asterisk…' : 'Provision & Assign'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </TenantLayout>
  )
}
