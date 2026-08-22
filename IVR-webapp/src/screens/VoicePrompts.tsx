import { useState, useRef, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Search, Upload, ChevronDown, MoreHorizontal, X,
  Play, Pause, Download, Trash2, RefreshCw,
  Volume2, Filter, ChevronLeft, ChevronRight,
  SkipBack, SkipForward, FileAudio, Sparkles,
} from 'lucide-react'

export interface Prompt {
  id: number;
  name: string;
  language: string;
  duration: string;
  type: string;
  createdBy: string;
  status: string;
  size: string;
  usedIn: string[];
  promptText?: string;
}

const prompts: Prompt[] = []

// Fake waveform bars
const WAVEFORM = Array.from({ length: 60 }, (_, i) => 0.15 + Math.abs(Math.sin(i * 0.5 + Math.cos(i * 0.3)) * 0.7 + Math.cos(i * 0.2) * 0.2))

const parseDurationSec = (durStr?: string) => {
  if (!durStr || durStr === '0:00' || durStr === '--:--') return 0
  const parts = durStr.split(':')
  if (parts.length === 2) {
    return (parseInt(parts[0], 10) || 0) * 60 + (parseInt(parts[1], 10) || 0)
  }
  return 0
}

const typeCls: Record<string, string> = {
  'AI Generated': 'bg-[#F5F3FF] text-[#6D28D9] border-[#DDD6FE]',
  Uploaded: 'bg-[#EFF6FF] text-[#2563EB] border-[#BFDBFE]',
}

