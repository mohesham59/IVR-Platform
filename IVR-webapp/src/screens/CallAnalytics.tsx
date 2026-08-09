import { useState, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import { backendUrl } from '../api/backendUrl'
import { History, PlayCircle, Clock, Calendar, Hash, Tag, Info } from 'lucide-react'

interface CallEvent {
  type: string
  data?: string
  timestamp: string
}

interface CallAnalyticsRecord {
  id: number
  callId: string
  callerNumber: string
  startTime: string
  endTime: string
  duration: number
  events: string
  recordingUrl?: string
}

export default function CallAnalytics({ onLogout }: { onLogout: () => void }) {
  const [calls, setCalls] = useState<CallAnalyticsRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const fetchCalls = async () => {
      try {
        const token = localStorage.getItem('nexus_jwt_token')
        const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
        
        let res = await fetch('/api/v1/ai/analytics/calls', { headers }).catch(() => null)
        if (!res || !res.ok) {
          res = await fetch(backendUrl('/api/v1/ai/analytics/calls'), { headers })
        }
        
        if (!res.ok) {
          setError(`Failed to fetch analytics (Status: ${res.status})`)
          return
        }

        const data = await res.json()
        if (data.success && data.data) {
          setCalls(data.data)
        } else {
          setError(data.error || 'Failed to load call analytics')
        }
      } catch (err: any) {
        setError(err.message)
      } finally {
        setLoading(false)
      }
    }
    fetchCalls()
  }, [])

  const formatDuration = (seconds: number) => {
    const m = Math.floor(seconds / 60)
    const s = seconds % 60
    return `${m}m ${s}s`
  }

  return (
    <TenantLayout
      pageTitle="Call Analytics"
      pageSubtitle="View your call history and analytics"
      activeNav="/tenant/call-analytics"
      onLogout={onLogout}
    >
      <div className="space-y-6">
        
        {loading ? (
          <div className="flex items-center justify-center h-64 text-[#6B7280]">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#2563EB]"></div>
          </div>
        ) : error ? (
          <div className="bg-red-50 text-red-600 p-4 rounded-xl border border-red-200">
            {error}
          </div>
        ) : calls.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-xl border border-[#E5E7EB] shadow-sm">
            <History className="w-12 h-12 text-[#D1D5DB] mx-auto mb-3" />
            <h3 className="text-lg font-medium text-[#1F2937]">No Calls Found</h3>
            <p className="text-[#6B7280] text-sm mt-1">There are no call records available yet.</p>
          </div>
        ) : (
          <div className="grid gap-4">
            {calls.map((call) => {
              let parsedEvents: CallEvent[] = []
              try {
                if (call.events) {
                  parsedEvents = JSON.parse(call.events)
                }
              } catch (e) {
                console.error("Failed to parse events", e)
              }

              return (
                <div key={call.id} className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm hover:shadow-md transition-shadow">
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-4 pb-4 border-b border-[#F3F4F6]">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-full bg-[#EFF6FF] flex items-center justify-center">
                        <Hash className="w-5 h-5 text-[#2563EB]" />
                      </div>
                      <div>
                        <div className="text-lg font-bold text-[#1F2937]">{call.callerNumber || 'Unknown'}</div>
                        <div className="flex items-center gap-3 text-sm text-[#6B7280] mt-1">
                          <span className="flex items-center gap-1"><Calendar className="w-3.5 h-3.5" /> {new Date(call.startTime).toLocaleString()}</span>
                          <span className="flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> {formatDuration(call.duration)}</span>
                        </div>
                      </div>
                    </div>
                    {call.recordingUrl && (
                      <div className="flex items-center gap-2 mt-2">
                        <audio 
                          controls 
                          src={"/api/v1/voice-prompts/stream?name=" + encodeURIComponent(call.recordingUrl)} 
                          className="h-8 max-w-[250px]"
                        >
                          Your browser does not support the audio element.
                        </audio>
                      </div>
                    )}
                  </div>

                  <div>
                    <h4 className="text-sm font-semibold text-[#374151] mb-3 flex items-center gap-1.5">
                      <Info className="w-4 h-4" /> Call Events Timeline
                    </h4>
                    {parsedEvents.length > 0 ? (
                      <div className="space-y-2">
                        {parsedEvents.map((evt, idx) => (
                          <div key={idx} className="flex items-start gap-3 text-sm">
                            <div className="mt-1 flex-shrink-0 w-2 h-2 rounded-full bg-[#93C5FD]"></div>
                            <div className="flex-1">
                              <span className="font-medium text-[#1F2937] bg-[#F3F4F6] px-1.5 py-0.5 rounded text-xs mr-2 border border-[#E5E7EB]">
                                {evt.type}
                              </span>
                              {evt.data && <span className="text-[#4B5563] font-medium mr-2">Input: <span className="text-[#2563EB]">{evt.data}</span></span>}
                              <span className="text-[#9CA3AF] text-xs">
                                {new Date(evt.timestamp).toLocaleTimeString()}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-sm text-[#9CA3AF]">No events logged for this call.</div>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </TenantLayout>
  )
}
