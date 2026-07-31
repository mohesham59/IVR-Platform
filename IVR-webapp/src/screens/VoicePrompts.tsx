import { useState, useRef } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Plus, Search, Upload, ChevronDown, MoreHorizontal, X,
  Play, Pause, Download, Trash2, Archive, RefreshCw,
  Volume2, Wand2, Mic, Globe, Filter,
  ChevronLeft, ChevronRight, SkipBack, SkipForward,
  FileAudio, Sparkles,
} from 'lucide-react'

const prompts = [
  { id: 1, name: 'welcome_hospital.wav', language: 'English (US)', duration: '0:08', type: 'AI Generated', createdBy: 'AI · Groq', status: 'Active', size: '64KB', usedIn: ['Hospital Main IVR'] },
  { id: 2, name: 'menu_main.wav', language: 'English (US)', duration: '0:22', type: 'AI Generated', createdBy: 'AI · Groq', status: 'Active', size: '176KB', usedIn: ['Hospital Main IVR', 'After-Hours IVR'] },
  { id: 3, name: 'afterhours_msg.wav', language: 'English (US)', duration: '0:15', type: 'Uploaded', createdBy: 'Marcus Webb', status: 'Active', size: '120KB', usedIn: ['Hospital Main IVR'] },
  { id: 4, name: 'billing_hold.wav', language: 'English (US)', duration: '2:30', type: 'Uploaded', createdBy: 'Natalie R.', status: 'Active', size: '1.2MB', usedIn: ['Billing Queue'] },
  { id: 5, name: 'bienvenida_hospital.wav', language: 'Spanish (US)', duration: '0:09', type: 'AI Generated', createdBy: 'AI · Polly.Lucia', status: 'Active', size: '72KB', usedIn: ['Hospital Main IVR'] },
  { id: 6, name: 'menu_principal.wav', language: 'Spanish (US)', duration: '0:24', type: 'AI Generated', createdBy: 'AI · Polly.Lucia', status: 'Active', size: '192KB', usedIn: ['Hospital Main IVR'] },
  { id: 7, name: 'emergency_prompt.wav', language: 'English (US)', duration: '0:06', type: 'Uploaded', createdBy: 'Priya Nair', status: 'Draft', size: '48KB', usedIn: [] },
  { id: 8, name: 'voicemail_greeting.wav', language: 'English (US)', duration: '0:12', type: 'AI Generated', createdBy: 'AI · Groq', status: 'Archived', size: '96KB', usedIn: [] },
]

// Fake waveform bars
const WAVEFORM = Array.from({ length: 60 }, (_, i) => 0.15 + Math.abs(Math.sin(i * 0.5 + Math.cos(i * 0.3)) * 0.7 + Math.cos(i * 0.2) * 0.2))

const statusCls: Record<string, string> = {
  Active: 'bg-[#DCFCE7] text-[#15803D]',
  Draft: 'bg-[#FEF9C3] text-[#A16207]',
  Archived: 'bg-[#F3F4F6] text-[#6B7280]',
}

const typeCls: Record<string, string> = {
  'AI Generated': 'bg-[#F5F3FF] text-[#6D28D9] border-[#DDD6FE]',
  Uploaded: 'bg-[#EFF6FF] text-[#2563EB] border-[#BFDBFE]',
}

