import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, Check, Inbox } from 'lucide-react'
import { backendUrl } from '../api/backendUrl'

interface NotificationItem {
  id: string
  tenantId: string | null
  userId: string | null
  message: string
  linkUrl: string | null
  isRead: boolean
  createdAt: string
  type: string
}

export default function NotificationBell() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  const getAuthHeaders = (): Record<string, string> => {
    const token = localStorage.getItem('nexus_jwt_token')
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
    
    const savedUser = localStorage.getItem('nexus_user')
    let isSuperadmin = false
    try {
      if (savedUser) isSuperadmin = JSON.parse(savedUser).isSuperadmin
    } catch (e) {}

    if (isSuperadmin || window.location.pathname.startsWith('/super-admin')) {
      headers['X-Is-SuperAdmin'] = 'true'
    }
    return headers
  }

  const fetchNotifications = async () => {
    try {
      const headers = getAuthHeaders()
      
      const res = await fetch(backendUrl('/api/v1/notifications'), { headers })
      if (res.ok) {
        const data = await res.json()
        if (data.success) {
          const list = Array.isArray(data.data) ? data.data : (Array.isArray(data.notifications) ? data.notifications : [])
          setNotifications(list)
        }
      }
    } catch (e) {
      console.error('Failed to fetch notifications:', e)
    }
  }

  useEffect(() => {
    fetchNotifications()
    // Poll notifications every 5 seconds to keep it live
    const interval = setInterval(fetchNotifications, 5000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const unreadCount = notifications.filter(n => !n.isRead).length

  const handleNotificationClick = async (n: NotificationItem) => {
    setIsOpen(false)
    if (!n.isRead) {
      try {
        const headers = getAuthHeaders()
        
        const res = await fetch(backendUrl(`/api/v1/notifications/${n.id}/read`), {
          method: 'POST',
          headers
        })
        if (res.ok) {
          setNotifications(prev => prev.map(item => item.id === n.id ? { ...item, isRead: true } : item))
        }
      } catch (e) {
        console.error('Failed to mark notification as read:', e)
      }
    }
    if (n.linkUrl) {
      navigate(n.linkUrl)
    }
  }

  const handleMarkAllAsRead = async () => {
    try {
      const headers = getAuthHeaders()
      await fetch(backendUrl('/api/v1/notifications/read-all'), {
        method: 'POST',
        headers
      })
    } catch (e) {
      console.error(e)
    }
    setNotifications(prev => prev.map(item => ({ ...item, isRead: true })))
  }

  return (
    <div className="relative flex items-center" ref={dropdownRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`relative w-8 h-8 rounded-lg flex items-center justify-center text-[#6B7280] hover:bg-[#F3F4F6] hover:text-[#1F2937] transition-all cursor-pointer ${isOpen ? 'bg-[#F3F4F6] text-[#1F2937]' : ''}`}
      >
        <Bell className="w-4 h-4" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 min-w-[14px] h-[14px] px-1 rounded-full bg-[#EF4444] text-white text-[8px] font-extrabold flex items-center justify-center border-2 border-white animate-pulse">
            {unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-80 bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-150">
          <div className="px-4 py-3 bg-slate-50 border-b border-[#E5E7EB] flex items-center justify-between">
            <span className="font-bold text-xs text-slate-800">Notifications</span>
            {unreadCount > 0 && (
              <button
                onClick={handleMarkAllAsRead}
                className="text-[10px] text-blue-600 hover:text-blue-800 font-semibold hover:underline flex items-center gap-1 cursor-pointer"
              >
                <Check className="w-3 h-3" /> Mark all read
              </button>
            )}
          </div>

          <div className="max-h-64 overflow-y-auto divide-y divide-slate-100">
            {notifications.length > 0 ? (
              notifications.map((n) => (
                <div
                  key={n.id}
                  onClick={() => handleNotificationClick(n)}
                  className={`p-3.5 text-xs text-[#4B5563] text-left hover:bg-slate-50 transition-colors cursor-pointer flex gap-2.5 items-start ${!n.isRead ? 'bg-blue-50/40 font-medium' : ''}`}
                >
                  <span className="w-2 h-2 rounded-full bg-blue-600 mt-1.5 flex-shrink-0 opacity-80" style={{ visibility: n.isRead ? 'hidden' : 'visible' }} />
                  <div className="flex-1 space-y-1">
                    <p className="leading-normal text-slate-700 text-[11px]">{n.message}</p>
                    <span className="text-[9px] text-[#9CA3AF] block">
                      {n.createdAt ? new Date(n.createdAt).toLocaleString() : 'Just now'}
                    </span>
                  </div>
                </div>
              ))
            ) : (
              <div className="py-12 flex flex-col items-center justify-center text-[#9CA3AF] space-y-2">
                <Inbox className="w-8 h-8 opacity-40 text-slate-400" />
                <p className="text-[11px]">All caught up!</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
