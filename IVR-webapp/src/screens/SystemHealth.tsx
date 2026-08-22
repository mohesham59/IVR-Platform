import { useState, useEffect } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import {
  Cpu, Database, PhoneCall, Server, Activity, CheckCircle2,
  AlertTriangle, XCircle, RefreshCw, Clock, Layers
} from 'lucide-react'

interface JvmHealth {
  status: string
  uptimeMs: number
  uptimeFormatted: string
  maxMemoryMb: number
  totalMemoryMb: number
  usedMemoryMb: number
  freeMemoryMb: number
  activeThreads: number
  availableProcessors: number
}

interface DbHealth {
  status: string
  activeConnections: number
  idleConnections: number
  totalConnections: number
  threadsAwaitingConnection: number
}

interface AiProviderStatus {
  provider: string
  status: string
  circuitState: string
  cooldownRemainingSeconds: number
  consecutiveFailures: number
  lastFailureReason: string
}

interface AsteriskHealth {
  status: string
  host: string
  port: number
  connected: boolean
}

interface SystemHealthData {
  overallStatus: string
  jvm: JvmHealth
  database: DbHealth
  aiProviders: Record<string, AiProviderStatus>
  asterisk: AsteriskHealth
  timestamp: string
}

export default function SystemHealth({ onLogout }: { onLogout: () => void }) {
  const [data, setData] = useState<SystemHealthData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)

  const fetchHealth = async () => {
    try {
      const headers = { 'X-Is-SuperAdmin': 'true' }
      const res = await fetch('/api/v1/admin/system-health', { headers }).then(r => r.json()).catch(() => null)
      if (res?.success && res.data) {
        setData(res.data)
        setError('')
      } else {
        setError(res?.error || 'Failed to fetch system health')
      }
    } catch (err: any) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchHealth()
    let interval: any = null
    if (autoRefresh) {
      interval = setInterval(fetchHealth, 5000)
    }
    return () => {
      if (interval) clearInterval(interval)
    }
  }, [autoRefresh])

  const getStatusBadge = (status: string) => {
    const s = status.toUpperCase()
    if (s === 'HEALTHY' || s === 'CLOSED') {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#DCFCE7] text-[#15803D]">
          <CheckCircle2 className="w-3.5 h-3.5" /> Healthy
        </span>
      )
    }
    if (s === 'DEGRADED' || s === 'CIRCUIT_OPEN' || s === 'OPEN') {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#FEF9C3] text-[#A16207]">
          <AlertTriangle className="w-3.5 h-3.5" /> Degraded
        </span>
      )
    }
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-[#FEE2E2] text-[#B91C1C]">
        <XCircle className="w-3.5 h-3.5" /> Critical / Offline
      </span>
    )
  }

  const memoryPercent = data ? Math.round((data.jvm.usedMemoryMb / data.jvm.totalMemoryMb) * 100) : 0

  return (
    <SuperAdminLayout
      pageTitle="System Health"
      pageSubtitle="Live JVM Runtime, Database Pool, AI Providers & Telephony Monitor"
      onLogout={onLogout}
      headerActions={
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-xs font-medium text-[#4B5563] cursor-pointer">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="rounded border-[#D1D5DB] text-[#2563EB] focus:ring-[#2563EB]"
            />
            Auto-refresh (5s)
          </label>
          <button
            onClick={fetchHealth}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-white border border-[#E5E7EB] text-[#374151] rounded-lg text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all cursor-pointer shadow-sm"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh Now
          </button>
        </div>
      }
    >
      {loading ? (
        <div className="flex items-center justify-center h-64 text-[#6B7280]">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#2563EB]"></div>
        </div>
      ) : error ? (
        <div className="bg-red-50 text-red-600 p-4 rounded-xl border border-red-200 text-sm">
          {error}
        </div>
      ) : data ? (
        <div className="space-y-6">
          {/* Overall Health Status Banner */}
          <div className={`rounded-xl p-5 border flex items-center justify-between shadow-sm ${
            data.overallStatus === 'HEALTHY'
              ? 'bg-[#F0FDF4] border-[#BBF7D0] text-[#166534]'
              : data.overallStatus === 'DEGRADED'
              ? 'bg-[#FEFCE8] border-[#FEF08A] text-[#854D0E]'
              : 'bg-[#FEF2F2] border-[#FECACA] text-[#991B1B]'
          }`}>
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-white/80 flex items-center justify-center shadow-xs">
                <Activity className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-lg font-bold">System Status: {data.overallStatus}</h3>
                <p className="text-xs opacity-80 mt-0.5">Last checked: {data.timestamp}</p>
              </div>
            </div>
            {getStatusBadge(data.overallStatus)}
          </div>

          {/* Health Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {/* 1. JVM Application Runtime */}
            <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Server className="w-5 h-5 text-[#2563EB]" />
                    <h4 className="font-semibold text-sm text-[#1F2937]">JVM Runtime</h4>
                  </div>
                  {getStatusBadge(data.jvm.status)}
                </div>

                <div className="space-y-3">
                  <div>
                    <div className="flex justify-between text-xs text-[#6B7280] mb-1">
                      <span>Memory Heap</span>
                      <span className="font-semibold text-[#1F2937]">{data.jvm.usedMemoryMb} MB / {data.jvm.totalMemoryMb} MB</span>
                    </div>
                    <div className="w-full bg-[#E5E7EB] h-2 rounded-full overflow-hidden">
                      <div
                        className="bg-[#2563EB] h-full rounded-full transition-all duration-300"
                        style={{ width: `${memoryPercent}%` }}
                      ></div>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs pt-2 border-t border-[#F3F4F6]">
                    <div>
                      <span className="text-[#9CA3AF]">Uptime:</span>
                      <p className="font-medium text-[#1F2937] flex items-center gap-1 mt-0.5">
                        <Clock className="w-3 h-3 text-[#2563EB]" /> {data.jvm.uptimeFormatted}
                      </p>
                    </div>
                    <div>
                      <span className="text-[#9CA3AF]">Active Threads:</span>
                      <p className="font-medium text-[#1F2937] mt-0.5">{data.jvm.activeThreads}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* 2. Database Connection Pool */}
            <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Database className="w-5 h-5 text-[#8B5CF6]" />
                    <h4 className="font-semibold text-sm text-[#1F2937]">PostgreSQL Pool</h4>
                  </div>
                  {getStatusBadge(data.database.status)}
                </div>

                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="bg-[#F9FAFB] p-3 rounded-lg border border-[#F3F4F6]">
                    <span className="text-[#9CA3AF]">Active Connections</span>
                    <p className="text-xl font-bold text-[#8B5CF6] mt-1">{data.database.activeConnections}</p>
                  </div>
                  <div className="bg-[#F9FAFB] p-3 rounded-lg border border-[#F3F4F6]">
                    <span className="text-[#9CA3AF]">Idle Connections</span>
                    <p className="text-xl font-bold text-[#10B981] mt-1">{data.database.idleConnections}</p>
                  </div>
                </div>

                <div className="flex justify-between text-xs text-[#6B7280] mt-3 pt-2 border-t border-[#F3F4F6]">
                  <span>Total Pool Capacity:</span>
                  <span className="font-semibold text-[#1F2937]">{data.database.totalConnections}</span>
                </div>
              </div>
            </div>

            {/* 3. Asterisk AMI Telephony */}
            <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <PhoneCall className="w-5 h-5 text-[#F59E0B]" />
                    <h4 className="font-semibold text-sm text-[#1F2937]">Asterisk Telephony</h4>
                  </div>
                  {getStatusBadge(data.asterisk.status)}
                </div>

                <div className="space-y-2 text-xs">
                  <div className="flex justify-between py-1.5 border-b border-[#F3F4F6]">
                    <span className="text-[#9CA3AF]">AMI Connection State:</span>
                    <span className={`font-semibold ${data.asterisk.connected ? 'text-[#10B981]' : 'text-[#EF4444]'}`}>
                      {data.asterisk.connected ? 'Connected' : 'Disconnected'}
                    </span>
                  </div>
                  <div className="flex justify-between py-1.5 border-b border-[#F3F4F6]">
                    <span className="text-[#9CA3AF]">Target Host:</span>
                    <span className="font-mono text-[#1F2937]">{data.asterisk.host}:{data.asterisk.port}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* 4. AI Provider Circuit Summary */}
            <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Cpu className="w-5 h-5 text-[#EC4899]" />
                    <h4 className="font-semibold text-sm text-[#1F2937]">AI Circuit Overview</h4>
                  </div>
                  <span className="text-xs font-semibold text-[#EC4899]">
                    {Object.keys(data.aiProviders).length} Providers
                  </span>
                </div>

                <div className="space-y-2 text-xs">
                  {Object.entries(data.aiProviders).map(([name, p]) => (
                    <div key={name} className="flex items-center justify-between py-1 border-b border-[#F3F4F6] last:border-0">
                      <span className="capitalize font-medium text-[#374151]">{name}</span>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        p.circuitState === 'CLOSED' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#FEE2E2] text-[#B91C1C]'
                      }`}>
                        {p.circuitState}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* AI Providers Circuit Breakers Detailed Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-[#F3F4F6] flex items-center justify-between">
              <div>
                <h3 className="text-[#1F2937] font-semibold text-sm flex items-center gap-2">
                  <Layers className="w-4 h-4 text-[#2563EB]" /> AI Provider Circuit Breakers & Failover Routing
                </h3>
                <p className="text-xs text-[#9CA3AF] mt-0.5">Monitors API quotas, connection rate limits, and fallback triggers across configured LLM clients</p>
              </div>
            </div>

            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB]">
                  {['Provider', 'Circuit State', 'Health Status', 'Consecutive Failures', 'Cooldown Remaining', 'Last Failure Reason'].map((h) => (
                    <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {Object.entries(data.aiProviders).map(([name, p]) => (
                  <tr key={name} className="hover:bg-[#F9FAFB] transition-colors">
                    <td className="px-5 py-3 font-semibold capitalize text-[#1F2937] flex items-center gap-2">
                      <Cpu className="w-4 h-4 text-[#2563EB]" /> {name}
                    </td>
                    <td className="px-5 py-3 font-mono text-xs">
                      <span className={`px-2 py-0.5 rounded font-bold text-[11px] ${
                        p.circuitState === 'CLOSED' ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#FEE2E2] text-[#B91C1C]'
                      }`}>
                        {p.circuitState}
                      </span>
                    </td>
                    <td className="px-5 py-3">{getStatusBadge(p.status)}</td>
                    <td className="px-5 py-3 text-xs text-[#4B5563] font-medium">{p.consecutiveFailures}</td>
                    <td className="px-5 py-3 text-xs text-[#6B7280]">
                      {p.cooldownRemainingSeconds > 0 ? `${p.cooldownRemainingSeconds}s` : 'None'}
                    </td>
                    <td className="px-5 py-3 text-xs text-[#9CA3AF] max-w-xs truncate">{p.lastFailureReason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </SuperAdminLayout>
  )
}
