import { useState } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Plus, Upload, Download, Search, ChevronDown, MoreHorizontal,
  Eye, Pencil, KeyRound, UserX, Trash2, X, Phone,
  Clock, CheckCircle, XCircle, ChevronLeft, ChevronRight,
  Filter, Mail, Building2, LogIn, PhoneCall,
} from 'lucide-react'

const ROLES = ['All Roles', 'Tenant Admin', 'Supervisor', 'Agent', 'Read-only']
const STATUSES = ['All Status', 'Active', 'Inactive', 'Pending']
const DEPARTMENTS = ['All Departments', 'Support', 'Sales', 'Billing', 'Technical', 'Management']

const users = [
  { id: 1, name: 'Natalie Rodriguez', email: 'natalie@meridian.io', role: 'Supervisor', dept: 'Support', ext: '1001', status: 'Active', lastLogin: '2 min ago', avatar: 'NR', color: 'from-[#2563EB] to-[#7C3AED]', queues: ['Support L1', 'Support L2'], calls: 48 },
  { id: 2, name: 'James Kowalski', email: 'james@meridian.io', role: 'Agent', dept: 'Sales', ext: '1002', status: 'Active', lastLogin: '14 min ago', avatar: 'JK', color: 'from-[#059669] to-[#0891B2]', queues: ['Sales', 'General'], calls: 42 },
  { id: 3, name: 'Priya Nair', email: 'priya@meridian.io', role: 'Agent', dept: 'Support', ext: '1003', status: 'Active', lastLogin: '1h ago', avatar: 'PN', color: 'from-[#D97706] to-[#DC2626]', queues: ['Support L1'], calls: 55 },
  { id: 4, name: 'Tom Brecker', email: 'tom@meridian.io', role: 'Agent', dept: 'Billing', ext: '1004', status: 'Inactive', lastLogin: '3 days ago', avatar: 'TB', color: 'from-[#7C3AED] to-[#DB2777]', queues: ['Billing'], calls: 36 },
  { id: 5, name: 'Sofia Alvarez', email: 'sofia@meridian.io', role: 'Supervisor', dept: 'Sales', ext: '1005', status: 'Active', lastLogin: '5h ago', avatar: 'SA', color: 'from-[#0284C7] to-[#059669]', queues: ['Sales', 'VIP'], calls: 50 },
  { id: 6, name: 'Darius Okafor', email: 'darius@meridian.io', role: 'Agent', dept: 'Technical', ext: '1006', status: 'Pending', lastLogin: 'Never', avatar: 'DO', color: 'from-[#BE185D] to-[#7C3AED]', queues: [], calls: 0 },
  { id: 7, name: 'Lea Fontaine', email: 'lea@meridian.io', role: 'Read-only', dept: 'Management', ext: '—', status: 'Active', lastLogin: '2h ago', avatar: 'LF', color: 'from-[#1E40AF] to-[#0891B2]', queues: [], calls: 0 },
  { id: 8, name: 'Marcus Webb', email: 'marcus@meridian.io', role: 'Tenant Admin', dept: 'Management', ext: '1000', status: 'Active', lastLogin: 'Now', avatar: 'MW', color: 'from-[#2563EB] to-[#7C3AED]', queues: [], calls: 0 },
]

const recentCalls = [
  { number: '+1 (415) 882-3301', duration: '4m 12s', time: '10:24 AM', status: 'Answered' },
  { number: '+1 (312) 445-9921', duration: '8m 04s', time: '09:51 AM', status: 'Answered' },
  { number: '+1 (617) 230-0084', duration: '2m 37s', time: '09:12 AM', status: 'Missed' },
]

const loginHistory = [
  { ip: '192.168.1.42', location: 'New York, US', time: 'Dec 12 — 10:21 AM', device: 'Chrome / macOS' },
  { ip: '192.168.1.42', location: 'New York, US', time: 'Dec 11 — 08:44 AM', device: 'Chrome / macOS' },
  { ip: '10.0.5.23', location: 'Brooklyn, US', time: 'Dec 10 — 09:05 AM', device: 'Safari / iPhone' },
]

const statusStyle: Record<string, string> = {
  Active: 'bg-[#DCFCE7] text-[#15803D]',
  Inactive: 'bg-[#FEE2E2] text-[#B91C1C]',
  Pending: 'bg-[#FEF9C3] text-[#A16207]',
}

const roleStyle: Record<string, string> = {
  'Tenant Admin': 'bg-[#EDE9FE] text-[#6D28D9]',
  Supervisor: 'bg-[#EFF6FF] text-[#2563EB]',
  Agent: 'bg-[#F0FDF4] text-[#15803D]',
  'Read-only': 'bg-[#F3F4F6] text-[#6B7280]',
}

