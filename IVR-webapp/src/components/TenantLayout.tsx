import { useState, type ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, Users, Phone, Server, List, Volume2, GitBranch,
  Bot, Radio, History, BarChart3, Settings, Bell, Search,
  ChevronDown, LogOut, ChevronLeft, ChevronRight, Layers, Moon,
} from 'lucide-react'

const navItems = [
  { icon: <LayoutDashboard className="w-4 h-4" />, label: 'Dashboard', path: '/tenant/dashboard' },
  { icon: <Users className="w-4 h-4" />, label: 'Users', path: '/tenant/users' },
  { icon: <Phone className="w-4 h-4" />, label: 'Phone Numbers', path: '/tenant/phone-numbers' },
  { icon: <Server className="w-4 h-4" />, label: 'SIP Extensions', path: '/tenant/sip-extensions' },
  { icon: <List className="w-4 h-4" />, label: 'Queues', path: '/tenant/queues' },
  { icon: <Volume2 className="w-4 h-4" />, label: 'Voice Prompts', path: '/tenant/voice-prompts' },
  { icon: <GitBranch className="w-4 h-4" />, label: 'IVR Builder', path: '/tenant/ivr-builder' },
  { icon: <Bot className="w-4 h-4" />, label: 'AI Assistant', path: '/tenant/ai-assistant' },
  { icon: <Radio className="w-4 h-4" />, label: 'Call Monitoring', path: '/tenant/call-monitoring' },
  { icon: <History className="w-4 h-4" />, label: 'Call History', path: '/tenant/call-history' },
  { icon: <BarChart3 className="w-4 h-4" />, label: 'Reports', path: '/tenant/reports' },
  { icon: <Settings className="w-4 h-4" />, label: 'Settings', path: '/tenant/settings' },
]

interface TenantLayoutProps {
  children: ReactNode
  activeNav?: string
  onLogout?: () => void
  pageTitle: string
  pageSubtitle?: string
  headerActions?: ReactNode
  liveCount?: number
}

export default function TenantLayout({
  children,
  activeNav,
  onLogout,
  pageTitle,
  pageSubtitle,
  headerActions,
  liveCount = 18,
}: TenantLayoutProps) {
  const [collapsed, setCollapsed] = useState(false)
  const [darkMode, setDarkMode] = useState(false)

  return (
    <div className="flex h-screen bg-[#F8FAFC] overflow-hidden" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Sidebar */}
      <aside className={`${collapsed ? 'w-16' : 'w-60'} flex-shrink-0 bg-white border-r border-[#E5E7EB] flex flex-col transition-all duration-200 z-30`}>
        {/* Logo */}
        <div className={`flex items-center gap-3 px-4 h-16 border-b border-[#E5E7EB] flex-shrink-0 ${collapsed ? 'justify-center' : ''}`}>
          <div className="w-8 h-8 rounded-lg bg-[#2563EB] flex items-center justify-center flex-shrink-0">
            <Phone className="w-4 h-4 text-white" />
          </div>
          {!collapsed && (
            <div>
              <div className="text-[#1F2937] font-bold text-sm leading-tight">NexusIVR</div>
              <div className="text-[#9CA3AF] text-[10px]">Meridian Health</div>
            </div>
          )}
        </div>

        {/* Tenant badge */}
        {!collapsed && (
          <div className="mx-3 mt-3 mb-1 p-2.5 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE] flex-shrink-0">
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 rounded bg-[#2563EB] flex items-center justify-center flex-shrink-0">
                <Layers className="w-3 h-3 text-white" />
              </div>
              <div className="min-w-0">
                <p className="text-[#2563EB] text-[10px] font-bold truncate">Meridian Health</p>
                <p className="text-[#93C5FD] text-[9px]">Enterprise Plan</p>
              </div>
            </div>
          </div>
        )}

        {/* Nav */}
        <nav data-active-nav={activeNav} className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => `w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
                isActive
                  ? 'bg-[#EFF6FF] text-[#2563EB]'
                  : 'text-[#6B7280] hover:bg-[#F9FAFB] hover:text-[#1F2937]'
              } ${collapsed ? 'justify-center' : ''}`}
              title={collapsed ? item.label : undefined}
            >
              {item.icon}
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          ))}
        </nav>

        {/* Collapse toggle */}
        <div className="p-3 border-t border-[#E5E7EB] flex-shrink-0">
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-[#6B7280] hover:bg-[#F3F4F6] text-sm transition-colors"
          >
            {collapsed ? <ChevronRight className="w-4 h-4" /> : (
              <>
                <ChevronLeft className="w-4 h-4" />
                <span>Collapse</span>
              </>
            )}
          </button>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top bar */}
        <header className="h-16 bg-white border-b border-[#E5E7EB] flex items-center px-6 gap-4 flex-shrink-0 z-20">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
            <input
              placeholder="Search…"
              className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-[#1F2937] text-sm placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
            />
          </div>

          <div className="flex items-center gap-2 ml-auto">
            <div className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 bg-[#F0FDF4] border border-[#BBF7D0] rounded-lg">
              <span className="w-1.5 h-1.5 rounded-full bg-[#22C55E] animate-pulse" />
              <span className="text-[#15803D] text-xs font-semibold">{liveCount} Active Calls</span>
            </div>

            <button
              onClick={() => setDarkMode(!darkMode)}
              className={`w-8 h-8 rounded-lg flex items-center justify-center transition-colors ${darkMode ? 'bg-[#1F2937] text-white' : 'text-[#6B7280] hover:bg-[#F3F4F6]'}`}
            >
              <Moon className="w-4 h-4" />
            </button>

            <button className="relative w-8 h-8 rounded-lg flex items-center justify-center text-[#6B7280] hover:bg-[#F3F4F6] transition-colors">
              <Bell className="w-4 h-4" />
              <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-[#EF4444]" />
            </button>

            <button className="flex items-center gap-2.5 pl-2 pr-3 py-1.5 rounded-lg hover:bg-[#F3F4F6] transition-colors">
              <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-xs font-bold">
                MW
              </div>
              <div className="text-left hidden sm:block">
                <div className="text-[#1F2937] text-xs font-semibold leading-tight">Marcus Webb</div>
                <div className="text-[#9CA3AF] text-[10px]">Tenant Admin</div>
              </div>
              <ChevronDown className="w-3 h-3 text-[#9CA3AF]" />
            </button>

            <button
              onClick={onLogout}
              className="w-8 h-8 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:text-[#EF4444] hover:bg-[#FEF2F2] transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto">
          {/* Page header */}
          <div className="px-6 pt-6 pb-4 flex items-start justify-between gap-4">
            <div>
              <h1 className="text-[#1F2937] text-xl font-bold">{pageTitle}</h1>
              {pageSubtitle && <p className="text-[#6B7280] text-sm mt-0.5">{pageSubtitle}</p>}
            </div>
            {headerActions && <div className="flex items-center gap-2 flex-shrink-0">{headerActions}</div>}
          </div>

          <div className="px-6 pb-6">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}
