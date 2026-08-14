import { useState, useEffect } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import {
  ShieldAlert, Search, Filter, ChevronLeft, ChevronRight,
  User, Calendar, Layers, Eye, RefreshCw, Code
} from 'lucide-react'

interface AuditLogRecord {
  id: string
  tenantId?: string
  actorUserId?: string
  actorEmail: string
  actionType: string
  targetEntityType: string
  targetEntityId: string
  details: string
  ipAddress: string
  createdAt: string
}

const ACTION_TYPES = [
  'ALL',
  'COMPANY_CREATED',
  'USER_LOGIN_SUCCESS',
  'USER_LOGIN_FAILED',
  'IVR_PUBLISHED',
  'SUBSCRIPTION_CHANGED',
  'ROLE_CHANGED'
]

export default function AuditLogs({ onLogout }: { onLogout: () => void }) {
  const [logs, setLogs] = useState<AuditLogRecord[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [totalPages, setTotalPages] = useState(1)
  const [actionTypeFilter, setActionTypeFilter] = useState('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedDetails, setSelectedDetails] = useState<string | null>(null)

  const fetchAuditLogs = async () => {
    setLoading(true)
    try {
      const headers = { 'X-Is-SuperAdmin': 'true' }
      const params = new URLSearchParams({
        page: page.toString(),
        pageSize: pageSize.toString(),
      })
      if (actionTypeFilter !== 'ALL') params.append('actionType', actionTypeFilter)
      if (dateFrom) params.append('dateFrom', dateFrom)
      if (dateTo) params.append('dateTo', dateTo)

      const res = await fetch(`/api/v1/admin/audit-logs?${params.toString()}`, { headers })
        .then(r => r.json())
        .catch(() => null)

      if (res?.success && res.data) {
        setLogs(res.data.items || [])
        setTotal(res.data.total || 0)
        setTotalPages(res.data.totalPages || 1)
        setError('')
      } else {
        setError(res?.error || 'Failed to fetch audit logs')
      }
    } catch (err: any) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchAuditLogs()
  }, [page, pageSize, actionTypeFilter, dateFrom, dateTo])

  const getActionBadge = (type: string) => {
    if (type.includes('SUCCESS') || type.includes('CREATED') || type.includes('PUBLISHED')) {
      return <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-[#DCFCE7] text-[#15803D]">{type}</span>
    }
    if (type.includes('FAILED') || type.includes('REJECTED')) {
      return <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-[#FEE2E2] text-[#B91C1C]">{type}</span>
    }
    return <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-[#EFF6FF] text-[#1D4ED8]">{type}</span>
  }

  return (
    <SuperAdminLayout
      pageTitle="Audit Logs"
      pageSubtitle="Security & Compliance Event Audit Trail Across All Tenants"
      onLogout={onLogout}
      headerActions={
        <button
          onClick={fetchAuditLogs}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-white border border-[#E5E7EB] text-[#374151] rounded-lg text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all cursor-pointer shadow-sm"
        >
          <RefreshCw className="w-3.5 h-3.5" /> Refresh
        </button>
      }
    >
      <div className="space-y-6">
        {/* Filters Card */}
        <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex flex-col md:flex-row gap-4 justify-between items-end">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 flex-1">
            {/* Action Type Filter */}
            <div>
              <label className="block text-xs font-medium text-[#4B5563] mb-1">Action Type</label>
              <select
                value={actionTypeFilter}
                onChange={(e) => { setActionTypeFilter(e.target.value); setPage(1); }}
                className="w-full text-xs bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-3 py-2 text-[#1F2937] focus:outline-none focus:border-[#2563EB]"
              >
                {ACTION_TYPES.map(t => (
                  <option key={t} value={t}>{t === 'ALL' ? 'All Action Types' : t}</option>
                ))}
              </select>
            </div>

            {/* Date From */}
            <div>
              <label className="block text-xs font-medium text-[#4B5563] mb-1">Date From</label>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => { setDateFrom(e.target.value); setPage(1); }}
                className="w-full text-xs bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-3 py-2 text-[#1F2937] focus:outline-none focus:border-[#2563EB]"
              />
            </div>

            {/* Date To */}
            <div>
              <label className="block text-xs font-medium text-[#4B5563] mb-1">Date To</label>
              <input
                type="date"
                value={dateTo}
                onChange={(e) => { setDateTo(e.target.value); setPage(1); }}
                className="w-full text-xs bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-3 py-2 text-[#1F2937] focus:outline-none focus:border-[#2563EB]"
              />
            </div>
          </div>

          {(actionTypeFilter !== 'ALL' || dateFrom || dateTo) && (
            <button
              onClick={() => { setActionTypeFilter('ALL'); setDateFrom(''); setDateTo(''); setPage(1); }}
              className="text-xs text-[#2563EB] font-medium hover:underline pb-2 cursor-pointer"
            >
              Reset Filters
            </button>
          )}
        </div>

        {/* Audit Logs Table */}
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-[#F3F4F6] flex items-center justify-between">
            <h3 className="text-[#1F2937] font-semibold text-sm flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-[#2563EB]" /> Audit Events ({total})
            </h3>
            <span className="text-xs text-[#9CA3AF]">Showing page {page} of {totalPages}</span>
          </div>

          {loading ? (
            <div className="flex items-center justify-center h-64 text-[#6B7280]">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#2563EB]"></div>
            </div>
          ) : error ? (
            <div className="p-5 text-center text-red-600 text-sm">{error}</div>
          ) : logs.length === 0 ? (
            <div className="p-12 text-center text-[#9CA3AF] text-sm">
              No audit log entries found matching the filter criteria.
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB]">
                  {['Timestamp', 'Actor Email', 'Action Type', 'Target Entity', 'IP Address', 'Details'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {logs.map((log) => (
                  <tr key={log.id} className="hover:bg-[#F9FAFB] transition-colors">
                    <td className="px-5 py-3 text-xs text-[#6B7280] font-mono whitespace-nowrap">
                      {new Date(log.createdAt).toLocaleString()}
                    </td>
                    <td className="px-5 py-3 text-xs font-medium text-[#1F2937]">
                      {log.actorEmail || 'System'}
                    </td>
                    <td className="px-5 py-3">{getActionBadge(log.actionType)}</td>
                    <td className="px-5 py-3 text-xs text-[#4B5563]">
                      <span className="font-semibold text-[#1F2937]">{log.targetEntityType || 'N/A'}</span>
                      {log.targetEntityId && <span className="text-[#9CA3AF] ml-1">({log.targetEntityId})</span>}
                    </td>
                    <td className="px-5 py-3 text-xs font-mono text-[#6B7280]">{log.ipAddress || '127.0.0.1'}</td>
                    <td className="px-5 py-3">
                      <button
                        onClick={() => setSelectedDetails(log.details)}
                        className="flex items-center gap-1 text-xs text-[#2563EB] hover:underline cursor-pointer"
                      >
                        <Eye className="w-3.5 h-3.5" /> View JSON
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {/* Pagination Footer */}
          <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] text-xs text-[#6B7280]">
            <div className="flex items-center gap-2">
              <span>Rows per page:</span>
              <select
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}
                className="bg-[#F9FAFB] border border-[#E5E7EB] rounded px-2 py-1 text-xs text-[#1F2937]"
              >
                <option value={10}>10</option>
                <option value={25}>25</option>
                <option value={50}>50</option>
              </select>
            </div>

            <div className="flex items-center gap-2">
              <button
                disabled={page <= 1}
                onClick={() => setPage(p => p - 1)}
                className="px-3 py-1.5 rounded border border-[#E5E7EB] bg-white text-[#374151] disabled:opacity-40 disabled:cursor-not-allowed hover:border-[#2563EB] transition-all cursor-pointer flex items-center gap-1"
              >
                <ChevronLeft className="w-3.5 h-3.5" /> Prev
              </button>
              <span>Page {page} of {totalPages}</span>
              <button
                disabled={page >= totalPages}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1.5 rounded border border-[#E5E7EB] bg-white text-[#374151] disabled:opacity-40 disabled:cursor-not-allowed hover:border-[#2563EB] transition-all cursor-pointer flex items-center gap-1"
              >
                Next <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>

        {/* JSON Details Modal */}
        {selectedDetails && (
          <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-xl max-w-lg w-full p-5 shadow-2xl border border-[#E5E7EB]">
              <div className="flex items-center justify-between mb-4 border-b border-[#F3F4F6] pb-3">
                <h4 className="font-semibold text-sm text-[#1F2937] flex items-center gap-2">
                  <Code className="w-4 h-4 text-[#2563EB]" /> Event Payload Details
                </h4>
                <button
                  onClick={() => setSelectedDetails(null)}
                  className="text-[#9CA3AF] hover:text-[#1F2937] text-sm font-bold cursor-pointer"
                >
                  ✕
                </button>
              </div>
              <pre className="bg-[#1E293B] text-[#F8FAFC] p-4 rounded-lg text-xs font-mono overflow-x-auto max-h-80">
                {(() => {
                  try {
                    return JSON.stringify(JSON.parse(selectedDetails), null, 2)
                  } catch (e) {
                    return selectedDetails
                  }
                })()}
              </pre>
              <div className="mt-4 flex justify-end">
                <button
                  onClick={() => setSelectedDetails(null)}
                  className="px-4 py-2 bg-[#2563EB] text-white rounded-lg text-xs font-semibold hover:bg-[#1E40AF] transition-all cursor-pointer shadow-md"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SuperAdminLayout>
  )
}
