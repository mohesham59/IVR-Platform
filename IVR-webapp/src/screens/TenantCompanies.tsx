import { useEffect, useState } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Search, ChevronDown, ChevronLeft, ChevronRight, Filter, Building2, User, CheckCircle2, Circle, AlertCircle
} from 'lucide-react'

export interface UserTenant {
  id: string
  displayName: string
  ownerUserId: string
  ownerUsername: string
  ownerEmail: string
  status: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

const STATUSES = ['All Status', 'ACTIVE', 'INACTIVE', 'SUSPENDED']
const ITEMS_PER_PAGE = 10

function formatDate(dateStr?: string): string {
  if (!dateStr || dateStr === 'Never' || dateStr === '—') return 'Never'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const day = String(d.getDate()).padStart(2, '0')
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const year = d.getFullYear()
  let hours = d.getHours()
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const ampm = hours >= 12 ? 'PM' : 'AM'
  hours = hours % 12
  hours = hours ? hours : 12
  const strHours = String(hours).padStart(2, '0')
  return `${day}-${month}-${year} ${strHours}:${minutes} ${ampm}`
}

export default function TenantCompanies({ onLogout }: { onLogout: () => void }) {
  const [tenantsList, setTenantsList] = useState<UserTenant[]>([])
  const [selectedTenant, setSelectedTenant] = useState<UserTenant | null>(null)
  const [statusFilter, setStatusFilter] = useState('All Status')
  const [search, setSearch] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [isLoading, setIsLoading] = useState(false)
  const [activeUpdatingId, setActiveUpdatingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const fetchTenants = async () => {
    setIsLoading(true)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers = token ? { Authorization: `Bearer ${token}` } : {}
      let res = await fetch('/api/v1/tenant/companies', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch('http://localhost:8081/nexusivr-ai-engine/api/v1/tenant/companies', { headers })
      }
      const data = await res.json()
      if (data.success && Array.isArray(data.tenants)) {
        setTenantsList(data.tenants)
        if (selectedTenant) {
          const updated = data.tenants.find((t: UserTenant) => t.id === selectedTenant.id)
          if (updated) setSelectedTenant(updated)
        }
      }
    } catch (e) {
      console.error('Failed to fetch user tenants:', e)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchTenants()
  }, [])

  const handleSetActive = async (tenantId: string) => {
    setActiveUpdatingId(tenantId)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (token) headers['Authorization'] = `Bearer ${token}`

      let res = await fetch('/api/v1/tenant/companies', {
        method: 'POST',
        headers,
        body: JSON.stringify({ tenantId })
      }).catch(() => null)

      if (!res || !res.ok) {
        res = await fetch('http://localhost:8081/nexusivr-ai-engine/api/v1/tenant/companies', {
          method: 'POST',
          headers,
          body: JSON.stringify({ tenantId })
        })
      }

      const data = await res.json()
      if (data.success) {
        // Immediately update state locally so UI responds instantly
        setTenantsList(prev =>
          prev.map(t => ({
            ...t,
            isActive: t.id === tenantId
          }))
        )
        if (selectedTenant) {
          setSelectedTenant(prev => prev ? { ...prev, isActive: prev.id === tenantId } : null)
        }
        
        // Dispatch event to update the sidebar layout instantly
        const activeTenant = tenantsList.find(t => t.id === tenantId);
        window.dispatchEvent(new CustomEvent('workspace-updated', { 
          detail: { name: activeTenant ? activeTenant.displayName : 'Unknown Workspace' } 
        }))
        setError(null)
      } else {
        setError(data.error || 'Failed to update active workspace')
      }
    } catch (e) {
      console.error('Failed to set active tenant:', e)
      setError('A network error occurred while setting the active workspace.')
    } finally {
      setActiveUpdatingId(null)
    }
  }

  // Filter & Pagination logic
  const filtered = tenantsList.filter(t => {
    const nameMatch = (t.displayName || '').toLowerCase().includes(search.toLowerCase()) ||
      t.ownerUsername.toLowerCase().includes(search.toLowerCase()) ||
      t.ownerEmail.toLowerCase().includes(search.toLowerCase())
    const statusMatch = statusFilter === 'All Status' || t.status.toUpperCase() === statusFilter.toUpperCase()
    return nameMatch && statusMatch
  })

