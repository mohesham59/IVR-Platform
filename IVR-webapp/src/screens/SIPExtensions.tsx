import { useState, useEffect, type ReactElement } from 'react'
import TenantLayout from '../components/TenantLayout'
import { backendUrl } from '../api/backendUrl'
import {
  Plus, Search, MoreHorizontal, X, Server,
  Trash2, RefreshCw, ChevronLeft, ChevronRight,
  PhoneCall, Wifi, WifiOff, Radio, Eye, EyeOff,
  Shield, AlertCircle
} from 'lucide-react'

interface SipExtensionItem {
  id: string
  extensionNumber: string
  displayName: string
  sipPassword?: string
  tlsEnabled: boolean
  registrationStatus: 'Registered' | 'Registering' | 'Offline'
  callStatus: 'Idle' | 'In Call'
  liveChannels: number
  createdAt?: string
}

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

function ExtActionMenu({ onDelete, onClose }: { onDelete: () => void; onClose: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1 overflow-hidden">
      <button
        onClick={() => { onDelete(); onClose(); }}
        className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] text-[#EF4444] transition-colors"
      >
        <Trash2 className="w-3.5 h-3.5" /> Delete Extension
      </button>
    </div>
  )
}

export default function SIPExtensions({ onLogout }: { onLogout: () => void }) {
  const [extensions, setExtensions] = useState<SipExtensionItem[]>([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<SipExtensionItem | null>(null)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [showSecret, setShowSecret] = useState(false)
  const [search, setSearch] = useState('')
  const [regFilter, setRegFilter] = useState('All')

  // Modals state
  const [showAddModal, setShowAddModal] = useState(false)
  const [newExtNum, setNewExtNum] = useState('')
  const [newDisplayName, setNewDisplayName] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newTlsEnabled, setNewTlsEnabled] = useState(false)

  // Status & banner feedback
  const [submitting, setSubmitting] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionSuccess, setActionSuccess] = useState<string | null>(null)

  // Use consistent tenant ID from localStorage, matching seed data and other screens
  const tenantId = localStorage.getItem('tenant_id') || '11111111-1111-1111-1111-111111111111'

  const fetchExtensions = async () => {
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Tenant-ID': tenantId,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      let res = await fetch('/api/v1/tenant/sip-extensions', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/tenant/sip-extensions'), { headers })
      }
      const json = await res.json()
      if (json.success && Array.isArray(json.data)) {
        setExtensions(json.data)
      } else if (Array.isArray(json)) {
        setExtensions(json)
      }
    } catch (e) {
      console.error('Failed to fetch SIP extensions', e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchExtensions()
    const interval = setInterval(fetchExtensions, 5000)
    return () => clearInterval(interval)
  }, [tenantId])

  const handleAddSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newExtNum.trim() || !newDisplayName.trim()) return

    setSubmitting(true)
    setActionError(null)

    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Tenant-ID': tenantId,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      const body = JSON.stringify({
        extensionNumber: newExtNum.trim(),
        displayName: newDisplayName.trim(),
        sipPassword: newPassword.trim() || '1234',
        tlsEnabled: newTlsEnabled
      })
      let res = await fetch('/api/v1/tenant/sip-extensions', { method: 'POST', headers, body }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/tenant/sip-extensions'), { method: 'POST', headers, body })
      }
      const json = await res.json()
      if (json.success || json.data) {
        setActionSuccess(`SIP Extension ${newExtNum} provisioned in Asterisk!`)
        setNewExtNum('')
        setNewDisplayName('')
        setNewPassword('')
        setNewTlsEnabled(false)
        setShowAddModal(false)
        fetchExtensions()
      } else {
        setActionError(json.message || json.error || 'Failed to add extension')
      }
    } catch (err: any) {
      setActionError(err.message || 'Error adding extension')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDeleteExt = async (ext: SipExtensionItem) => {
    if (!confirm(`Are you sure you want to delete extension ${ext.extensionNumber}? This will remove its Asterisk PJSIP configuration.`)) {
      return
    }

    setActionError(null)
    setActionSuccess(null)

    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Tenant-ID': tenantId,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
      let res = await fetch(`/api/v1/tenant/sip-extensions/${ext.id}`, { method: 'DELETE', headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl(`/api/v1/tenant/sip-extensions/${ext.id}`), { method: 'DELETE', headers })
      }
      const json = await res.json()
      if (json.success) {
        setActionSuccess(`Extension ${ext.extensionNumber} removed from Asterisk`)
        if (selected?.id === ext.id) setSelected(null)
        fetchExtensions()
      } else {
        setActionError(json.message || json.error || 'Delete failed')
      }
    } catch (err: any) {
      setActionError(err.message || 'Error deleting extension')
    }
  }

  const registeredCount = extensions.filter(e => e.registrationStatus === 'Registered').length
  const registeringCount = extensions.filter(e => e.registrationStatus === 'Registering').length
  const offlineCount = extensions.filter(e => e.registrationStatus === 'Offline').length
  const busyCount = extensions.filter(e => e.callStatus === 'In Call').length
  const tlsCount = extensions.filter(e => e.tlsEnabled).length

  const filtered = extensions.filter((e) => {
    const statusMatch = regFilter === 'All' || e.registrationStatus === regFilter
    const searchMatch = e.extensionNumber.includes(search) || e.displayName.toLowerCase().includes(search.toLowerCase())
    return statusMatch && searchMatch
  })

  const headerActions = (
    <button
      onClick={() => setShowAddModal(true)}
      className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20"
    >
      <Plus className="w-4 h-4" />
      Add Extension
    </button>
  )

  return (
    <TenantLayout
      activeNav="sip"
      onLogout={onLogout}
      pageTitle="SIP Extensions"
      pageSubtitle="Manage live PJSIP accounts connected to Asterisk"
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

        <div className="flex gap-4">
          {/* Main */}
          <div className="flex-1 min-w-0 space-y-4">
            {/* Stats */}
            <div className="grid grid-cols-4 gap-4">
              {[
                { label: 'Total Extensions', value: extensions.length, icon: <Server className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF' },
                { label: 'Registered', value: registeredCount, icon: <Wifi className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4' },
                { label: 'Registering', value: registeringCount, icon: <Radio className="w-5 h-5" />, color: '#F59E0B', bg: '#FFFBEB' },
                { label: 'Offline', value: offlineCount, icon: <WifiOff className="w-5 h-5" />, color: '#EF4444', bg: '#FEF2F2' },
                { label: 'Busy / In Call', value: busyCount, icon: <PhoneCall className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF' },
                { label: 'TLS Secured', value: tlsCount, icon: <Shield className="w-5 h-5" />, color: '#06B6D4', bg: '#ECFEFF' },
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
                <button
                  onClick={fetchExtensions}
                  title="Refresh live status"
                  className="p-2 rounded-lg border border-[#E5E7EB] hover:bg-[#F3F4F6] text-[#6B7280]"
                >
                  <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                </button>
                <div className="ml-auto text-xs text-[#9CA3AF]">{filtered.length} extensions</div>
              </div>
            </div>

            {/* Table */}
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
              {loading ? (
                <div className="flex items-center justify-center py-12 text-[#9CA3AF] text-sm">
                  <RefreshCw className="w-5 h-5 animate-spin mr-2" />
                  Querying Asterisk PJSIP status…
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                      {['Extension', 'Display Name', 'Transport', 'Registration', 'Call Status', ''].map((h) => (
                        <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#F3F4F6]">
                    {filtered.map((ext) => {
                      const statusKey = ext.registrationStatus || 'Offline'
                      const rc = regConfig[statusKey] || regConfig['Offline']
                      const callKey = ext.callStatus || 'Idle'
                      const cc = callConfig[callKey] || callConfig['Idle']

                      return (
                        <tr
                          key={ext.id}
                          onClick={() => { setSelected(ext); setOpenMenu(null) }}
                          className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer group ${selected?.id === ext.id ? 'bg-[#EFF6FF]' : ''}`}
                        >
                          <td className="px-4 py-3.5">
                            <div className="flex items-center gap-2">
                              <div className={`w-2 h-2 rounded-full flex-shrink-0 ${rc.dotCls}`} />
                              <span className="text-[#1F2937] font-bold text-sm font-mono">{ext.extensionNumber}</span>
                            </div>
                          </td>
                          <td className="px-4 py-3.5 text-[#374151] text-xs font-medium">{ext.displayName}</td>
                          <td className="px-4 py-3.5">
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-[#F3F4F6] text-[#374151]">
                              {ext.tlsEnabled ? 'TLS (Encrypted)' : 'UDP'}
                            </span>
                          </td>
                          <td className="px-4 py-3.5">
                            <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold ${rc.cls}`}>
                              {rc.icon}
                              {ext.registrationStatus}
                            </span>
                          </td>
                          <td className="px-4 py-3.5">
                            <span className={`text-xs font-medium ${cc.cls}`}>{ext.callStatus}</span>
                          </td>
                          <td className="px-4 py-3.5" onClick={(e) => e.stopPropagation()}>
                            <div className="relative">
                              <button
                                onClick={() => setOpenMenu(openMenu === ext.id ? null : ext.id)}
                                className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors"
                              >
                                <MoreHorizontal className="w-4 h-4" />
                              </button>
                              {openMenu === ext.id && (
                                <ExtActionMenu
                                  onDelete={() => handleDeleteExt(ext)}
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

              {!loading && filtered.length === 0 && (
                <div className="flex flex-col items-center justify-center py-16">
                  <div className="w-14 h-14 rounded-2xl bg-[#F3F4F6] flex items-center justify-center mb-4">
                    <Server className="w-7 h-7 text-[#D1D5DB]" />
                  </div>
                  <p className="text-[#374151] font-medium text-sm">No SIP extensions found</p>
                  <p className="text-[#9CA3AF] text-xs mt-1">Add a new PJSIP extension to register softphones</p>
                </div>
              )}
            </div>
          </div>

          {/* Side panel */}
          {selected && (
            <div className="w-72 flex-shrink-0 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col" style={{ maxHeight: 'calc(100vh - 160px)', position: 'sticky', top: 0 }}>
              <div className="flex items-start justify-between p-5 border-b border-[#F3F4F6]">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-[#1F2937] font-bold text-2xl font-mono">{selected.extensionNumber}</span>
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold ${regConfig[selected.registrationStatus || 'Offline'].cls}`}>
                      {regConfig[selected.registrationStatus || 'Offline'].icon}
                      {selected.registrationStatus}
                    </span>
                  </div>
                  <p className="text-[#6B7280] text-xs">{selected.displayName}</p>
                </div>
                <button onClick={() => setSelected(null)} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto p-5 space-y-6">
                <section>
                  <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Extension Details</h4>
                  <div className="space-y-2.5">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[#9CA3AF] text-xs">Transport</span>
                      <span className="text-[#1F2937] text-xs font-medium">{selected.tlsEnabled ? 'TLS' : 'UDP'}</span>
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[#9CA3AF] text-xs">Call Status</span>
                      <span className="text-[#1F2937] text-xs font-medium">{selected.callStatus}</span>
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[#9CA3AF] text-xs">Active Channels</span>
                      <span className="text-[#1F2937] text-xs font-medium font-mono">{selected.liveChannels}</span>
                    </div>

                    <div className="flex items-center justify-between gap-2 border-t pt-2">
                      <span className="text-[#9CA3AF] text-xs">SIP Password</span>
                      <div className="flex items-center gap-1.5">
                        <span className="text-[#1F2937] text-xs font-mono">{showSecret ? (selected.sipPassword || '1234') : '••••••••••••'}</span>
                        <button onClick={() => setShowSecret(!showSecret)} className="text-[#9CA3AF] hover:text-[#374151] transition-colors">
                          {showSecret ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                        </button>
                      </div>
                    </div>
                  </div>
                </section>

                <section className="space-y-2 pt-2 border-t">
                  <button
                    onClick={() => handleDeleteExt(selected)}
                    className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-red-50 border border-red-200 text-[#EF4444] text-xs font-semibold hover:bg-red-100 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" /> Delete Extension
                  </button>
                </section>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Add Extension Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl max-w-md w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="text-base font-bold text-[#1F2937]">Add PJSIP Extension</h3>
              <button onClick={() => setShowAddModal(false)}><X className="w-5 h-5 text-[#9CA3AF]" /></button>
            </div>
            <form onSubmit={handleAddSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#374151] mb-1">Extension Number (3-10 digits)</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. 1005"
                  pattern="[0-9]{3,10}"
                  value={newExtNum}
                  onChange={(e) => setNewExtNum(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB] font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#374151] mb-1">Display Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Sales Support Desk"
                  value={newDisplayName}
                  onChange={(e) => setNewDisplayName(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#374151] mb-1">SIP Password / Secret</label>
                <input
                  type="password"
                  placeholder="e.g. 1234"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full h-9 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB] font-mono"
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="tlsCheckbox"
                  checked={newTlsEnabled}
                  onChange={(e) => setNewTlsEnabled(e.target.checked)}
                  className="w-4 h-4 rounded border-[#D1D5DB] accent-[#2563EB]"
                />
                <label htmlFor="tlsCheckbox" className="text-xs text-[#374151] font-medium cursor-pointer">
                  Enable TLS / SRTP Encryption
                </label>
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
                  {submitting ? 'Provisioning Asterisk…' : 'Create & Provision'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </TenantLayout>
  )
}
