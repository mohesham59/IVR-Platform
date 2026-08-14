import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, LogOut, Settings as SettingsIcon, User as UserIcon, Shield } from 'lucide-react'
import { backendUrl } from '../api/backendUrl'

interface AccountMenuProps {
  role: 'super_admin' | 'tenant_admin'
  onLogout?: () => void
}

export default function AccountMenu({ role, onLogout }: AccountMenuProps) {
  const [isOpen, setIsOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  const [currentUser, setCurrentUser] = useState<{ username?: string; email?: string; isSuperadmin?: boolean } | null>(() => {
    const saved = localStorage.getItem('nexus_user')
    try {
      return saved ? JSON.parse(saved) : null
    } catch {
      return null
    }
  })

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const token = localStorage.getItem('nexus_jwt_token')
        const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
        let res = await fetch('/api/v1/auth/me', { headers }).catch(() => null)
        if (!res || !res.ok) {
          res = await fetch(backendUrl('/api/v1/auth/me'), { headers })
        }
        if (res && res.ok) {
          const data = await res.json()
          if (data.success && data.user) {
            setCurrentUser(data.user)
            localStorage.setItem('nexus_user', JSON.stringify(data.user))
          }
        }
      } catch (e) {
        console.error('Failed to fetch current user profile', e)
      }
    }
    fetchUser()
  }, [])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleLogoutClick = () => {
    localStorage.removeItem('nexus_jwt_token')
    localStorage.removeItem('nexus_user')
    localStorage.removeItem('tenant_id')
    setIsOpen(false)
    if (onLogout) {
      onLogout()
    }
    navigate('/')
  }

  const defaultName = role === 'super_admin' ? 'Super Admin' : 'Tenant Admin'
  const defaultEmail = role === 'super_admin' ? 'admin@nexusivr.com' : 'tenant@nexusivr.com'
  const displayName = currentUser?.username || defaultName
  const displayEmail = currentUser?.email || defaultEmail
  const settingsPath = role === 'super_admin' ? '/super-admin/settings' : '/tenant/settings'
  const initials = displayName
    .split(' ')
    .map((n) => n[0])
    .join('')
    .substring(0, 2)
    .toUpperCase()

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2.5 pl-2 pr-3 py-1.5 rounded-lg hover:bg-[#F3F4F6] transition-colors cursor-pointer"
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-xs font-bold shadow-xs">
          {initials || 'U'}
        </div>
        <div className="text-left hidden sm:block">
          <div className="text-[#1F2937] text-xs font-semibold leading-tight">{displayName}</div>
          <div className="text-[#9CA3AF] text-[10px]">{role === 'super_admin' ? 'Super Admin' : 'Tenant Admin'}</div>
        </div>
        <ChevronDown className={`w-3.5 h-3.5 text-[#9CA3AF] transition-transform duration-150 ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-60 bg-white rounded-xl border border-[#E5E7EB] shadow-xl z-50 p-1.5 divide-y divide-[#F3F4F6]">
          <div className="px-3 py-2.5">
            <div className="flex items-center gap-2 mb-1">
              <div className="w-6 h-6 rounded-full bg-[#EFF6FF] text-[#2563EB] flex items-center justify-center font-bold text-xs">
                {initials}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-[#1F2937] truncate">{displayName}</p>
                <p className="text-[10px] text-[#9CA3AF] truncate">{displayEmail}</p>
              </div>
            </div>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-[#EFF6FF] text-[#2563EB]">
              <Shield className="w-3 h-3" /> {role === 'super_admin' ? 'Platform Super Admin' : 'Tenant Administrator'}
            </span>
          </div>

          <div className="py-1">
            <button
              onClick={() => {
                setIsOpen(false)
                navigate(settingsPath)
              }}
              className="w-full flex items-center gap-2.5 px-3 py-2 text-xs text-[#374151] hover:bg-[#F9FAFB] hover:text-[#2563EB] rounded-lg transition-colors cursor-pointer"
            >
              <SettingsIcon className="w-3.5 h-3.5 text-[#6B7280]" />
              Account Settings
            </button>
          </div>

          <div className="pt-1">
            <button
              onClick={handleLogoutClick}
              className="w-full flex items-center gap-2.5 px-3 py-2 text-xs text-[#EF4444] hover:bg-[#FEF2F2] rounded-lg transition-colors cursor-pointer font-medium"
            >
              <LogOut className="w-3.5 h-3.5" />
              Sign Out
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