  const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE) || 1
  const paginated = filtered.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE)

  const activeCount = tenantsList.filter(t => t.isActive).length

  return (
    <TenantLayout onLogout={onLogout}>
      <div className="p-6 space-[#111827]">
        {/* Header */}
        <div className="flex justify-between items-start mb-6">
          <div>
            <h1 className="text-2xl font-bold text-[#1F2937]">Companies</h1>
            <p className="text-sm text-[#6B7280] mt-1">
              View your associated tenants and select your active tenant workspace.
            </p>
          </div>
        </div>

        {error && (
          <div className="mb-6 p-4 rounded-lg bg-[#FEF2F2] border border-[#FCA5A5] text-[#991B1B] text-sm flex items-center gap-2">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <p>{error}</p>
          </div>
        )}

        {/* Quick Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
          <div className="bg-white rounded-xl p-5 border border-[#E5E7EB] shadow-xs flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-[#6B7280] uppercase tracking-wider">Total Associated Tenants</p>
              <h3 className="text-2xl font-bold text-[#111827] mt-1">{tenantsList.length}</h3>
            </div>
            <div className="w-10 h-10 rounded-lg bg-[#EFF6FF] text-[#2563EB] flex items-center justify-center font-semibold">
              <Building2 className="w-5 h-5" />
            </div>
          </div>
          <div className="bg-white rounded-xl p-5 border border-[#E5E7EB] shadow-xs flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-[#6B7280] uppercase tracking-wider">Active Workspace</p>
              <h3 className="text-2xl font-bold text-[#10B981] mt-1">{activeCount ? '1 Active' : 'None Selected'}</h3>
            </div>
            <div className="w-10 h-10 rounded-lg bg-[#ECFDF5] text-[#10B981] flex items-center justify-center font-semibold">
              <CheckCircle2 className="w-5 h-5" />
            </div>
          </div>
        </div>

        {/* Content Area with Flex side panel */}
        <div className="flex gap-6">
          {/* Main Table Container */}
          <div className="flex-1 bg-white rounded-xl border border-[#E5E7EB] shadow-xs overflow-hidden">
            {/* Filters Header */}
            <div className="p-4 border-b border-[#E5E7EB] flex flex-col md:flex-row gap-3 items-center justify-between bg-white">
              <div className="relative flex-1 w-full">
                <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#9CA3AF]" />
                <input
                  type="text"
                  placeholder="Search companies by name or owner..."
                  value={search}
                  onChange={(e) => { setSearch(e.target.value); setCurrentPage(1); }}
                  className="w-full pl-9 pr-4 py-2 bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg text-sm text-[#1F2937] placeholder-[#9CA3AF] focus:outline-none focus:ring-2 focus:ring-[#2563EB] focus:border-transparent transition-all"
                />
              </div>

              <div className="flex items-center gap-3 w-full md:w-auto">
                <div className="relative flex-1 md:flex-none">
                  <select
                    value={statusFilter}
                    onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
                    className="w-full md:w-auto appearance-none pl-9 pr-8 py-2 bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg text-sm text-[#374151] font-medium focus:outline-none focus:ring-2 focus:ring-[#2563EB] cursor-pointer"
                  >
                    {STATUSES.map(s => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                  <Filter className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#9CA3AF] pointer-events-none" />
                  <ChevronDown className="w-4 h-4 absolute right-2.5 top-1/2 -translate-y-1/2 text-[#9CA3AF] pointer-events-none" />
                </div>
              </div>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB] text-xs font-semibold text-[#6B7280] uppercase tracking-wider">
                    <th className="py-3.5 px-4">Company Name</th>
                    <th className="py-3.5 px-4">Workspace Status</th>
                    <th className="py-3.5 px-4">State</th>
                    <th className="py-3.5 px-4">Company Owner</th>
                    <th className="py-3.5 px-4 text-center">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E5E7EB] text-sm text-[#374151]">
                  {isLoading ? (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-[#9CA3AF]">
                        Loading companies...
                      </td>
                    </tr>
                  ) : paginated.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-8 text-center text-[#9CA3AF]">
                        No companies found.
                      </td>
                    </tr>
                  ) : (
                    paginated.map((tenant) => {
                      const isSelected = selectedTenant?.id === tenant.id
                      return (
                        <tr
                          key={tenant.id}
                          onClick={() => setSelectedTenant(tenant)}
                          className={`hover:bg-[#F9FAFB] cursor-pointer transition-colors ${isSelected ? 'bg-[#F0FDF4]' : ''}`}
                        >
                          <td className="py-3.5 px-4 font-semibold text-[#1F2937]">
                            <div className="flex items-center gap-2">
                              <Building2 className="w-4 h-4 text-[#2563EB]" />
                              <span>{tenant.displayName}</span>
                            </div>
                          </td>
                          <td className="py-3.5 px-4">
                            {tenant.isActive ? (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#ECFDF5] text-[#10B981] border border-[#A7F3D0]">
                                <CheckCircle2 className="w-3.5 h-3.5" />
                                Active Workspace
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-[#F3F4F6] text-[#6B7280]">
                                <Circle className="w-3.5 h-3.5" />
                                Available
                              </span>
                            )}
                          </td>
                          <td className="py-3.5 px-4">
                            <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                              tenant.status === 'ACTIVE' ? 'bg-[#ECFDF5] text-[#10B981]' :
                              tenant.status === 'SUSPENDED' ? 'bg-[#FEF2F2] text-[#EF4444]' :
                              'bg-[#F3F4F6] text-[#6B7280]'
                            }`}>
                              {tenant.status}
                            </span>
                          </td>
                          <td className="py-3.5 px-4">
                            <div>
                              <p className="font-medium text-[#1F2937]">{tenant.ownerUsername}</p>
                              <p className="text-xs text-[#9CA3AF]">{tenant.ownerEmail}</p>
                            </div>
                          </td>
                          <td className="py-3.5 px-4 text-center" onClick={(e) => e.stopPropagation()}>
                            {tenant.isActive ? (
                              <button
                                disabled
                                className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-[#ECFDF5] text-[#10B981] border border-[#A7F3D0] cursor-default"
                              >
                                Active
                              </button>
                            ) : (
                              <button
                                onClick={() => handleSetActive(tenant.id)}
                                disabled={activeUpdatingId === tenant.id}
                                className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-[#2563EB] hover:bg-[#1D4ED8] text-white transition-colors disabled:opacity-50"
                              >
                                {activeUpdatingId === tenant.id ? 'Setting...' : 'Set Active'}
                              </button>
                            )}
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="p-4 border-t border-[#E5E7EB] flex items-center justify-between text-xs text-[#6B7280] bg-white">
              <div>
                Showing {filtered.length === 0 ? 0 : (currentPage - 1) * ITEMS_PER_PAGE + 1} to {Math.min(currentPage * ITEMS_PER_PAGE, filtered.length)} of {filtered.length} entries
              </div>
              <div className="flex items-center gap-2">
                <button
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  className="p-1 rounded border border-[#E5E7EB] disabled:opacity-40 hover:bg-[#F3F4F6]"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="font-medium text-[#374151]">Page {currentPage} of {totalPages}</span>
                <button
                  disabled={currentPage === totalPages}
                  onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  className="p-1 rounded border border-[#E5E7EB] disabled:opacity-40 hover:bg-[#F3F4F6]"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          {/* Details Inspector Side Panel */}
          {selectedTenant && (
            <div className="w-80 bg-white rounded-xl border border-[#E5E7EB] shadow-xs p-5 self-start space-y-6">
              <div className="flex items-center justify-between border-b border-[#E5E7EB] pb-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-[#EFF6FF] text-[#2563EB] flex items-center justify-center font-bold">
                    <Building2 className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-[#1F2937] text-sm">{selectedTenant.displayName}</h3>
                  </div>
                </div>
                <button onClick={() => setSelectedTenant(null)} className="text-[#9CA3AF] hover:text-[#374151] p-1 rounded-lg hover:bg-[#F3F4F6]">
                  ✕
                </button>
              </div>

              <div className="space-y-4 text-xs">
                <div>
                  <p className="text-[#9CA3AF] font-medium uppercase tracking-wider text-[10px]">Workspace Status</p>
                  <div className="mt-1">
                    {selectedTenant.isActive ? (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#ECFDF5] text-[#10B981] border border-[#A7F3D0]">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        Active Workspace
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-[#F3F4F6] text-[#6B7280]">
                        <Circle className="w-3.5 h-3.5" />
                        Available Workspace
                      </span>
                    )}
                  </div>
                </div>

                <div>
                  <p className="text-[#9CA3AF] font-medium uppercase tracking-wider text-[10px]">Company Owner</p>
                  <div className="flex items-center gap-2 mt-1 bg-[#F9FAFB] p-2.5 rounded-lg border border-[#E5E7EB]">
                    <User className="w-4 h-4 text-[#6B7280]" />
                    <div>
                      <p className="font-semibold text-[#1F2937]">{selectedTenant.ownerUsername}</p>
                      <p className="text-[#6B7280]">{selectedTenant.ownerEmail}</p>
                    </div>
                  </div>
                </div>

                <div>
                  <p className="text-[#9CA3AF] font-medium uppercase tracking-wider text-[10px]">Account Status</p>
                  <p className="font-medium text-[#374151] mt-0.5">{selectedTenant.status}</p>
                </div>

                <div>
                  <p className="text-[#9CA3AF] font-medium uppercase tracking-wider text-[10px]">Created Date</p>
                  <p className="font-medium text-[#374151] mt-0.5">{formatDate(selectedTenant.createdAt)}</p>
                </div>

                <div>
                  <p className="text-[#9CA3AF] font-medium uppercase tracking-wider text-[10px]">Last Updated</p>
                  <p className="font-medium text-[#374151] mt-0.5">{formatDate(selectedTenant.updatedAt)}</p>
                </div>

                <div className="pt-2">
                  {!selectedTenant.isActive && (
                    <button
                      onClick={() => handleSetActive(selectedTenant.id)}
                      disabled={activeUpdatingId === selectedTenant.id}
                      className="w-full py-2 px-3 text-xs font-semibold rounded-lg bg-[#2563EB] hover:bg-[#1D4ED8] text-white transition-colors disabled:opacity-50"
                    >
                      {activeUpdatingId === selectedTenant.id ? 'Setting Active...' : 'Set As Active Workspace'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </TenantLayout>
  )
}
