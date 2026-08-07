import { useEffect, useState } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import { backendUrl } from '../api/backendUrl'
import {
  Plus, Search, ChevronDown, MoreHorizontal,
  Pencil, KeyRound, Trash2, X,
  ChevronLeft, ChevronRight, Filter
} from 'lucide-react'

export interface DbUser {
  id: string
  activeTenantId: string
  email: string
  username: string
  isSuperadmin: boolean
  role: string
  status: string
  lastLoginAt: string
  createdAt: string
}

const ROLES = ['All Roles', 'Super Admin', 'Tenant Admin']
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

export default function SuperAdminUsers({ onLogout }: { onLogout: () => void }) {
  const [usersList, setUsersList] = useState<DbUser[]>([])
  const [selectedUser, setSelectedUser] = useState<DbUser | null>(null)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [roleFilter, setRoleFilter] = useState('All Roles')
  const [statusFilter, setStatusFilter] = useState('All Status')
  const [search, setSearch] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [, setIsLoading] = useState(false)

  // Modal states
  const [modalConfig, setModalConfig] = useState<{
    isOpen: boolean
    mode: 'add' | 'edit' | 'password' | 'delete'
    user?: DbUser | null
  }>({ isOpen: false, mode: 'add' })

  const [formUsername, setFormUsername] = useState('')
  const [formEmail, setFormEmail] = useState('')
  const [formPassword, setFormPassword] = useState('')
  const [formRetypePassword, setFormRetypePassword] = useState('')
  const [formIsSuperAdmin, setFormIsSuperAdmin] = useState(false)
  const [formStatus, setFormStatus] = useState('ACTIVE')

  const fetchUsers = async () => {
    setIsLoading(true)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
      let res = await fetch('/api/v1/super-admin/users', { headers }).catch(() => null)
      if (!res || !res.ok) {
        res = await fetch(backendUrl('/api/v1/super-admin/users'), { headers })
      }
      const data = await res.json()
      if (data.success && Array.isArray(data.users)) {
        setUsersList(data.users)
        if (selectedUser) {
          const updated = data.users.find((u: DbUser) => u.id === selectedUser.id)
          if (updated) setSelectedUser(updated)
        }
      }
    } catch (e) {
      console.error('Failed to fetch users:', e)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchUsers()
  }, [])

  const filtered = usersList.filter((u) => {
    const matchRole = roleFilter === 'All Roles' || (roleFilter === 'Super Admin' ? u.isSuperadmin : !u.isSuperadmin)
    const matchStatus = statusFilter === 'All Status' || u.status === statusFilter
    const matchSearch = u.username.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase())
    return matchRole && matchStatus && matchSearch
  })

  const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE) || 1
  const validCurrentPage = Math.min(currentPage, totalPages)
  const startIndex = (validCurrentPage - 1) * ITEMS_PER_PAGE
  const paginatedUsers = filtered.slice(startIndex, startIndex + ITEMS_PER_PAGE)

  const handleOpenAdd = () => {
    setFormUsername('')
    setFormEmail('')
    setFormPassword('')
    setFormRetypePassword('')
    setFormIsSuperAdmin(false)
    setFormStatus('ACTIVE')
    setModalConfig({ isOpen: true, mode: 'add' })
  }

  const handleOpenEdit = (user: DbUser) => {
    setFormUsername(user.username)
    setFormEmail(user.email)
    setFormIsSuperAdmin(user.isSuperadmin)
    setFormStatus(user.status)
    setModalConfig({ isOpen: true, mode: 'edit', user })
  }

  const handleOpenPassword = (user: DbUser) => {
    setFormPassword('')
    setModalConfig({ isOpen: true, mode: 'password', user })
  }

  const handleOpenDelete = (user: DbUser) => {
    setModalConfig({ isOpen: true, mode: 'delete', user })
  }

  const handleSubmitModal = async () => {
    const token = localStorage.getItem('nexus_jwt_token')
    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }

    try {
      const doFetch = async (url: string, init: RequestInit) => {
        let r = await fetch(url, init).catch(() => null)
        if (!r || !r.ok) {
          const directUrl = url.startsWith('http') ? url : backendUrl(url)
          r = await fetch(directUrl, init)
        }
        return r
      }

      if (modalConfig.mode === 'add') {
        if (!formUsername || !formEmail) return alert('Please enter username and email.')
        if (formPassword !== formRetypePassword) return alert('Passwords do not match. Please retype password correctly.')
        const res = await doFetch('/api/v1/super-admin/users', {
          method: 'POST',
          headers,
          body: JSON.stringify({
            username: formUsername,
            email: formEmail,
            password: formPassword || 'password',
            isSuperadmin: formIsSuperAdmin,
            status: formStatus
          })
        })
        const data = await res.json()
        if (data.success) {
          fetchUsers()
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error creating user')
      } else if (modalConfig.mode === 'edit' && modalConfig.user) {
        const res = await doFetch('/api/v1/super-admin/users', {
          method: 'PUT',
          headers,
          body: JSON.stringify({
            id: modalConfig.user.id,
            username: formUsername,
            email: formEmail,
            isSuperadmin: formIsSuperAdmin,
            status: formStatus
          })
        })
        const data = await res.json()
        if (data.success) {
          fetchUsers()
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error updating user')
      } else if (modalConfig.mode === 'password' && modalConfig.user) {
        if (!formPassword) return alert('Please enter a new password.')
        const res = await doFetch('/api/v1/super-admin/users/reset-password', {
          method: 'POST',
          headers,
          body: JSON.stringify({ id: modalConfig.user.id, newPassword: formPassword })
        })
        const data = await res.json()
        if (data.success) {
          alert('Password updated successfully!')
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error resetting password')
      } else if (modalConfig.mode === 'delete' && modalConfig.user) {
        const res = await doFetch(`/api/v1/super-admin/users?id=${modalConfig.user.id}`, {
          method: 'DELETE',
          headers
        })
        const data = await res.json()
        if (data.success) {
          if (selectedUser?.id === modalConfig.user.id) setSelectedUser(null)
          fetchUsers()
          setModalConfig({ isOpen: false, mode: 'add' })
        } else alert(data.message || 'Error deleting user')
      }
    } catch (e: any) {
      alert('Operation failed: ' + e.message)
    }
  }

  const headerActions = (
    <button onClick={handleOpenAdd} className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
      <Plus className="w-4 h-4" />
      Add User
    </button>
  )

  return (
    <SuperAdminLayout pageTitle="User Management" pageSubtitle={`${filtered.length} total registered accounts`} headerActions={headerActions} onLogout={onLogout}>
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
                  placeholder="Search by username or email…"
                  className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] transition-all"
                />
              </div>

              <div className="flex items-center gap-2">
                <Filter className="w-4 h-4 text-[#9CA3AF]" />
                {[
                  { label: 'Role', value: roleFilter, options: ROLES, setter: setRoleFilter },
                  { label: 'Status', value: statusFilter, options: STATUSES, setter: setStatusFilter }
                ].map((f) => (
                  <div key={f.label} className="relative">
                    <select
                      value={f.value}
                      onChange={(e) => f.setter(e.target.value)}
                      className="h-9 pl-3 pr-8 rounded-lg border border-[#E5E7EB] bg-white text-sm text-[#374151] font-medium outline-none focus:border-[#2563EB] appearance-none cursor-pointer hover:border-[#2563EB] transition-colors"
                    >
                      {f.options.map((o) => <option key={o}>{o}</option>)}
                    </select>
                    <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* User Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                  {['User', 'Role', 'Status', 'Last Login', 'Created', ''].map((h) => (
                    <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {paginatedUsers.map((user) => (
                  <tr
                    key={user.id}
                    onClick={() => { setSelectedUser(user); setOpenMenu(null) }}
                    className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer ${selectedUser?.id === user.id ? 'bg-[#EFF6FF]' : ''}`}
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                          {user.username.slice(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <p className="text-[#1F2937] font-medium text-xs">{user.username}</p>
                          <p className="text-[#9CA3AF] text-[11px]">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-semibold ${user.isSuperadmin ? 'bg-[#EDE9FE] text-[#6D28D9]' : 'bg-[#EFF6FF] text-[#2563EB]'}`}>
                        {user.isSuperadmin ? 'Super Admin' : 'Tenant Admin'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${user.status === 'ACTIVE' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#FEE2E2] text-[#B91C1C]'}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${user.status === 'ACTIVE' ? 'bg-[#22C55E]' : 'bg-[#EF4444]'}`} />
                        {user.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-[#9CA3AF] text-xs">{formatDate(user.lastLoginAt)}</td>
                    <td className="px-4 py-3 text-[#9CA3AF] text-xs">{formatDate(user.createdAt)}</td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <div className="relative">
                        <button
                          onClick={() => setOpenMenu(openMenu === user.id ? null : user.id)}
                          className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors"
                        >
                          <MoreHorizontal className="w-4 h-4" />
                        </button>
                        {openMenu === user.id && (
                          <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1">
                            <button onClick={() => { setOpenMenu(null); handleOpenEdit(user) }} className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium text-[#374151] hover:bg-[#F9FAFB]">
                              <Pencil className="w-3.5 h-3.5" /> Edit User
                            </button>
                            <button onClick={() => { setOpenMenu(null); handleOpenPassword(user) }} className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium text-[#374151] hover:bg-[#F9FAFB]">
                              <KeyRound className="w-3.5 h-3.5" /> Reset Password
                            </button>
                            <button onClick={() => { setOpenMenu(null); handleOpenDelete(user) }} className="w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium text-[#EF4444] hover:bg-[#FEF2F2]">
                              <Trash2 className="w-3.5 h-3.5" /> Delete User
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
                Showing {filtered.length === 0 ? 0 : startIndex + 1} to {Math.min(startIndex + ITEMS_PER_PAGE, filtered.length)} of {filtered.length} users
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

        {/* Side Panel Inspector Modal */}
        {selectedUser && (
          <div className="fixed inset-0 z-40 bg-black/30 backdrop-blur-xs flex justify-end p-4">
            <div className="w-80 bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl p-5 space-y-5 flex flex-col h-full animate-in slide-in-from-right duration-200">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white font-bold text-sm">
                    {selectedUser.username.slice(0, 2).toUpperCase()}
                  </div>
                  <div>
                    <h3 className="font-semibold text-[#1F2937] text-sm">{selectedUser.username}</h3>
                    <p className="text-xs text-[#9CA3AF]">{selectedUser.email}</p>
                  </div>
                </div>
                <button onClick={() => setSelectedUser(null)} className="text-[#9CA3AF] hover:text-[#374151] p-1 rounded-lg hover:bg-[#F3F4F6]">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="space-y-3 border-t border-b border-[#F3F4F6] py-4 text-xs flex-1 overflow-y-auto">
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Role:</span>
                  <span className="font-medium text-[#1F2937]">{selectedUser.isSuperadmin ? 'Super Admin' : 'Tenant Admin'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Status:</span>
                  <span className={`font-semibold ${selectedUser.status === 'ACTIVE' ? 'text-[#166534]' : 'text-[#991B1B]'}`}>{selectedUser.status}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Active Tenant ID:</span>
                  <span className="font-mono text-[10px] text-[#374151]">{selectedUser.activeTenantId || '—'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Last Login:</span>
                  <span className="text-[#374151]">{formatDate(selectedUser.lastLoginAt)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[#6B7280]">Created At:</span>
                  <span className="text-[#374151]">{formatDate(selectedUser.createdAt)}</span>
                </div>
              </div>

              <div className="space-y-2 pt-2 border-t border-[#F3F4F6]">
                <button onClick={() => handleOpenEdit(selectedUser)} className="w-full py-2.5 rounded-xl bg-[#EFF6FF] text-[#2563EB] text-xs font-semibold hover:bg-[#DBEAFE] transition-colors">
                  Edit Details
                </button>
                <button onClick={() => handleOpenPassword(selectedUser)} className="w-full py-2.5 rounded-xl border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] transition-all">
                  Reset Password
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
                {modalConfig.mode === 'add' && 'Create New User'}
                {modalConfig.mode === 'edit' && 'Edit User Account'}
                {modalConfig.mode === 'password' && 'Reset Password'}
                {modalConfig.mode === 'delete' && 'Confirm Deletion'}
              </h3>
              <button onClick={() => setModalConfig({ isOpen: false, mode: 'add' })} className="text-[#9CA3AF] hover:text-[#374151]">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              {modalConfig.mode === 'delete' ? (
                <p className="text-sm text-[#4B5563]">
                  Are you sure you want to permanently delete user <span className="font-semibold text-[#1F2937]">{modalConfig.user?.username}</span>? This action cannot be undone.
                </p>
              ) : modalConfig.mode === 'password' ? (
                <div>
                  <label className="block text-xs font-semibold text-[#374151] mb-1">New Password</label>
                  <input
                    type="password"
                    value={formPassword}
                    onChange={(e) => setFormPassword(e.target.value)}
                    placeholder="Enter new password"
                    className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                  />
                </div>
              ) : modalConfig.mode === 'add' ? (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Username</label>
                    <input
                      value={formUsername}
                      onChange={(e) => setFormUsername(e.target.value)}
                      placeholder="Username"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Email Address</label>
                    <input
                      type="email"
                      value={formEmail}
                      onChange={(e) => setFormEmail(e.target.value)}
                      placeholder="user@nexusivr.com"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Password</label>
                    <input
                      type="password"
                      value={formPassword}
                      onChange={(e) => setFormPassword(e.target.value)}
                      placeholder="Password"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Retype Password</label>
                    <input
                      type="password"
                      value={formRetypePassword}
                      onChange={(e) => setFormRetypePassword(e.target.value)}
                      placeholder="Retype password"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Role</label>
                    <select
                      value={formIsSuperAdmin ? 'Super Admin' : 'Tenant Admin'}
                      onChange={(e) => setFormIsSuperAdmin(e.target.value === 'Super Admin')}
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB] bg-white cursor-pointer"
                    >
                      <option value="Tenant Admin">User (Tenant Admin)</option>
                      <option value="Super Admin">Super Admin</option>
                    </select>
                  </div>
                </>
              ) : (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Username</label>
                    <input
                      value={formUsername}
                      onChange={(e) => setFormUsername(e.target.value)}
                      placeholder="Username"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Email Address</label>
                    <input
                      type="email"
                      value={formEmail}
                      onChange={(e) => setFormEmail(e.target.value)}
                      placeholder="user@nexusivr.com"
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB]"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Account Role</label>
                    <select
                      value={formIsSuperAdmin ? 'Super Admin' : 'Tenant Admin'}
                      onChange={(e) => setFormIsSuperAdmin(e.target.value === 'Super Admin')}
                      className="w-full h-10 px-3 rounded-lg border border-[#E5E7EB] text-sm outline-none focus:border-[#2563EB] bg-white cursor-pointer"
                    >
                      <option value="Tenant Admin">User (Tenant Admin)</option>
                      <option value="Super Admin">Super Admin</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#374151] mb-1">Account Status</label>
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
                onClick={handleSubmitModal}
                className={`px-4 py-2 rounded-lg text-white text-xs font-semibold ${modalConfig.mode === 'delete' ? 'bg-[#EF4444] hover:bg-[#DC2626]' : 'bg-[#2563EB] hover:bg-[#1E40AF]'}`}
              >
                {modalConfig.mode === 'delete' ? 'Delete User' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}
    </SuperAdminLayout>
  )
}
