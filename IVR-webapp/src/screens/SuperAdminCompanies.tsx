import { useEffect, useState } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import { backendUrl } from '../api/backendUrl'
import {
  Plus, Search, ChevronDown, MoreHorizontal,
  Pencil, Trash2, X, ChevronLeft, ChevronRight, Filter, Building2
} from 'lucide-react'

export interface DbTenant {
  id: string
  displayName: string
  ownerUserId: string
  ownerUsername: string
  ownerEmail: string
  status: string
  createdAt: string
  updatedAt: string
  subscriptionPlanId?: string
  subscriptionStatus?: string
  subscriptionExpiresAt?: string
  subscriptionPlanName?: string
  subscriptionPlanPrice?: number
  subscriptionPlanInterval?: string
}

export interface UserOption {
  id: string
  username: string
  email: string
}

export interface SubscriptionPlan {
  id: string
  name: string
  pricePiasters: number
  billingInterval: string
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

export default function SuperAdminCompanies({ onLogout }: { onLogout: () => void }) {
  const [tenantsList, setTenantsList] = useState<DbTenant[]>([])
  const [userOptions, setUserOptions] = useState<UserOption[]>([])
  const [plans, setPlans] = useState<SubscriptionPlan[]>([])
  const [selectedTenant, setSelectedTenant] = useState<DbTenant | null>(null)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('All Status')
  const [search, setSearch] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [, setIsLoading] = useState(false)

  // Modal State
  const [modalConfig, setModalConfig] = useState<{
    isOpen: boolean
    mode: 'add' | 'edit' | 'delete'
    tenant?: DbTenant | null
  }>({ isOpen: false, mode: 'add' })

  const [formDisplayName, setFormDisplayName] = useState('')
  const [formOwnerUserId, setFormOwnerUserId] = useState('')
  const [formStatus, setFormStatus] = useState('ACTIVE')
  const [formSubscriptionPlanId, setFormSubscriptionPlanId] = useState('')

  // Searchable Owner Select State
  const [ownerSearch, setOwnerSearch] = useState('')
  const [isOwnerDropdownOpen, setIsOwnerDropdownOpen] = useState(false)
  const [showOverrideConfirm, setShowOverrideConfirm] = useState(false)

  const fetchPlans = async () => {
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
      let res = await fetch('/api/payments/plans', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/payments/plans'), { headers })
      }
      const data = await res.json()
      // API returns a plain array (not {success, plans})
      if (Array.isArray(data)) {
        setPlans(data)
      } else if (data.success && Array.isArray(data.plans)) {
        // Handle wrapped shape defensively
        setPlans(data.plans)
      }
    } catch (e) {
      console.error('Failed to fetch plans:', e)
    }
  }