interface ActionMenuProps {
  onClose: () => void
}

function ActionMenu({ onClose }: ActionMenuProps) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-44 py-1 overflow-hidden">
      {[
        { icon: <Eye className="w-3.5 h-3.5" />, label: 'View Profile', color: '' },
        { icon: <Pencil className="w-3.5 h-3.5" />, label: 'Edit User', color: '' },
        { icon: <KeyRound className="w-3.5 h-3.5" />, label: 'Reset Password', color: '' },
        { icon: <UserX className="w-3.5 h-3.5" />, label: 'Deactivate', color: 'text-[#F59E0B]' },
        { icon: <Trash2 className="w-3.5 h-3.5" />, label: 'Delete User', color: 'text-[#EF4444]' },
      ].map((item) => (
        <button
          key={item.label}
          onClick={onClose}
          className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${item.color || 'text-[#374151]'}`}
        >
          {item.icon}
          {item.label}
        </button>
      ))}
    </div>
  )
}

export default function UserManagement({ onLogout }: { onLogout: () => void }) {
  const [selectedUser, setSelectedUser] = useState<typeof users[0] | null>(null)
  const [openMenu, setOpenMenu] = useState<number | null>(null)
  const [roleFilter, setRoleFilter] = useState('All Roles')
  const [statusFilter, setStatusFilter] = useState('All Status')
  const [deptFilter, setDeptFilter] = useState('All Departments')
  const [search, setSearch] = useState('')
  const [activeTab, setActiveTab] = useState('info')

  const filtered = users.filter((u) => {
    const matchRole = roleFilter === 'All Roles' || u.role === roleFilter
    const matchStatus = statusFilter === 'All Status' || u.status === statusFilter
    const matchDept = deptFilter === 'All Departments' || u.dept === deptFilter
    const matchSearch = u.name.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase())
    return matchRole && matchStatus && matchDept && matchSearch
  })

  const headerActions = (
    <>
      <button className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Upload className="w-4 h-4" />
        Import
      </button>
      <button className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Download className="w-4 h-4" />
        Export CSV
      </button>
      <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
        <Plus className="w-4 h-4" />
        Add User
      </button>
    </>
  )

  return (
    <TenantLayout
      activeNav="users"
      onLogout={onLogout}
      pageTitle="User Management"
      pageSubtitle={`${filtered.length} users in Meridian Health`}
      headerActions={headerActions}
    >
      <div className="flex gap-4 h-full">
        {/* Main panel */}
        <div className="flex-1 min-w-0 space-y-4">
          {/* Filters row */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-4">
            <div className="flex items-center gap-3 flex-wrap">
              {/* Search */}
              <div className="relative flex-1 min-w-[200px]">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search by name or email…"
                  className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
                />
              </div>

              <div className="flex items-center gap-2">
                <Filter className="w-4 h-4 text-[#9CA3AF]" />
                {[
                  { label: 'Role', value: roleFilter, options: ROLES, setter: setRoleFilter },
                  { label: 'Status', value: statusFilter, options: STATUSES, setter: setStatusFilter },
                  { label: 'Department', value: deptFilter, options: DEPARTMENTS, setter: setDeptFilter },
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

              <div className="ml-auto text-xs text-[#9CA3AF]">
                {filtered.length} of {users.length} users
              </div>
            </div>
          </div>

          {/* Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                  <th className="w-10 px-4 py-3">
                    <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]" />
                  </th>
                  {['User', 'Role', 'Department', 'Extension', 'Status', 'Last Login', ''].map((h) => (
                    <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {filtered.map((user) => (
                  <tr
                    key={user.id}
                    onClick={() => { setSelectedUser(user); setOpenMenu(null) }}
                    className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer ${selectedUser?.id === user.id ? 'bg-[#EFF6FF]' : ''}`}
                  >
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]" />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className={`w-8 h-8 rounded-full bg-gradient-to-br ${user.color} flex items-center justify-center text-white text-xs font-bold flex-shrink-0`}>
                          {user.avatar}
                        </div>
                        <div>
                          <p className="text-[#1F2937] font-medium text-xs">{user.name}</p>
                          <p className="text-[#9CA3AF] text-[11px]">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-semibold ${roleStyle[user.role]}`}>{user.role}</span>
                    </td>
                    <td className="px-4 py-3 text-[#6B7280] text-xs">{user.dept}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1 bg-[#F3F4F6] rounded-md px-2 py-0.5 text-[#374151] text-xs font-mono">
                        {user.ext}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusStyle[user.status]}`}>
                        {user.status === 'Active' && <span className="w-1.5 h-1.5 rounded-full bg-[#22C55E]" />}
                        {user.status === 'Inactive' && <span className="w-1.5 h-1.5 rounded-full bg-[#EF4444]" />}
                        {user.status === 'Pending' && <span className="w-1.5 h-1.5 rounded-full bg-[#F59E0B]" />}
                        {user.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-[#9CA3AF] text-xs">{user.lastLogin}</td>
                    <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                      <div className="relative">
                        <button
                          onClick={() => setOpenMenu(openMenu === user.id ? null : user.id)}
                          className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] hover:text-[#374151] transition-colors"
                        >
                          <MoreHorizontal className="w-4 h-4" />
                        </button>
                        {openMenu === user.id && <ActionMenu onClose={() => setOpenMenu(null)} />}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination */}
            <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
              <p className="text-[#9CA3AF] text-xs">Showing {filtered.length} of {users.length} users</p>
              <div className="flex items-center gap-1">
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] hover:text-[#2563EB] transition-colors">
                  <ChevronLeft className="w-3.5 h-3.5" />
                </button>
                {[1, 2, 3].map((p) => (
                  <button key={p} className={`w-7 h-7 rounded-lg border text-xs font-medium transition-colors ${p === 1 ? 'bg-[#2563EB] border-[#2563EB] text-white' : 'border-[#E5E7EB] bg-white text-[#6B7280] hover:border-[#2563EB] hover:text-[#2563EB]'}`}>{p}</button>
                ))}
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] hover:border-[#2563EB] hover:text-[#2563EB] transition-colors">
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Side panel */}
        {selectedUser && (
          <div className="w-80 flex-shrink-0 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col" style={{ maxHeight: 'calc(100vh - 160px)', position: 'sticky', top: 0 }}>
            {/* Panel header */}
            <div className="flex items-start justify-between p-5 border-b border-[#F3F4F6]">
              <div className="flex items-center gap-3">
                <div className={`w-11 h-11 rounded-full bg-gradient-to-br ${selectedUser.color} flex items-center justify-center text-white text-sm font-bold flex-shrink-0`}>
                  {selectedUser.avatar}
                </div>
                <div>
                  <p className="text-[#1F2937] font-semibold text-sm">{selectedUser.name}</p>
                  <p className="text-[#9CA3AF] text-[11px] mt-0.5">{selectedUser.email}</p>
                </div>
              </div>
              <button onClick={() => setSelectedUser(null)} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Status row */}
            <div className="px-5 py-3 border-b border-[#F3F4F6] flex items-center gap-2">
              <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold ${statusStyle[selectedUser.status]}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${selectedUser.status === 'Active' ? 'bg-[#22C55E]' : selectedUser.status === 'Inactive' ? 'bg-[#EF4444]' : 'bg-[#F59E0B]'}`} />
                {selectedUser.status}
              </span>
              <span className={`inline-flex px-2.5 py-1 rounded-full text-xs font-semibold ${roleStyle[selectedUser.role]}`}>{selectedUser.role}</span>
            </div>

            {/* Tabs */}
            <div className="flex border-b border-[#F3F4F6] px-2 pt-2">
              {[
                { id: 'info', label: 'Info' },
                { id: 'calls', label: 'Calls' },
                { id: 'logins', label: 'Logins' },
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

            {/* Tab content */}
            <div className="flex-1 overflow-y-auto">
              {activeTab === 'info' && (
                <div className="p-5 space-y-5">
                  {/* Personal info */}
                  <section>
                    <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Personal Information</h4>
                    <div className="space-y-2.5">
                      {[
                        { icon: <Mail className="w-3.5 h-3.5" />, label: 'Email', value: selectedUser.email },
                        { icon: <Building2 className="w-3.5 h-3.5" />, label: 'Department', value: selectedUser.dept },
                        { icon: <Phone className="w-3.5 h-3.5" />, label: 'Extension', value: selectedUser.ext },
                        { icon: <Clock className="w-3.5 h-3.5" />, label: 'Last Login', value: selectedUser.lastLogin },
                      ].map((row) => (
                        <div key={row.label} className="flex items-center gap-3">
                          <div className="w-6 h-6 rounded-md bg-[#F3F4F6] flex items-center justify-center text-[#9CA3AF] flex-shrink-0">{row.icon}</div>
                          <div className="flex-1 min-w-0">
                            <p className="text-[#9CA3AF] text-[10px]">{row.label}</p>
                            <p className="text-[#1F2937] text-xs font-medium truncate">{row.value}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </section>

                  {/* Assigned queues */}
                  <section>
                    <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Assigned Queues</h4>
                    {selectedUser.queues.length > 0 ? (
                      <div className="flex flex-wrap gap-1.5">
                        {selectedUser.queues.map((q) => (
                          <span key={q} className="bg-[#EFF6FF] border border-[#BFDBFE] text-[#2563EB] text-[10px] font-medium px-2.5 py-1 rounded-full">{q}</span>
                        ))}
                      </div>
                    ) : (
                      <p className="text-[#9CA3AF] text-xs italic">No queues assigned</p>
                    )}
                  </section>

                  {/* Permissions */}
                  <section>
                    <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-3">Permissions</h4>
                    <div className="space-y-1.5">
                      {[
                        { label: 'View Reports', granted: true },
                        { label: 'Manage Users', granted: selectedUser.role === 'Tenant Admin' },
                        { label: 'Access IVR Builder', granted: selectedUser.role !== 'Read-only' },
                        { label: 'Export Data', granted: selectedUser.role !== 'Agent' },
                        { label: 'System Settings', granted: selectedUser.role === 'Tenant Admin' },
                      ].map((perm) => (
                        <div key={perm.label} className="flex items-center justify-between">
                          <span className="text-[#374151] text-xs">{perm.label}</span>
                          {perm.granted
                            ? <CheckCircle className="w-3.5 h-3.5 text-[#22C55E]" />
                            : <XCircle className="w-3.5 h-3.5 text-[#D1D5DB]" />}
                        </div>
                      ))}
                    </div>
                  </section>

                  {/* Actions */}
                  <section className="pt-2 space-y-2">
                    <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE] text-[#2563EB] text-xs font-semibold hover:bg-[#DBEAFE] transition-colors">
                      <Pencil className="w-3.5 h-3.5" /> Edit User
                    </button>
                    <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-semibold hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                      <KeyRound className="w-3.5 h-3.5" /> Reset Password
                    </button>
                    <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#FEE2E2] text-[#EF4444] text-xs font-semibold hover:bg-[#FEF2F2] transition-colors">
                      <UserX className="w-3.5 h-3.5" /> Deactivate
                    </button>
                  </section>
                </div>
              )}

              {activeTab === 'calls' && (
                <div className="p-5 space-y-4">
                  <div className="grid grid-cols-2 gap-3">
                    {[
                      { label: 'Calls Today', value: String(selectedUser.calls), icon: <PhoneCall className="w-4 h-4" />, color: '#2563EB', bg: '#EFF6FF' },
                      { label: 'Avg Duration', value: '4m 38s', icon: <Clock className="w-4 h-4" />, color: '#22C55E', bg: '#F0FDF4' },
                    ].map((s) => (
                      <div key={s.label} className="bg-[#F9FAFB] rounded-lg p-3 border border-[#F3F4F6]">
                        <div className="w-7 h-7 rounded-lg flex items-center justify-center mb-2" style={{ background: s.bg, color: s.color }}>{s.icon}</div>
                        <p className="text-[#1F2937] font-bold text-base">{s.value}</p>
                        <p className="text-[#9CA3AF] text-[10px] mt-0.5">{s.label}</p>
                      </div>
                    ))}
                  </div>
                  <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Recent Calls</h4>
                  <div className="space-y-2">
                    {recentCalls.map((c, i) => (
                      <div key={i} className="flex items-center gap-3 p-3 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] transition-colors">
                        <div className={`w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 ${c.status === 'Answered' ? 'bg-[#DCFCE7]' : 'bg-[#FEE2E2]'}`}>
                          <PhoneCall className={`w-3 h-3 ${c.status === 'Answered' ? 'text-[#22C55E]' : 'text-[#EF4444]'}`} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-[#1F2937] text-xs font-mono">{c.number}</p>
                          <p className="text-[#9CA3AF] text-[10px]">{c.time} · {c.duration}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {activeTab === 'logins' && (
                <div className="p-5 space-y-4">
                  <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Login History</h4>
                  <div className="space-y-3">
                    {loginHistory.map((l, i) => (
                      <div key={i} className="p-3 rounded-lg border border-[#F3F4F6] space-y-1.5">
                        <div className="flex items-center gap-2">
                          <LogIn className="w-3.5 h-3.5 text-[#22C55E]" />
                          <span className="text-[#1F2937] text-xs font-medium">{l.location}</span>
                        </div>
                        <p className="text-[#9CA3AF] text-[10px]">IP: {l.ip}</p>
                        <p className="text-[#9CA3AF] text-[10px]">{l.device}</p>
                        <p className="text-[#9CA3AF] text-[10px]">{l.time}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </TenantLayout>
  )
}
