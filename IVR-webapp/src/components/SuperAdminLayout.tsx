import { useState, useEffect, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { backendUrl } from '../api/backendUrl'
import NotificationBell from './NotificationBell'
import AccountMenu from './AccountMenu'
import {
  Activity, BarChart3, Building2, ChevronDown, ChevronLeft, ChevronRight,
  CreditCard, LayoutDashboard, LogOut, Moon, Phone, ScrollText, Settings, Users,
} from 'lucide-react'

const navItems = [
  { icon: LayoutDashboard, label: 'Dashboard', path: '/super-admin/dashboard' },
  { icon: Building2, label: 'Companies', path: '/super-admin/companies' },
  { icon: Users, label: 'Users', path: '/super-admin/users' },
  { icon: CreditCard, label: 'Subscriptions', path: '/super-admin/subscriptions' },
  { icon: Activity, label: 'System Health', path: '/super-admin/system-health' },
  { icon: ScrollText, label: 'Audit Logs', path: '/super-admin/audit-logs' },
  { icon: BarChart3, label: 'Reports', path: '/super-admin/reports' },
  { icon: Settings, label: 'Settings', path: '/super-admin/settings' },
]

interface SuperAdminLayoutProps {
  children: ReactNode
  pageTitle: string
  pageSubtitle?: string
  headerActions?: ReactNode
  onLogout: () => void
}

export default function SuperAdminLayout({ children, pageTitle, pageSubtitle, headerActions, onLogout }: SuperAdminLayoutProps) {
  const [collapsed, setCollapsed] = useState(false)
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('theme') === 'dark')

  useEffect(() => {
    if (darkMode) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, [darkMode])

  return (
    <div className="flex h-screen bg-[#F8FAFC] overflow-hidden">
      <aside className={`${collapsed ? 'w-16' : 'w-60'} flex-shrink-0 bg-white border-r border-[#E5E7EB] flex flex-col transition-all duration-200 z-30`}>
        <div className={`flex items-center gap-3 px-4 h-16 border-b border-[#E5E7EB] ${collapsed ? 'justify-center' : ''}`}>
          <div className="w-8 h-8 rounded-lg bg-[#2563EB] flex items-center justify-center flex-shrink-0"><Phone className="w-4 h-4 text-white" /></div>
          {!collapsed && <div><div className="text-[#1F2937] font-bold text-sm leading-tight">NexusIVR</div><div className="text-[#9CA3AF] text-[10px]">Super Admin</div></div>}
        </div>
        <nav className="flex-1 px-2 py-4 space-y-0.5 overflow-y-auto">
          {navItems.map(({ icon: Icon, label, path }) => (
            <NavLink key={path} to={path} className={({ isActive }) => `w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${isActive ? 'bg-[#EFF6FF] text-[#2563EB]' : 'text-[#6B7280] hover:bg-[#F9FAFB] hover:text-[#1F2937]'} ${collapsed ? 'justify-center' : ''}`} title={collapsed ? label : undefined}>
              <Icon className="w-4 h-4" />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="p-3 border-t border-[#E5E7EB]"><button onClick={() => setCollapsed(!collapsed)} className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-[#6B7280] hover:bg-[#F3F4F6] text-sm transition-colors">{collapsed ? <ChevronRight className="w-4 h-4" /> : <><ChevronLeft className="w-4 h-4" /><span>Collapse</span></>}</button></div>
      </aside>
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="h-16 bg-white border-b border-[#E5E7EB] flex items-center px-6 gap-4 flex-shrink-0 z-20">
          <div className="flex items-center gap-2 ml-auto">
            <button onClick={() => setDarkMode(!darkMode)} className={`w-8 h-8 rounded-lg flex items-center justify-center transition-colors ${darkMode ? 'bg-[#1F2937] text-white' : 'text-[#6B7280] hover:bg-[#F3F4F6]'}`}><Moon className="w-4 h-4" /></button>
            <NotificationBell />
            <AccountMenu role="super_admin" onLogout={onLogout} />
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-6 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-[#1F2937] text-xl font-bold">{pageTitle}</h1>
              {pageSubtitle && <p className="text-[#9CA3AF] text-xs mt-0.5">{pageSubtitle}</p>}
            </div>
            {headerActions && <div className="flex items-center gap-3">{headerActions}</div>}
          </div>
          {children}
        </main>
      </div>
    </div>
  )
}