function ActionMenu({ onClose, onDelete, onDownload, onReplace }: { onClose: () => void, onDelete: () => void, onDownload: () => void, onReplace: () => void }) {
  return (
    <div className="absolute right-0 top-8 z-50 bg-white rounded-xl border border-[#E5E7EB] shadow-xl shadow-black/10 w-40 py-1">
      {[
        { icon: <Download className="w-3.5 h-3.5" />, label: 'Download', action: onDownload },
        { icon: <RefreshCw className="w-3.5 h-3.5" />, label: 'Replace', action: onReplace },
        { icon: <Trash2 className="w-3.5 h-3.5" />, label: 'Delete', color: 'text-[#EF4444]', action: onDelete },
      ].map(item => (
        <button key={item.label} onClick={() => { if (item.action) item.action(); onClose(); }}
          className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${(item as { color?: string }).color ?? 'text-[#374151]'}`}>
          {item.icon}{item.label}
        </button>
      ))}
    </div>
  )
}

export default function VoicePrompts({ onLogout }: { onLogout: () => void }) {
  const [promptsList, setPromptsList] = useState<Prompt[]>(prompts)
  const [selected, setSelected] = useState<Prompt | null>(promptsList[0] || null)
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(32)
  const [openMenu, setOpenMenu] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [langFilter, setLangFilter] = useState('All Languages')
  const [typeFilter, setTypeFilter] = useState('All Types')
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    fetch('/api/v1/voice-prompts/upload')
      .then(res => res.json())
      .then(data => {
        if (data.success && data.prompts) {
          setPromptsList(data.prompts)
          if (data.prompts.length > 0) {
            setSelected(data.prompts[0])
          }
        }
      })
      .catch(err => console.error('Failed to load prompts', err))
  }, [])

  const [modalConfig, setModalConfig] = useState<{ isOpen: boolean; mode: 'upload' | 'replace' | 'generate'; originalName?: string }>({ isOpen: false, mode: 'upload' })
  const [generateText, setGenerateText] = useState('')
  const [modalFile, setModalFile] = useState<File | null>(null)
  const [modalFileName, setModalFileName] = useState('')
  const [modalLanguage, setModalLanguage] = useState('English (US)')

  const handleReplaceInit = (prompt: Prompt) => {
    const isAi = prompt.type === 'AI Generated'
    setModalConfig({ isOpen: true, mode: isAi ? 'generate' : 'replace', originalName: prompt.name })
    setModalFile(null)
    setModalFileName(prompt.name.replace('.wav', ''))
    setModalLanguage(prompt.language || 'English (US)')
    setGenerateText('')
  }

  const handleUploadInit = () => {
    setModalConfig({ isOpen: true, mode: 'upload' })
    setModalFile(null)
    setModalFileName('')
    setModalLanguage('English (US)')
  }

  const handleGenerateInit = () => {
    setModalConfig({ isOpen: true, mode: 'generate' })
    setModalFile(null)
    setModalFileName('')
    setModalLanguage('English (US)')
    setGenerateText('')
  }

  const handleModalFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.wav')) {
      alert('Only .wav files are allowed.')
      return
    }
    setModalFile(file)
    if (!modalFileName || modalConfig.mode === 'upload') {
      setModalFileName(file.name.replace('.wav', ''))
    }
  }

  const submitModal = async () => {
    let finalName = modalFileName.trim()
    if (!finalName) {
      alert('Please enter a file name.')
      return
    }
    if (!finalName.toLowerCase().endsWith('.wav')) {
      finalName += '.wav'
    }

    if (modalConfig.mode === 'generate') {
      if (!generateText.trim()) return alert('Please enter text to generate.')
      if (!modalConfig.originalName || finalName.toLowerCase() !== modalConfig.originalName.toLowerCase()) {
        if (promptsList.some(p => p.name.toLowerCase() === finalName.toLowerCase())) {
          return alert(`A voice prompt named "${finalName}" already exists.`)
        }
      }
      try {
        const res = await fetch('/api/v1/voice-prompts/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ fileName: finalName, language: modalLanguage, text: generateText })
        })
        if (!res.ok) throw new Error('Generation failed')
        const data = await res.json()
        if (data.success) {
          if (modalConfig.originalName && finalName !== modalConfig.originalName) {
            await fetch(`/api/v1/voice-prompts/upload?fileName=${encodeURIComponent(modalConfig.originalName)}`, { method: 'DELETE' }).catch(() => {})
          }
          fetch('/api/v1/voice-prompts/upload').then(r => r.json()).then(d => {
            if (d.success && d.prompts) {
              setPromptsList(d.prompts)
              if (selected?.name === modalConfig.originalName) {
                setSelected(d.prompts.find((p: any) => p.name === finalName) || null)
              }
            }
          })
          setModalConfig({ isOpen: false, mode: 'upload' })
        } else alert('Error: ' + data.message)
      } catch (e: any) { alert(e.message) }
      return
    }

    if (!modalFile && modalConfig.mode === 'upload') {
      alert('Please select a file.')
      return
    }

    if (modalConfig.mode === 'upload' || (modalConfig.mode === 'replace' && finalName !== modalConfig.originalName)) {
      if (promptsList.some(p => p.name.toLowerCase() === finalName.toLowerCase())) {
        alert(`A voice prompt named "${finalName}" already exists.`)
        return
      }
    }

    const fileToUpload = modalFile
    if (!fileToUpload && modalConfig.mode === 'replace') {
      alert('Please select a replacement file.')
      return
    }

    const formData = new FormData()
    formData.append('file', fileToUpload!, finalName)
    formData.append('language', modalLanguage)
    formData.append('username', 'Tenant Admin')

    try {
      const res = await fetch('/api/v1/voice-prompts/upload', {
        method: 'POST',
        body: formData
      })
      if (!res.ok) throw new Error('Operation failed')
      const data = await res.json()
      if (data.success) {
        if (modalConfig.mode === 'replace' && finalName !== modalConfig.originalName) {
          await fetch(`/api/v1/voice-prompts/upload?fileName=${encodeURIComponent(modalConfig.originalName!)}`, { method: 'DELETE' }).catch(() => {})
        }
        
        fetch('/api/v1/voice-prompts/upload')
          .then(r => r.json())
          .then(d => {
            if (d.success && d.prompts) {
              setPromptsList(d.prompts)
              if (selected?.name === modalConfig.originalName) {
                setSelected(d.prompts.find((p: any) => p.name === finalName) || null)
              }
            }
          })
        setModalConfig({ isOpen: false, mode: 'upload' })
      } else {
        alert('Operation failed: ' + JSON.stringify(data))
      }
    } catch (err) {
      console.error(err)
      alert('Operation failed')
    }
  }

  const handleDownload = (fileName: string) => {
    window.location.href = `/api/v1/voice-prompts/upload?download=${encodeURIComponent(fileName)}`
  }

  const handleDelete = async (fileName: string) => {
    if (!confirm(`Are you sure you want to delete ${fileName}?`)) return
    try {
      const res = await fetch(`/api/v1/voice-prompts/upload?fileName=${encodeURIComponent(fileName)}`, {
        method: 'DELETE'
      })
      if (!res.ok) throw new Error('Failed to delete')
      const data = await res.json()
      if (data.success) {
        setPromptsList(promptsList.filter(p => p.name !== fileName))
        if (selected?.name === fileName) setSelected(null)
      } else {
        alert('Delete failed')
      }
    } catch (err) {
      console.error(err)
      alert('Delete failed')
    }
  }

  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [audioDuration, setAudioDuration] = useState<number>(() => parseDurationSec(selected?.duration))
  const [currentTime, setCurrentTime] = useState(0)
  const [volume, setVolume] = useState(80)
  const animFrameRef = useRef<number | null>(null)

  const autoPlayRef = useRef(false)

  useEffect(() => {
    if (selected) {
      if (audioRef.current) {
        audioRef.current.pause()
      }

      const dbSecs = parseDurationSec(selected.duration)
      setAudioDuration(dbSecs)

      const audio = new Audio(`/api/v1/voice-prompts/stream?name=${encodeURIComponent(selected.name)}`)
      audio.volume = volume / 100
      audioRef.current = audio

      const updateDuration = () => {
        if (audio.duration && !isNaN(audio.duration) && isFinite(audio.duration) && audio.duration > 0) {
          setAudioDuration(audio.duration)
        }
      }

      audio.addEventListener('loadedmetadata', updateDuration)
      audio.addEventListener('durationchange', updateDuration)

      audio.onended = () => {
        if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
        setPlaying(false)
        setProgress(0)
        setCurrentTime(0)
      }

      setProgress(0)
      setCurrentTime(0)

      audio.load()

      if (autoPlayRef.current) {
        audio.play().then(() => setPlaying(true)).catch(err => {
          console.error('Auto-play failed:', err)
          setPlaying(false)
        })
        autoPlayRef.current = false
      } else {
        setPlaying(false)
      }

      return () => {
        if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
        audio.removeEventListener('loadedmetadata', updateDuration)
        audio.removeEventListener('durationchange', updateDuration)
        audio.pause()
        audioRef.current = null
      }
    } else {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
      if (audioRef.current) {
        audioRef.current.pause()
        audioRef.current = null
      }
      setPlaying(false)
      setProgress(0)
      setCurrentTime(0)
      setAudioDuration(0)
    }
  }, [selected])

  // Continuous animation frame loop when playing
  useEffect(() => {
    if (playing && audioRef.current) {
      const updateProgress = () => {
        if (audioRef.current) {
          const cur = audioRef.current.currentTime
          const dur = (audioRef.current.duration && !isNaN(audioRef.current.duration) && isFinite(audioRef.current.duration) && audioRef.current.duration > 0)
            ? audioRef.current.duration
            : parseDurationSec(selected?.duration)
          
          setCurrentTime(cur)
          if (dur > 0) {
            setProgress((cur / dur) * 100)
          }
          if (!audioRef.current.paused && !audioRef.current.ended) {
            animFrameRef.current = requestAnimationFrame(updateProgress)
          }
        }
      }
      animFrameRef.current = requestAnimationFrame(updateProgress)
    } else {
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current)
      }
    }

    return () => {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
    }
  }, [playing, selected])

  const togglePlay = () => {
    if (!selected) return

    if (!audioRef.current) {
      const audio = new Audio(`/api/v1/voice-prompts/stream?name=${encodeURIComponent(selected.name)}`)
      audio.volume = volume / 100
      audioRef.current = audio
      
      const updateDuration = () => {
        if (audio.duration && !isNaN(audio.duration) && isFinite(audio.duration)) {
          setAudioDuration(audio.duration)
        }
      }

      audio.addEventListener('loadedmetadata', updateDuration)
      audio.addEventListener('durationchange', updateDuration)
      audio.onended = () => {
        if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
        setPlaying(false)
        setProgress(0)
        setCurrentTime(0)
      }
    }

    if (audioRef.current) {
      if (playing) {
        audioRef.current.pause()
        setPlaying(false)
      } else {
        audioRef.current.play()
          .then(() => setPlaying(true))
          .catch(err => console.error('Play failed:', err))
      }
    }
  }

  const effectiveDuration = (audioDuration && isFinite(audioDuration) && audioDuration > 0)
    ? audioDuration
    : parseDurationSec(selected?.duration)

  const handleSkip = (seconds: number) => {
    if (!audioRef.current) return
    const dur = effectiveDuration
    const newTime = Math.max(0, Math.min(dur || 9999, audioRef.current.currentTime + seconds))
    audioRef.current.currentTime = newTime
    setCurrentTime(newTime)
    if (dur) setProgress((newTime / dur) * 100)
  }

  const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = Number(e.target.value)
    setVolume(val)
    if (audioRef.current) {
      audioRef.current.volume = val / 100
    }
  }

  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!audioRef.current) return
    const dur = effectiveDuration
    if (!dur) return
    const rect = e.currentTarget.getBoundingClientRect()
    const clickX = e.clientX - rect.left
    const pct = Math.max(0, Math.min(1, clickX / rect.width))
    const seekTime = pct * dur
    audioRef.current.currentTime = seekTime
    setCurrentTime(seekTime)
    setProgress(pct * 100)
  }

  const formatTime = (secs: number) => {
    if (isNaN(secs) || secs < 0) return '0:00'
    const m = Math.floor(secs / 60)
    const s = Math.floor(secs % 60)
    return `${m}:${s < 10 ? '0' : ''}${s}`
  }

  const [currentPage, setCurrentPage] = useState(1)
  const ITEMS_PER_PAGE = 10

  const filtered = promptsList.filter(p =>
    (langFilter === 'All Languages' || p.language === langFilter) &&
    (typeFilter === 'All Types' || p.type === typeFilter) &&
    p.name.toLowerCase().includes(search.toLowerCase())
  )

  const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE) || 1
  const validCurrentPage = Math.min(currentPage, totalPages)
  const startIndex = (validCurrentPage - 1) * ITEMS_PER_PAGE
  const pageItems = filtered.slice(startIndex, startIndex + ITEMS_PER_PAGE)

  const handleSelectAll = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.checked) {
      setSelectedIds(new Set(filtered.map(p => p.id)))
    } else {
      setSelectedIds(new Set())
    }
  }

  const handleSelectRow = (id: number) => {
    const next = new Set(selectedIds)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setSelectedIds(next)
  }

  const handleBatchDelete = async () => {
    if (!confirm(`Are you sure you want to delete ${selectedIds.size} prompts?`)) return
    const toDelete = promptsList.filter(p => selectedIds.has(p.id)).map(p => p.name)
    
    let deletedCount = 0
    for (const name of toDelete) {
      try {
        const res = await fetch(`/api/v1/voice-prompts/upload?fileName=${encodeURIComponent(name)}`, { method: 'DELETE' })
        if (res.ok) {
          const data = await res.json()
          if (data.success) deletedCount++
        }
      } catch(e) {}
    }
    
    fetch('/api/v1/voice-prompts/upload')
      .then(res => res.json())
      .then(data => {
        if (data.success && data.prompts) setPromptsList(data.prompts)
      })
    
    setSelectedIds(new Set())
    if (deletedCount > 0) alert(`Successfully deleted ${deletedCount} prompts.`)
  }

  const handleBatchDownload = () => {
    const toDownload = promptsList.filter(p => selectedIds.has(p.id)).map(p => p.name)
    toDownload.forEach((name, idx) => {
      setTimeout(() => {
        window.location.href = `/api/v1/voice-prompts/upload?download=${encodeURIComponent(name)}`
      }, idx * 500)
    })
  }

  const headerActions = (
    <div className="flex items-center gap-3">
      {selectedIds.size > 0 && (
        <div className="flex items-center gap-2 bg-[#EFF6FF] px-3 py-1.5 rounded-lg border border-[#BFDBFE] animate-in fade-in slide-in-from-top-2">
          <span className="text-[#1E40AF] text-sm font-medium mr-2">{selectedIds.size} selected</span>
          <button onClick={handleBatchDownload} className="flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-white text-[#2563EB] text-xs font-semibold hover:bg-[#DBEAFE] transition-colors shadow-sm">
            <Download className="w-3.5 h-3.5" /> Download
          </button>
          <button onClick={handleBatchDelete} className="flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-white text-[#EF4444] text-xs font-semibold hover:bg-[#FEE2E2] transition-colors shadow-sm">
            <Trash2 className="w-3.5 h-3.5" /> Delete
          </button>
        </div>
      )}
      <button onClick={handleUploadInit} className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-sm font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm">
        <Upload className="w-4 h-4" /> Upload
      </button>
      <button onClick={handleGenerateInit} className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-gradient-to-r from-[#8B5CF6] to-[#2563EB] text-white text-sm font-medium hover:opacity-90 transition-opacity shadow-md">
        <Sparkles className="w-4 h-4" /> Generate
      </button>

    </div>
  )

  return (
    <>
      <TenantLayout activeNav="voice-prompts" onLogout={onLogout}
        pageTitle="Voice Prompts" pageSubtitle={`${promptsList.length} audio prompts`}
        headerActions={headerActions}>
        <div className="flex gap-4">
          {/* Main */}
          <div className="flex-1 min-w-0 space-y-4">
            {/* Stats */}
            <div className="grid grid-cols-4 gap-4">
              {[
                { label: 'Total Prompts', value: promptsList.length, icon: <FileAudio className="w-5 h-5" />, color: '#2563EB', bg: '#EFF6FF' },
                { label: 'AI Generated', value: promptsList.filter(p => p.type === 'AI Generated').length, icon: <Sparkles className="w-5 h-5" />, color: '#8B5CF6', bg: '#F5F3FF' },
                { label: 'Uploaded', value: promptsList.filter(p => p.type === 'Uploaded').length, icon: <Upload className="w-5 h-5" />, color: '#06B6D4', bg: '#ECFEFF' },
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
                { value: langFilter, setter: setLangFilter, opts: ['All Languages', 'English (US)', 'Arabic (AR)'] },
                { value: typeFilter, setter: setTypeFilter, opts: ['All Types', 'AI Generated', 'Uploaded'] },
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
            <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-visible">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F9FAFB] border-b border-[#E5E7EB]">
                    <th className="w-10 px-4 py-3">
                      <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]"
                        onChange={handleSelectAll} checked={filtered.length > 0 && selectedIds.size === filtered.length} />
                    </th>
                    {['Prompt Name', 'Language', 'Duration', 'Type', 'Created By', ''].map(h => (
                      <th key={h} className="text-left px-4 py-3 text-[#9CA3AF] text-xs font-semibold uppercase tracking-wide whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#F3F4F6]">
                  {pageItems.map(p => (
                    <tr key={p.id} onClick={() => setSelected(p)}
                      className={`hover:bg-[#F9FAFB] transition-colors cursor-pointer group ${selected?.id === p.id ? 'bg-[#EFF6FF]' : ''}`}>
                      <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
                        <input type="checkbox" className="w-3.5 h-3.5 rounded border-[#D1D5DB] accent-[#2563EB]"
                          checked={selectedIds.has(p.id)} onChange={() => handleSelectRow(p.id)} />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2.5">
                          <button onClick={e => { 
                              e.stopPropagation(); 
                              if (selected?.id === p.id) {
                                togglePlay();
                              } else {
                                autoPlayRef.current = true;
                                setSelected(p);
                              }
                            }}
                            className="w-7 h-7 rounded-lg bg-[#EFF6FF] flex items-center justify-center text-[#2563EB] hover:bg-[#DBEAFE] transition-colors flex-shrink-0">
                            <Play className="w-3 h-3" />
                          </button>
                          <span className="text-[#1F2937] font-medium text-xs font-mono truncate max-w-[160px]">{p.name}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-[#374151] text-xs font-medium">{p.language}</td>
                      <td className="px-4 py-3 text-[#6B7280] text-xs font-mono">{p.duration}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full border text-[10px] font-semibold ${typeCls[p.type]}`}>
                          {p.type === 'AI Generated' ? <Sparkles className="w-2.5 h-2.5 mr-1" /> : <Upload className="w-2.5 h-2.5 mr-1" />}
                          {p.type}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-[#6B7280] text-xs">{p.createdBy}</td>
                      <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
                        <div className="relative">
                          <button onClick={() => setOpenMenu(openMenu === p.id ? null : p.id)}
                            className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors opacity-0 group-hover:opacity-100">
                            <MoreHorizontal className="w-4 h-4" />
                          </button>
                          {openMenu === p.id && <ActionMenu onClose={() => setOpenMenu(null)} onDelete={() => handleDelete(p.name)} onDownload={() => handleDownload(p.name)} onReplace={() => handleReplaceInit(p)} />}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="flex items-center justify-between px-5 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
                <p className="text-[#9CA3AF] text-xs">
                  Showing {filtered.length === 0 ? 0 : startIndex + 1} to {Math.min(startIndex + ITEMS_PER_PAGE, filtered.length)} of {filtered.length} prompts
                </p>
                <div className="flex gap-1 items-center">
                  <button onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={validCurrentPage === 1}
                    className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] disabled:opacity-40 disabled:cursor-not-allowed hover:bg-[#F9FAFB] transition-colors">
                    <ChevronLeft className="w-3.5 h-3.5" />
                  </button>
                  {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                    <button key={page} onClick={() => setCurrentPage(page)}
                      className={`w-7 h-7 rounded-lg border text-xs font-medium transition-colors ${validCurrentPage === page ? 'bg-[#2563EB] border-[#2563EB] text-white' : 'bg-white border-[#E5E7EB] text-[#374151] hover:bg-[#F9FAFB]'}`}>
                      {page}
                    </button>
                  ))}
                  <button onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={validCurrentPage === totalPages}
                    className="w-7 h-7 rounded-lg border border-[#E5E7EB] bg-white flex items-center justify-center text-[#9CA3AF] disabled:opacity-40 disabled:cursor-not-allowed hover:bg-[#F9FAFB] transition-colors">
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
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
                    <p className="text-[#9CA3AF] text-[10px] mt-0.5">{selected.duration} · {selected.size}</p>
                  </div>
                </div>

                {/* Waveform */}
                <div>
                  <div onClick={handleSeek} className="flex items-end gap-0.5 h-14 bg-[#F9FAFB] rounded-xl px-3 py-2 cursor-pointer border border-[#F3F4F6]">
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
                    <span className="text-[#9CA3AF] text-[10px] font-mono">{formatTime(currentTime)}</span>
                    <span className="text-[#9CA3AF] text-[10px] font-mono">{selected.duration}</span>
                  </div>
                </div>

                {/* File Details */}
                <div className="flex flex-col gap-2">
                  <div className="flex justify-between items-center py-2.5 border-b border-[#F3F4F6] last:border-0">
                    <span className="text-[#6B7280] text-[13px]">Created</span>
                    <span className="text-[#1F2937] text-[13px] font-medium">{new Date(selected.id).toLocaleDateString()}</span>
                  </div>
                </div>
                {selected.promptText && (
                  <div className="mt-4 p-3 bg-[#F9FAFB] rounded-lg border border-[#E5E7EB]">
                    <span className="text-[#6B7280] text-[13px] font-medium mb-1.5 block">Original Text Prompt</span>
                    <p className="text-[#1F2937] text-[13px] leading-relaxed break-words">{selected.promptText}</p>
                  </div>
                )}

                {/* Controls */}
                <div className="flex items-center justify-center gap-3">
                  <button onClick={() => handleSkip(-5)} className="w-8 h-8 rounded-full flex items-center justify-center text-[#9CA3AF] hover:text-[#374151] transition-colors" title="Rewind 5s">
                    <SkipBack className="w-4 h-4" />
                  </button>
                  <button onClick={togglePlay}
                    className="w-11 h-11 rounded-full bg-[#2563EB] flex items-center justify-center text-white hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/25">
                    {playing ? <Pause className="w-5 h-5" /> : <Play className="w-5 h-5 ml-0.5" />}
                  </button>
                  <button onClick={() => handleSkip(5)} className="w-8 h-8 rounded-full flex items-center justify-center text-[#9CA3AF] hover:text-[#374151] transition-colors" title="Forward 5s">
                    <SkipForward className="w-4 h-4" />
                  </button>
                </div>

                {/* Volume */}
                <div className="flex items-center gap-2">
                  <Volume2 className="w-3.5 h-3.5 text-[#9CA3AF] flex-shrink-0" />
                  <input type="range" min={0} max={100} value={volume} onChange={handleVolumeChange}
                    className="flex-1 h-1 bg-[#E5E7EB] rounded-lg appearance-none cursor-pointer accent-[#2563EB]" />
                  <span className="text-[#9CA3AF] text-[10px] w-7 text-right">{volume}%</span>
                </div>

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
              </div>
            </div>
          )}
        </div>
      </TenantLayout>

      {/* Upload/Replace Modal */}
      {modalConfig.isOpen && (
        <div className="fixed inset-0 z-[100] bg-black/40 flex items-center justify-center p-4 backdrop-blur-sm">
          <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl border border-[#E5E7EB] overflow-hidden flex flex-col transform transition-all">
            <div className="p-5 border-b border-[#F3F4F6] flex justify-between items-center bg-white">
              <h3 className="text-lg font-bold text-[#1F2937] flex items-center gap-2">
                {modalConfig.mode === 'upload' ? <Upload className="w-5 h-5 text-[#2563EB]" /> : modalConfig.mode === 'generate' ? <Sparkles className="w-5 h-5 text-[#8B5CF6]" /> : <RefreshCw className="w-5 h-5 text-[#2563EB]" />}
                {modalConfig.mode === 'upload' ? 'Upload Voice Prompt' : modalConfig.mode === 'generate' ? 'Generate AI Prompt' : 'Replace Voice Prompt'}
              </h3>
              <button onClick={() => setModalConfig({ isOpen: false, mode: 'upload' })} className="text-[#9CA3AF] hover:bg-[#F3F4F6] hover:text-[#374151] p-1.5 rounded-lg transition-colors"><X className="w-5 h-5" /></button>
            </div>
            
            <div className="p-6 space-y-5 bg-white">
              {modalConfig.mode === 'generate' ? (
                <div>
                  <label className="block text-sm font-semibold text-[#374151] mb-2">Prompt Text</label>
                  <textarea value={generateText} onChange={e => setGenerateText(e.target.value)} rows={4}
                    placeholder="Enter the text to synthesize..."
                    className="w-full rounded-lg border border-[#D1D5DB] focus:border-[#2563EB] focus:ring-4 focus:ring-[#2563EB]/10 outline-none p-3 text-sm transition-all resize-none" />
                </div>
              ) : (
                <div>
                  <label className="block text-sm font-semibold text-[#374151] mb-2">Audio File (.wav)</label>
                  <div className="flex items-center gap-3">
                    <button onClick={() => document.getElementById('modalFileInput')?.click()} className="px-4 py-2 bg-[#F9FAFB] text-[#374151] text-sm font-medium rounded-lg hover:bg-[#F3F4F6] hover:border-[#D1D5DB] transition-colors border border-[#E5E7EB] shadow-sm flex-shrink-0">
                      Choose File
                    </button>
                    <span className="text-sm text-[#6B7280] truncate font-medium">{modalFile ? modalFile.name : 'No file chosen'}</span>
                    <input id="modalFileInput" type="file" accept=".wav" className="hidden" onChange={handleModalFileChange} />
                  </div>
                </div>
              )}

              <div>
                <label className="block text-sm font-semibold text-[#374151] mb-2">Prompt Name</label>
                <div className="relative">
                  <input type="text" value={modalFileName} onChange={e => setModalFileName(e.target.value)} placeholder="e.g. welcome_message" 
                    className="w-full h-10 pl-3 pr-10 rounded-lg border border-[#D1D5DB] focus:border-[#2563EB] focus:ring-4 focus:ring-[#2563EB]/10 outline-none text-sm transition-all" />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-[#9CA3AF] pointer-events-none">.wav</span>
                </div>
                {modalConfig.mode === 'replace' && (
                  <p className="text-xs text-[#6B7280] mt-1.5 flex items-center gap-1">
                    Replacing: <span className="font-semibold text-[#374151]">{modalConfig.originalName}</span>
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-semibold text-[#374151] mb-2">Language</label>
                <div className="relative">
                  <select value={modalLanguage} onChange={e => setModalLanguage(e.target.value)} 
                    className="w-full h-10 px-3 pr-8 rounded-lg border border-[#D1D5DB] focus:border-[#2563EB] focus:ring-4 focus:ring-[#2563EB]/10 outline-none text-sm transition-all bg-white cursor-pointer appearance-none">
                    <option value="English (US)">English (US)</option>
                    <option value="Arabic (AR)">Arabic (AR)</option>
                  </select>
                  <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9CA3AF] pointer-events-none" />
                </div>
              </div>
            </div>

            <div className="p-5 border-t border-[#F3F4F6] bg-[#F9FAFB] flex justify-end gap-3">
              <button onClick={() => setModalConfig({ isOpen: false, mode: 'upload' })} className="px-5 py-2 rounded-lg text-[#4B5563] bg-white border border-[#D1D5DB] text-sm font-medium hover:bg-[#F9FAFB] hover:text-[#111827] transition-all shadow-sm">Cancel</button>
              <button onClick={submitModal} className="px-5 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] transition-all shadow-md shadow-[#2563EB]/20 flex items-center gap-2">
                {modalConfig.mode === 'upload' ? 'Upload Prompt' : modalConfig.mode === 'generate' ? 'Generate Prompt' : 'Replace Prompt'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