  const fetchTenants = async () => {
    setIsLoading(true)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
      let res = await fetch('/api/v1/super-admin/companies', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/super-admin/companies'), { headers })
      }
      const data = await res.json()
      if (data.success) {
        if (Array.isArray(data.tenants)) setTenantsList(data.tenants)
        if (Array.isArray(data.userOptions)) {
          setUserOptions(data.userOptions)
        }
        if (selectedTenant) {
          const updated = data.tenants.find((t: DbTenant) => t.id === selectedTenant.id)
          if (updated) setSelectedTenant(updated)
        }
      }
    } catch (e) {
      console.error('Failed to fetch tenants:', e)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchTenants()
    fetchPlans()
  }, [])

  const filtered = tenantsList.filter((t) => {
    const matchStatus = statusFilter === 'All Status' || t.status === statusFilter
    const nameMatch = (t.displayName || '').toLowerCase().includes(search.toLowerCase())
    const ownerMatch = (t.ownerUsername && t.ownerUsername.toLowerCase().includes(search.toLowerCase())) || (t.ownerEmail && t.ownerEmail.toLowerCase().includes(search.toLowerCase()))
    return matchStatus && (nameMatch || ownerMatch)
  })

  const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE) || 1
  const validCurrentPage = Math.min(currentPage, totalPages)
  const startIndex = (validCurrentPage - 1) * ITEMS_PER_PAGE
  const paginatedTenants = filtered.slice(startIndex, startIndex + ITEMS_PER_PAGE)

  const handleOpenAdd = () => {
    setFormDisplayName('')
    const defaultOwner = userOptions.length > 0 ? userOptions[0].id : ''
    setFormOwnerUserId(defaultOwner)
    setOwnerSearch('')
    setFormStatus('ACTIVE')
    setFormSubscriptionPlanId('')
    setIsOwnerDropdownOpen(false)
    fetchPlans() // always refresh plan list when opening modal
    setModalConfig({ isOpen: true, mode: 'add' })
  }

  const handleOpenEdit = (tenant: DbTenant) => {
    setFormDisplayName(tenant.displayName || '')
    setFormOwnerUserId(tenant.ownerUserId || (userOptions.length > 0 ? userOptions[0].id : ''))
    setOwnerSearch('')
    setFormStatus(tenant.status)
    setFormSubscriptionPlanId(tenant.subscriptionPlanId || '')
    setIsOwnerDropdownOpen(false)
    fetchPlans() // always refresh plan list when opening modal
    setModalConfig({ isOpen: true, mode: 'edit', tenant })
  }

  const handleOpenDelete = (tenant: DbTenant) => {
    setModalConfig({ isOpen: true, mode: 'delete', tenant })
  }

  const selectedOwnerUser = userOptions.find(u => u.id === formOwnerUserId)

  const filteredOwnerOptions = userOptions.filter(u =>
    u.username.toLowerCase().includes(ownerSearch.toLowerCase()) ||
    u.email.toLowerCase().includes(ownerSearch.toLowerCase())
  )

  const handleSubmitModal = async (isOverrideConfirmed = false) => {
    const token = localStorage.getItem('nexus_jwt_token')
    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }

    const doFetch = async (url: string, init: RequestInit) => {
      let r = await fetch(url, init).catch(() => null)
      if (!r || !r.ok) {
        const directUrl = url.startsWith('http') ? url : backendUrl(url)
        r = await fetch(directUrl, init)
      }
      return r
    }

    try {
      if (modalConfig.mode === 'add') {
        if (!formDisplayName.trim()) return alert('Please enter company name.')
        if (!formOwnerUserId) return alert('Please select a company owner user.')
        const res = await doFetch('/api/v1/super-admin/companies', {
          method: 'POST',
          headers,
          body: JSON.stringify({
            displayName: formDisplayName,
            ownerUserId: formOwnerUserId,
            status: formStatus,
            subscriptionPlanId: formSubscriptionPlanId
          })
        })
        const data = await res.json()
        if (data.success) {
          fetchTenants()
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error creating company')
      } else if (modalConfig.mode === 'edit' && modalConfig.tenant) {
        if (!formDisplayName.trim()) return alert('Please enter company name.')
        if (!formOwnerUserId) return alert('Please select a company owner user.')

        const oldPlanId = modalConfig.tenant.subscriptionPlanId || ''
        const isPlanChanged = formSubscriptionPlanId !== oldPlanId

        if (isPlanChanged && !isOverrideConfirmed) {
          setShowOverrideConfirm(true)
          return
        }

        const res = await doFetch('/api/v1/super-admin/companies', {
          method: 'PUT',
          headers,
          body: JSON.stringify({
            id: modalConfig.tenant.id,
            displayName: formDisplayName,
            ownerUserId: formOwnerUserId,
            status: formStatus,
            subscriptionPlanId: formSubscriptionPlanId
          })
        })
        const data = await res.json()
        if (data.success) {
          fetchTenants()
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error updating company')
      } else if (modalConfig.mode === 'delete' && modalConfig.tenant) {
        const res = await doFetch(`/api/v1/super-admin/companies?id=${modalConfig.tenant.id}`, {
          method: 'DELETE',
          headers
        })
        const data = await res.json()
        if (data.success) {
          if (selectedTenant?.id === modalConfig.tenant.id) setSelectedTenant(null)
          fetchTenants()
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error deleting company')
      }
    } catch (e: any) {
      alert('Operation failed: ' + e.message)
    }
  }

  const headerActions = (
    <button onClick={handleOpenAdd} className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
      <Plus className="w-4 h-4" />
      Add Company
    </button>
  )

  return (
    <SuperAdminLayout pageTitle="Companies & Tenants" pageSubtitle={`${filtered.length} total registered enterprise tenants`} headerActions={headerActions} onLogout={onLogout}>
      <div className="flex gap-4 h-full">
        {/* Main table section */}
        <div className="flex-1 min-w-0 space-y-4">
          {/* Filters */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-4">
            <div className="flex items-center gap-3 flex-wrap">
              <div className="relative flex-1 min-w-[200px]">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search by company name or owner…"
                  className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] transition-all"
                />
              </div>

              <div className="flex items-center gap-2">
                <Filter className="w-4 h-4 text-[#9CA3AF]" />
                <div className="relative">
                  <select
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    className="h-9 pl-3 pr-8 rounded-lg border border-[#E5E7EB] bg-white text-sm text-[#374151] font-medium outline-none focus:border-[#2563EB] appearance-none cursor-pointer hover:border-[#2563EB] transition-colors"
                  >
                    {STATUSES.map((o) => <option key={o}>{o}</option>)}
                  </select>
                  <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
                </div>
              </div>
            </div>
          </div>

          {/* Tenants Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                  {['Company', 'Owner', 'Subscription', 'Status', 'Created', ''].map((h) => (
                    <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {paginatedTenants.map((tenant) => (
                  <tr
                    key={tenant.id}
                    onClick={() => { setSelectedTenant(tenant); setOpenMenu(null) }}
                    className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer ${selectedTenant?.id === tenant.id ? 'bg-[#EFF6FF]' : ''}`}
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-[#EFF6FF] text-[#2563EB] flex items-center justify-center text-xs font-bold flex-shrink-0">
                          <Building2 className="w-4 h-4" />
                        </div>
                        <div>
                          <p className="text-[#1F2937] font-semibold text-xs">{tenant.displayName}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div>
                        <p className="text-[#1F2937] font-medium text-xs">{tenant.ownerUsername}</p>
                        <p className="text-[#9CA3AF] text-[11px]">{tenant.ownerEmail}</p>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div>
                        <p className="text-[#1F2937] font-semibold text-xs">
                          {tenant.subscriptionPlanName || 'None'}
                        </p>
                        {tenant.subscriptionPlanPrice !== undefined && tenant.subscriptionPlanPrice !== null && (
                          <p className="text-[#9CA3AF] text-[10px]">
                            {tenant.subscriptionPlanPrice / 100} EGP / {tenant.subscriptionPlanInterval ? tenant.subscriptionPlanInterval.toLowerCase() : ''}
                          </p>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${tenant.status === 'ACTIVE' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#FEE2E2] text-[#B91C1C]'}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${tenant.status === 'ACTIVE' ? 'bg-[#22C55E]' : 'bg-[#EF4444]'}`} />
                        {tenant.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-[#9CA3AF] text-xs">{formatDate(tenant.createdAt)}</td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <div className="relative">
                        <button
                          onClick={() => setOpenMenu(openMenu === tenant.id ? null : tenant.id)}
                          className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors"
                        >
                          <MoreHorizontal className="w-4 h-4" />
                        </button>
                        {openMenu === tenant.id && (
                          <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1">
                            <button onClick={() => { setOpenMenu(null); handleOpenEdit(tenant) }} className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium text-[#374151] hover:bg-[#F9FAFB]">
                              <Pencil className="w-3.5 h-3.5" /> Edit Company
                            </button>
                            <button onClick={() => { setOpenMenu(null); handleOpenDelete(tenant) }} className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium text-[#EF4444] hover:bg-[#FEF2F2]">
                              <Trash2 className="w-3.5 h-3.5" /> Delete Company
                            </button>
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination */}
            <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
              <p className="text-[#9CA3AF] text-xs">
                Showing {filtered.length === 0 ? 0 : startIndex + 1} to {Math.min(startIndex + ITEMS_PER_PAGE, filtered.length)} of {filtered.length} companies
              </p>
              <div className="flex items-center gap-1">
                <button onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={validCurrentPage === 1}
                  className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] disabled:opacity-40 hover:bg-[#F9FAFB]">
                  <ChevronLeft className="w-3.5 h-3.5" />
                </button>
                {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                  <button key={page} onClick={() => setCurrentPage(page)}
                    className={`w-7 h-7 rounded-lg border text-xs font-medium ${validCurrentPage === page ? 'bg-[#2563EB] border-[#2563EB] text-white' : 'bg-white border-[#E5E7EB] text-[#374151] hover:bg-[#F9FAFB]'}`}>
                    {page}
                  </button>
                ))}
                <button onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={validCurrentPage === totalPages}
                  className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] disabled:opacity-40 hover:bg-[#F9FAFB]">
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Company Details Inspector Modal */}
        {selectedTenant && (
          <div className="fixed inset-0 z-40 bg-black/30 backdrop-blur-xs flex justify-end p-4">
            <div className="w-80 bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl p-5 space-y-5 flex flex-col h-full animate-in slide-in-from-right duration-200">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-[#EFF6FF] text-[#2563EB] flex items-center justify-center text-lg font-bold flex-shrink-0">
                    <Building2 className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-[#1F2937] text-sm">{selectedTenant.displayName}</h3>
                  </div>
                </div>
                <button onClick={() => setSelectedTenant(null)} className="text-[#9CA3AF] hover:text-[#374151] p-1 rounded-lg hover:bg-[#F3F4F6]">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="space-y-4 border-t border-b border-[#F3F4F6] py-4 text-xs flex-1 overflow-y-auto">
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Status:</span>
                  <span className={`font-semibold ${selectedTenant.status === 'ACTIVE' ? 'text-[#166534]' : 'text-[#991B1B]'}`}>{selectedTenant.status}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Owner Username:</span>
                  <span className="font-medium text-[#1F2937]">{selectedTenant.ownerUsername}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Owner Email:</span>
                  <span className="text-[#374151]">{selectedTenant.ownerEmail}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Subscription Plan:</span>
                  <span className="font-semibold text-[#1F2937]">{selectedTenant.subscriptionPlanName || 'None'}</span>
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-[#F3F4F6]">
                  <span className="text-[#6B7280]">Created At:</span>
                  <span className="text-[#374151]">{formatDate(selectedTenant.createdAt)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Last Updated:</span>
                  <span className="text-[#374151]">{formatDate(selectedTenant.updatedAt)}</span>
                </div>
              </div>

              <div className="pt-2 border-t border-[#F3F4F6]">
                <button onClick={() => handleOpenEdit(selectedTenant)} className="w-full py-2.5 rounded-xl bg-[#EFF6FF] text-[#2563EB] text-xs font-semibold hover:bg-[#DBEAFE] transition-colors">
                  Edit Company Details
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Modal Dialog */}
      {modalConfig.isOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl w-full max-w-md overflow-hidden">
            <div className="px-6 py-4 border-b border-[#F3F4F6] flex items-center justify-between">
              <h3 className="font-bold text-[#1F2937] text-base">
                {modalConfig.mode === 'add' && 'Create New Company / Tenant'}
                {modalConfig.mode === 'edit' && 'Edit Company Details'}
                {modalConfig.mode === 'delete' && 'Confirm Deletion'}
              </h3>
              <button onClick={() => setModalConfig({ isOpen: false, mode: 'add' })} className="text-[#9CA3AF] hover:text-[#374151]">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
              {modalConfig.mode === 'delete' ? (
                <p className="text-sm text-[#4B5563]">
                  Are you sure you want to permanently delete company <span className="font-semibold text-[#1F2937]">{modalConfig.tenant?.displayName}</span>?
                </p>
              ) : (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Company Display Name</label>
                    <input
                      value={formDisplayName}
                      onChange={(e) => {
                        setFormDisplayName(e.target.value)
                      }}
                      placeholder="e.g. TEST Corp"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>

                  {/* Searchable Owner User Dropdown */}
                  <div className="relative">
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Company Owner User</label>
                    <div
                      onClick={() => setIsOwnerDropdownOpen(!isOwnerDropdownOpen)}
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm flex items-center justify-between bg-white cursor-pointer hover:border-[#2563EB] transition-colors"
                    >
                      {selectedOwnerUser ? (
                        <span className="text-[#1F2937] font-medium">{selectedOwnerUser.username} <span className="text-[#9CA3AF] font-normal text-xs">({selectedOwnerUser.email})</span></span>
                      ) : (
                        <span className="text-[#9CA3AF]">Select Owner User…</span>
                      )}
                      <ChevronDown className="w-4 h-4 text-[#9CA3AF]" />
                    </div>

                    {isOwnerDropdownOpen && (
                      <div className="absolute left-0 right-0 top-full mt-1 bg-white rounded-xl border border-[#E5E7EB] shadow-xl z-50 p-2 space-y-2 max-h-56 overflow-hidden flex flex-col">
                        <div className="relative">
                          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF]" />
                          <input
                            value={ownerSearch}
                            onChange={(e) => setOwnerSearch(e.target.value)}
                            placeholder="Search user by name or email…"
                            className="w-full h-8 pl-8 pr-3 rounded-lg border border-[#E5E7EB] text-xs outline-none focus:border-[#2563EB]"
                            autoFocus
                          />
                        </div>
                        <div className="overflow-y-auto flex-1 divide-y divide-[#F3F4F6]">
                          {filteredOwnerOptions.length > 0 ? (
                            filteredOwnerOptions.map(u => (
                              <button
                                key={u.id}
                                onClick={() => {
                                  setFormOwnerUserId(u.id)
                                  setIsOwnerDropdownOpen(false)
                                }}
                                className={`w-full text-left px-3 py-2 text-xs flex items-center justify-between hover:bg-[#F9FAFB] transition-colors ${formOwnerUserId === u.id ? 'bg-[#EFF6FF] text-[#2563EB] font-semibold' : 'text-[#374151]'}`}
                              >
                                <div>
                                  <p className="font-medium text-xs">{u.username}</p>
                                  <p className="text-[10px] text-[#9CA3AF]">{u.email}</p>
                                </div>
                                {formOwnerUserId === u.id && <span className="text-[10px] text-[#2563EB] font-bold">Selected</span>}
                              </button>
                            ))
                          ) : (
                            <p className="text-xs text-[#9CA3AF] p-2 italic text-center">No matching users found</p>
                          )}
                        </div>
                      </div>
                    )}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Subscription Plan</label>
                    <select
                      value={formSubscriptionPlanId}
                      onChange={(e) => setFormSubscriptionPlanId(e.target.value)}
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB] bg-white cursor-pointer"
                    >
                      <option value="">No Plan (None)</option>
                      {plans.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name} ({(p.pricePiasters / 100).toLocaleString()} EGP / {p.billingInterval.toLowerCase()})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Status</label>
                    <select
                      value={formStatus}
                      onChange={(e) => setFormStatus(e.target.value)}
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB] bg-white cursor-pointer"
                    >
                      <option value="ACTIVE">ACTIVE</option>
                      <option value="INACTIVE">INACTIVE</option>
                      <option value="SUSPENDED">SUSPENDED</option>
                    </select>
                  </div>
                </>
              )}
            </div>

            <div className="px-6 py-4 border-t border-[#F3F4F6] bg-[#F9FAFB] flex items-center justify-end gap-3">
              <button onClick={() => setModalConfig({ isOpen: false, mode: 'add' })} className="px-4 py-2 rounded-lg border border-[#E5E7EB] text-xs font-medium text-[#374151] hover:bg-white">
                Cancel
              </button>
              <button
                onClick={() => handleSubmitModal(false)}
                className={`px-4 py-2 rounded-lg text-white text-xs font-semibold ${modalConfig.mode === 'delete' ? 'bg-[#EF4444] hover:bg-[#DC2626]' : 'bg-[#2563EB] hover:bg-[#1E40AF]'}`}
              >
                {modalConfig.mode === 'delete' ? 'Delete Company' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}

      {showOverrideConfirm && modalConfig.tenant && (
        <div className="fixed inset-0 z-55 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-amber-200 shadow-2xl w-full max-w-sm overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 bg-amber-50 border-b border-amber-100 flex items-center gap-3 text-amber-800">
              <span className="w-8 h-8 rounded-lg bg-amber-100 flex items-center justify-center font-bold text-base flex-shrink-0">⚠️</span>
              <h3 className="font-bold text-sm">Confirm Plan Override</h3>
            </div>
            <div className="p-6 text-xs text-[#4B5563] space-y-3 leading-relaxed">
              <p>
                This will change <strong className="text-slate-900">{modalConfig.tenant.displayName}</strong>'s subscription plan from:
              </p>
              <div className="bg-slate-50 border border-slate-100 rounded-xl p-3 space-y-1">
                <p className="text-[11px]"><span className="text-[#6B7280]">From:</span> <strong className="text-slate-800">{modalConfig.tenant.subscriptionPlanName || 'No Plan (None)'}</strong></p>
                <p className="text-[11px]"><span className="text-[#6B7280]">To:</span> <strong className="text-blue-600">{plans.find(p => p.id === formSubscriptionPlanId)?.name || 'No Plan (None)'}</strong></p>
              </div>
              <p className="text-[#CA8A04] font-medium">
                The tenant admin will be notified of this manual subscription override.
              </p>
            </div>
            <div className="px-6 py-4 border-t border-[#F3F4F6] bg-[#F9FAFB] flex items-center justify-end gap-2.5">
              <button 
                onClick={() => setShowOverrideConfirm(false)}
                className="px-3.5 py-2 rounded-lg border border-[#E5E7EB] text-xs font-semibold text-[#4B5563] bg-white hover:bg-slate-50 transition-all cursor-pointer"
              >
                Cancel
              </button>
              <button 
                onClick={() => {
                  setShowOverrideConfirm(false);
                  handleSubmitModal(true);
                }}
                className="px-4 py-2 rounded-lg bg-[#D97706] hover:bg-[#B45309] text-white text-xs font-semibold shadow-sm transition-all cursor-pointer"
              >
                Confirm Override
              </button>
            </div>
          </div>
        </div>
      )}
    </SuperAdminLayout>
  )
}
