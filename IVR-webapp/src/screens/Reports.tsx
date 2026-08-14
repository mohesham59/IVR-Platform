import { useState, useEffect } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import {
  FileSpreadsheet, Phone, Cpu, DollarSign, Download, Filter,
  Calendar, Building2, RefreshCw, BarChart2
} from 'lucide-react'

interface TelephonyReportRow {
  tenantId: string
  displayName: string
  totalCalls: number
  totalDurationSeconds: number
  aiCalls: number
  publishedIvrs: number
  aiRequests: number
}

interface BillingReportRow {
  tenantId: string
  displayName: string
  status: string
  totalUsers: number
  assignedDids: number
  llmTurns: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  estimatedBillUsd: string
}

export default function Reports({ onLogout }: { onLogout: () => void }) {
  const [activeTab, setActiveTab] = useState<'telephony' | 'billing'>('telephony')
  const [telephonyData, setTelephonyData] = useState<TelephonyReportRow[]>([])
  const [billingData, setBillingData] = useState<BillingReportRow[]>([])
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const fetchReports = async () => {
    setLoading(true)
    try {
      const headers = { 'X-Is-SuperAdmin': 'true' }
      const params = new URLSearchParams()
      if (dateFrom) params.append('dateFrom', dateFrom)
      if (dateTo) params.append('dateTo', dateTo)

      const endpoint = activeTab === 'telephony' ? '/api/v1/admin/reports/telephony' : '/api/v1/admin/reports/billing'
      const res = await fetch(`${endpoint}?${params.toString()}`, { headers })
        .then(r => r.json())
        .catch(() => null)

      if (res?.success && res.data) {
        if (activeTab === 'telephony') {
          setTelephonyData(res.data)
        } else {
          setBillingData(res.data)
        }
        setError('')
      } else {
        setError(res?.error || 'Failed to fetch report data')
      }
    } catch (err: any) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchReports()
  }, [activeTab, dateFrom, dateTo])

  const handleExportCsv = () => {
    const params = new URLSearchParams()
    if (dateFrom) params.append('dateFrom', dateFrom)
    if (dateTo) params.append('dateTo', dateTo)

    const exportPath = activeTab === 'telephony'
      ? `/api/v1/admin/reports/telephony/export?${params.toString()}`
      : `/api/v1/admin/reports/billing/export?${params.toString()}`

    window.open(exportPath, '_blank')
  }

  const formatDuration = (sec: number) => {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return `${m}m ${s}s`
  }

  return (
    <SuperAdminLayout
      pageTitle="Platform Reports"
      pageSubtitle="Per-Tenant Telephony Usage & Billing Token Consumption Reports"
      onLogout={onLogout}
      headerActions={
        <div className="flex items-center gap-2">
          <button
            onClick={fetchReports}
            className="flex items-center gap-1.5 px-3 py-2 bg-white border border-[#E5E7EB] text-[#374151] rounded-lg text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all cursor-pointer shadow-sm"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh
          </button>
          <button
            onClick={handleExportCsv}
            className="flex items-center gap-2 px-4 py-2 bg-[#2563EB] text-white rounded-lg text-xs font-semibold hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20 cursor-pointer"
          >
            <Download className="w-4 h-4" /> Export {activeTab === 'telephony' ? 'Telephony' : 'Billing'} CSV
          </button>
        </div>
      }
    >
      <div className="space-y-6">
        {/* Navigation Tabs & Date Filter */}
        <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          {/* Tab Selector */}
          <div className="flex p-1 bg-[#F1F5F9] rounded-lg border border-[#E2E8F0]">
            <button
              onClick={() => setActiveTab('telephony')}
              className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-md transition-all cursor-pointer ${
                activeTab === 'telephony'
                  ? 'bg-white text-[#2563EB] shadow-xs'
                  : 'text-[#64748B] hover:text-[#0F172A]'
              }`}
            >
              <Phone className="w-4 h-4" /> Telephony & Call Usage
            </button>
            <button
              onClick={() => setActiveTab('billing')}
              className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-md transition-all cursor-pointer ${
                activeTab === 'billing'
                  ? 'bg-white text-[#2563EB] shadow-xs'
                  : 'text-[#64748B] hover:text-[#0F172A]'
              }`}
            >
              <DollarSign className="w-4 h-4" /> Billing & AI Token Usage
            </button>
          </div>

          {/* Date Range Picker */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <label className="text-xs text-[#6B7280]">From:</label>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
                className="text-xs bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-2.5 py-1.5 text-[#1F2937]"
              />
            </div>
            <div className="flex items-center gap-2">
              <label className="text-xs text-[#6B7280]">To:</label>
              <input
                type="date"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
                className="text-xs bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-2.5 py-1.5 text-[#1F2937]"
              />
            </div>
            {(dateFrom || dateTo) && (
              <button
                onClick={() => { setDateFrom(''); setDateTo(''); }}
                className="text-xs text-[#2563EB] hover:underline cursor-pointer"
              >
                Clear
              </button>
            )}
          </div>
        </div>

        {/* Content Report Card */}
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-[#F3F4F6] flex items-center justify-between">
            <h3 className="text-[#1F2937] font-semibold text-sm flex items-center gap-2">
              <FileSpreadsheet className="w-4 h-4 text-[#2563EB]" />
              {activeTab === 'telephony' ? 'Per-Tenant Telephony & Operations Breakdown' : 'Per-Tenant AI Token & Subscription Billing Report'}
            </h3>
            <span className="text-xs text-[#9CA3AF]">
              {activeTab === 'telephony' ? `${telephonyData.length} Tenants` : `${billingData.length} Tenants`}
            </span>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64 text-[#6B7280]">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#2563EB]"></div>
            </div>
          ) : error ? (
            <div className="p-5 text-center text-red-600 text-sm">{error}</div>
          ) : activeTab === 'telephony' ? (
            /* Telephony Usage Table */
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB]">
                  {['Company / Tenant', 'Total Calls', 'Total Duration', 'AI Handled Calls', 'Published IVRs', 'AI Requests'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {telephonyData.map((row) => (
                  <tr key={row.tenantId} className="hover:bg-[#F9FAFB] transition-colors">
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-[#EFF6FF] flex items-center justify-center">
                          <Building2 className="w-4 h-4 text-[#2563EB]" />
                        </div>
                        <div>
                          <div className="font-semibold text-xs text-[#1F2937]">{row.displayName}</div>
                          <div className="text-[10px] text-[#9CA3AF] font-mono">{row.tenantId}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3.5 font-bold text-xs text-[#1F2937]">{row.totalCalls.toLocaleString()}</td>
                    <td className="px-5 py-3.5 text-xs text-[#6B7280]">{formatDuration(row.totalDurationSeconds)}</td>
                    <td className="px-5 py-3.5 text-xs font-semibold text-[#10B981]">{row.aiCalls.toLocaleString()}</td>
                    <td className="px-5 py-3.5 text-xs text-[#6B7280]">{row.publishedIvrs}</td>
                    <td className="px-5 py-3.5 text-xs font-medium text-[#8B5CF6]">{row.aiRequests.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            /* Billing & Token Usage Table */
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB]">
                  {['Company / Tenant', 'Status', 'Users', 'DIDs', 'LLM Turns', 'Input Tokens', 'Output Tokens', 'Total Tokens', 'Est. Monthly Bill'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {billingData.map((row) => (
                  <tr key={row.tenantId} className="hover:bg-[#F9FAFB] transition-colors">
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-[#F5F3FF] flex items-center justify-center">
                          <Building2 className="w-4 h-4 text-[#8B5CF6]" />
                        </div>
                        <div>
                          <div className="font-semibold text-xs text-[#1F2937]">{row.displayName}</div>
                          <div className="text-[10px] text-[#9CA3AF] font-mono">{row.tenantId}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-[#DCFCE7] text-[#15803D]">
                        {row.status}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-xs text-[#6B7280]">{row.totalUsers}</td>
                    <td className="px-5 py-3.5 text-xs text-[#6B7280]">{row.assignedDids}</td>
                    <td className="px-5 py-3.5 text-xs font-medium text-[#1F2937]">{row.llmTurns.toLocaleString()}</td>
                    <td className="px-5 py-3.5 text-xs text-[#6B7280] font-mono">{row.inputTokens.toLocaleString()}</td>
                    <td className="px-5 py-3.5 text-xs text-[#6B7280] font-mono">{row.outputTokens.toLocaleString()}</td>
                    <td className="px-5 py-3.5 text-xs font-bold text-[#8B5CF6] font-mono">{row.totalTokens.toLocaleString()}</td>
                    <td className="px-5 py-3.5 text-xs font-bold text-[#10B981]">${row.estimatedBillUsd}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </SuperAdminLayout>
  )
}