function ActionMenu({ onClose }: { onClose: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-40 py-1">
      {[
        { icon: <Play className="w-3.5 h-3.5" />, label: 'Play' },
        { icon: <Download className="w-3.5 h-3.5" />, label: 'Download' },
        { icon: <RefreshCw className="w-3.5 h-3.5" />, label: 'Replace' },
        { icon: <Archive className="w-3.5 h-3.5" />, label: 'Archive', color: 'text-[#F59E0B]' },
        { icon: <Trash2 className="w-3.5 h-3.5" />, label: 'Delete', color: 'text-[#EF4444]' },
      ].map(item => (
        <button key={item.label} onClick={onClose}
          className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${(item as { color?: string }).color ?? 'text-[#374151]'}`}>
          {item.icon}{item.label}
        </button>
      ))}
    </div>
  )
}

export default function VoicePrompts({ onLogout }: { onLogout: () => void }) {
  const [selected, setSelected] = useState<typeof prompts[0] | null>(prompts[0])
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(32)
  const [openMenu, setOpenMenu] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [langFilter, setLangFilter] = useState('All Languages')
  const [typeFilter, setTypeFilter] = useState('All Types')
  const [statusFilter, setStatusFilter] = useState('All Status')
  const intervalRef = useRef<number | null>(null)

  const togglePlay = () => {
    setPlaying(p => {
      if (!p) {
        intervalRef.current = window.setInterval(() => setProgress(v => v >= 100 ? 0 : v + 0.5), 50)
      } else {
        if (intervalRef.current) clearInterval(intervalRef.current)
      }
      return !p
    })
  }

  const filtered = prompts.filter(p =>
    (langFilter === 'All Languages' || p.language === langFilter) &&
    (typeFilter === 'All Types' || p.type === typeFilter) &&
    (statusFilter === 'All Status' || p.status === statusFilter) &&
    p.name.toLowerCase().includes(search.toLowerCase())
  )

  const headerActions = (
    <>
      <button className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Upload className="w-4 h-4" /> Upload
      </button>
      <button className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-gradient-to-r from-[#8B5CF6] to-[#2563EB] text-white text-sm font-medium hover:opacity-90 transition-opacity shadow-md">
        <Sparkles className="w-4 h-4" /> AI Generate
      </button>
      <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-medium hover:bg-[#1E40AF] transition-all shadow-md shadow-[#2563EB]/20">
        <Plus className="w-4 h-4" /> New Prompt
      </button>
    </>
  )

  return (
    <TenantLayout activeNav="voice-prompts" onLogout={onLogout}
      pageTitle="Voice Prompts" pageSubtitle={`${prompts.length} audio prompts across ${prompts.filter(p => p.status === 'Active').length} active`}
      headerActions={headerActions}>
      <div className="flex gap-4">
        {/* Main */}
        <div className="flex-1 min-w-0 space-y-4">
          {/* Stats */}
          <div className="grid grid-cols-4 gap-4">
            {[
              { label: 'Total Prompts', value: prompts.length, icon: <FileAudio className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF' },
              { label: 'AI Generated', value: prompts.filter(p => p.type === 'AI Generated').length, icon: <Sparkles className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF' },
              { label: 'Uploaded', value: prompts.filter(p => p.type === 'Uploaded').length, icon: <Upload className="w-5 h-5" />, color: '#06B6D4', bg: '#ECFEFF' },
              { label: 'Languages', value: 2, icon: <Globe className="w-5 h-5" />, color: '#22C55E', bg: '#F0FDF4' },
            ].map(s => (
              <div key={s.label} className="bg-white rounded-xl border border-[#E5E7EB] p-4 shadow-sm hover:shadow-md transition-shadow">
                <div className="w-9 h-9 rounded-lg flex items-center justify-center mb-3" style={{ backgroundColor: s.bg, color: s.color }}>{s.icon}</div>
                <div className="text-[#1F2937] font-bold text-2xl">{s.value}</div>
                <div className="text-[#9CA3AF] text-xs mt-1">{s.label}</div>
              </div>
            ))}
          </div>

          {/* Filter bar */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-4 flex items-center gap-3 flex-wrap">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF]" />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search prompts…"
                className="w-full h-9 pl-9 pr-4 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all" />
            </div>
            <Filter className="w-4 h-4 text-[#9CA3AF]" />
            {[
              { value: langFilter, setter: setLangFilter, opts: ['All Languages', 'English (US)', 'Spanish (US)'] },
              { value: typeFilter, setter: setTypeFilter, opts: ['All Types', 'AI Generated', 'Uploaded'] },
              { value: statusFilter, setter: setStatusFilter, opts: ['All Status', 'Active', 'Draft', 'Archived'] },
            ].map((f, i) => (
              <div key={i} className="relative">
                <select value={f.value} onChange={e => f.setter(e.target.value)}
                  className="h-9 pl-3 pr-8 rounded-lg border border-[#E5E7EB] bg-white text-sm text-[#374151] font-medium outline-none focus:border-[#2563EB] appearance-none cursor-pointer hover:border-[#2563EB] transition-colors">
                  {f.opts.map(o => <option key={o}>{o}</option>)}
                </select>
                <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
              </div>
            ))}
            <span className="ml-auto text-xs text-[#9CA3AF]">{filtered.length} prompts</span>
          </div>

          {/* Table */}
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                  <th className="w-10 px-4 py-3"><input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]" /></th>
                  {['Prompt Name', 'Language', 'Duration', 'Type', 'Created By', 'Status', ''].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[#F3F4F6]">
                {filtered.map(p => (
                  <tr key={p.id} onClick={() => setSelected(p)}
                    className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer group ${selected?.id === p.id ? 'bg-[#EFF6FF]' : ''}`}>
                    <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
                      <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]" />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2.5">
                        <button onClick={e => { e.stopPropagation(); setSelected(p); togglePlay() }}
                          className="w-7 h-7 rounded-lg bg-[#EFF6FF] flex items-center justify-center text-[#2563EB] hover:bg-[#DBEAFE] transition-colors flex-shrink-0">
                          <Play className="w-3 h-3" />
                        </button>
                        <span className="text-[#1F2937] font-medium text-xs font-mono truncate max-w-[160px]">{p.name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-[#6B7280] text-xs">{p.language}</td>
                    <td className="px-4 py-3 text-[#6B7280] text-xs font-mono">{p.duration}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full border text-[10px] font-semibold ${typeCls[p.type]}`}>
                        {p.type === 'AI Generated' ? <Sparkles className="w-2.5 h-2.5 mr-1" /> : <Upload className="w-2.5 h-2.5 mr-1" />}
                        {p.type}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-[#6B7280] text-xs">{p.createdBy}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${statusCls[p.status]}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${p.status === 'Active' ? 'bg-[#22C55E]' : p.status === 'Draft' ? 'bg-[#F59E0B]' : 'bg-[#D1D5DB]'}`} />
                        {p.status}
                      </span>
                    </td>
                    <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
                      <div className="relative">
                        <button onClick={() => setOpenMenu(openMenu === p.id ? null : p.id)}
                          className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors opacity-0 group-hover:opacity-100">
                          <MoreHorizontal className="w-4 h-4" />
                        </button>
                        {openMenu === p.id && <ActionMenu onClose={() => setOpenMenu(null)} />}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
              <p className="text-[#9CA3AF] text-xs">Showing {filtered.length} of {prompts.length} prompts</p>
              <div className="flex gap-1">
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF]"><ChevronLeft className="w-3.5 h-3.5" /></button>
                <button className="w-7 h-7 rounded-lg border bg-[#2563EB] border-[#2563EB] text-white text-xs font-medium">1</button>
                <button className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF]"><ChevronRight className="w-3.5 h-3.5" /></button>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Audio player panel */}
        {selected && (
          <div className="w-72 flex-shrink-0 bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden flex flex-col" style={{ maxHeight: 'calc(100vh - 160px)', position: 'sticky', top: 0 }}>
            <div className="flex items-center justify-between p-4 border-b border-[#F3F4F6]">
              <p className="text-[#1F2937] font-semibold text-sm">Audio Preview</p>
              <button onClick={() => setSelected(null)} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-5">
              {/* File info */}
              <div className="flex items-center gap-3 p-3 rounded-xl bg-[#F9FAFB] border border-[#F3F4F6]">
                <div className="w-10 h-10 rounded-xl bg-[#EFF6FF] flex items-center justify-center flex-shrink-0">
                  <Volume2 className="w-5 h-5 text-[#2563EB]" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[#1F2937] font-semibold text-xs truncate">{selected.name}</p>
                  <p className="text-[#9CA3AF] text-[10px] mt-0.5">{selected.language} · {selected.duration} · {selected.size}</p>
                </div>
              </div>

              {/* Waveform */}
              <div>
                <div className="flex items-end gap-0.5 h-14 bg-[#F9FAFB] rounded-xl px-3 py-2 cursor-pointer border border-[#F3F4F6]">
                  {WAVEFORM.map((h, i) => {
                    const pct = (i / WAVEFORM.length) * 100
                    const isPast = pct <= progress
                    return (
                      <div key={i} className="flex-1 rounded-full transition-colors"
                        style={{ height: `${h * 100}%`, backgroundColor: isPast ? '#2563EB' : '#DBEAFE' }} />
                    )
                  })}
                </div>
                <div className="flex items-center justify-between mt-1.5 px-1">
                  <span className="text-[#9CA3AF] text-[10px] font-mono">0:0{Math.floor(progress * 0.15)}</span>
                  <span className="text-[#9CA3AF] text-[10px] font-mono">{selected.duration}</span>
                </div>
              </div>

              {/* Controls */}
              <div className="flex items-center justify-center gap-3">
                <button className="w-8 h-8 rounded-full flex items-center justify-center text-[#9CA3AF] hover:text-[#374151] transition-colors">
                  <SkipBack className="w-4 h-4" />
                </button>
                <button onClick={togglePlay}
                  className="w-11 h-11 rounded-full bg-[#2563EB] flex items-center justify-center text-white hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/25">
                  {playing ? <Pause className="w-5 h-5" /> : <Play className="w-5 h-5 ml-0.5" />}
                </button>
                <button className="w-8 h-8 rounded-full flex items-center justify-center text-[#9CA3AF] hover:text-[#374151] transition-colors">
                  <SkipForward className="w-4 h-4" />
                </button>
              </div>

              {/* Volume */}
              <div className="flex items-center gap-2">
                <Volume2 className="w-3.5 h-3.5 text-[#9CA3AF] flex-shrink-0" />
                <input type="range" min={0} max={100} defaultValue={80}
                  className="flex-1 h-1 rounded-full accent-[#2563EB]" />
                <span className="text-[#9CA3AF] text-[10px] w-7 text-right">80%</span>
              </div>

              {/* Transcript */}
              <section>
                <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Transcript</h4>
                <div className="p-3 rounded-lg bg-[#F9FAFB] border border-[#F3F4F6] text-[#374151] text-xs leading-relaxed italic">
                  "Thank you for calling Meridian Health. For appointments, press 1. For emergency services, press 2. For billing, press 3. For all other inquiries, press 0 to speak with an agent."
                </div>
              </section>

              {/* Used in */}
              {selected.usedIn.length > 0 && (
                <section>
                  <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Used In</h4>
                  <div className="flex flex-wrap gap-1.5">
                    {selected.usedIn.map(f => (
                      <span key={f} className="bg-[#EFF6FF] border border-[#BFDBFE] text-[#2563EB] text-[10px] font-medium px-2 py-0.5 rounded-full">{f}</span>
                    ))}
                  </div>
                </section>
              )}

              {/* AI actions */}
              <section className="space-y-2 pt-1">
                <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">AI Actions</h4>
                <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-gradient-to-r from-[#8B5CF6] to-[#2563EB] text-white text-xs font-semibold hover:opacity-90 transition-opacity">
                  <Wand2 className="w-3.5 h-3.5" /> AI Rewrite Prompt
                </button>
                <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                  <Mic className="w-3.5 h-3.5" /> Generate New Voice
                </button>
                <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                  <Download className="w-3.5 h-3.5" /> Download File
                </button>
              </section>
            </div>
          </div>
        )}
      </div>
    </TenantLayout>
  )
}
