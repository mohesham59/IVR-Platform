import { useState, useCallback, useRef } from 'react'
import {
  Undo2, Redo2, LayoutGrid, ShieldCheck, Save, Upload, Download,
  Sparkles, Wand2, LogOut, Bell, ChevronDown, Moon,
  Phone, Play, Square, ChevronUp, X, Send, Bot,
  Copy, Scissors, Trash2, EyeOff, Pencil, Group,
  AlertTriangle, CheckCircle, XCircle, Info, RefreshCw,
  GitBranch, Zap, Code,
} from 'lucide-react'
import FlowCanvas from '../ivr/FlowCanvas'
import NodeLibrary from '../ivr/NodeLibrary'
import PropertiesPanel from '../ivr/PropertiesPanel'
import { INITIAL_NODES, INITIAL_EDGES, VERSIONS } from '../ivr/initialFlow'
import { NODE_DEFS } from '../ivr/nodeConfig'
import type { FlowNode, FlowEdge, NodeType } from '../ivr/types'
import VxmlModal from '../components/VxmlModal'
import { generateVxml } from '../ivr/vxmlGenerator'
import { parseVxml } from '../ivr/vxmlParser'


type ValidationItem = { type: 'error' | 'warning' | 'info'; message: string; nodeId?: string }
const VALIDATION_ITEMS: ValidationItem[] = [
  { type: 'warning', message: 'Emergency Transfer node has no "Failed" output connected.', nodeId: 'n7' },
  { type: 'info', message: 'After-Hours VM node is not connected to an End node.', nodeId: 'n4' },
  { type: 'info', message: 'Consider adding a retry prompt to the DTMF Menu for invalid input.', nodeId: 'n5' },
]

const LOG_LINES = [
  { level: 'info', msg: 'Flow saved — v4 draft', time: '10:42:01' },
  { level: 'info', msg: 'Node "AI Assistant" configuration updated', time: '10:41:33' },
  { level: 'warn', msg: 'Emergency Transfer: no fallback path configured', time: '10:40:12' },
  { level: 'info', msg: 'Auto-layout applied to 13 nodes', time: '10:39:55' },
  { level: 'info', msg: 'Flow validated — 0 errors, 1 warning', time: '10:38:44' },
]

const AI_SUGGESTIONS = [
  { text: 'Add a "No Input" retry loop to the DTMF Menu', icon: '💡' },
  { text: 'Connect After-Hours VM to an End Call node', icon: '🔗' },
  { text: 'Add Emergency Transfer fallback to Voicemail', icon: '⚡' },
]

const AI_PROMPTS = [
  'Build an IVR for a Hospital',
  'Generate a Bank IVR',
  'Restaurant Reservation Flow',
  'Insurance Support Flow',
  'Hotel Concierge IVR',
]

const CONTEXT_MENU_ITEMS = [
  { icon: <Copy className="w-3.5 h-3.5" />, label: 'Duplicate', shortcut: '⌘D' },
  { icon: <Scissors className="w-3.5 h-3.5" />, label: 'Cut', shortcut: '⌘X' },
  { icon: <Copy className="w-3.5 h-3.5" />, label: 'Copy', shortcut: '⌘C' },
  null,
  { icon: <Pencil className="w-3.5 h-3.5" />, label: 'Rename', shortcut: 'F2' },
  { icon: <EyeOff className="w-3.5 h-3.5" />, label: 'Disable Node', shortcut: '' },
  { icon: <Group className="w-3.5 h-3.5" />, label: 'Group', shortcut: '⌘G' },
  null,
  { icon: <Trash2 className="w-3.5 h-3.5" />, label: 'Delete', shortcut: '⌫', danger: true },
]

type RightPanelTab = 'props' | 'versions' | 'validation'
type BottomTab = 'logs' | 'ai'

let nodeCounter = INITIAL_NODES.length + 1

export default function IVRBuilder({ onLogout }: { onLogout: () => void }) {
  const [nodes, setNodes] = useState<FlowNode[]>(INITIAL_NODES)
  const [edges] = useState<FlowEdge[]>(INITIAL_EDGES)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [libCollapsed, setLibCollapsed] = useState(false)
  const [rightTab, setRightTab] = useState<RightPanelTab>('props')
  const [bottomOpen, setBottomOpen] = useState(true)
  const [bottomTab, setBottomTab] = useState<BottomTab>('logs')
  const [simulatingId, setSimulatingId] = useState<string | null>(null)
  const [isSimulating, setIsSimulating] = useState(false)
  const [isSaved, setIsSaved] = useState(false)
  const [isPublished, setIsPublished] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)
  const [aiInput, setAiInput] = useState('')
  const [aiGenerating, setAiGenerating] = useState(false)
  const [aiMessages, setAiMessages] = useState<{ role: 'user' | 'ai'; text: string }[]>([
    { role: 'ai', text: 'Hi! I\'m your AI flow assistant. Describe an IVR and I\'ll generate the full flow, or ask me to improve the current one.' }
  ])
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; nodeId: string } | null>(null)
  const [vxmlModalOpen, setVxmlModalOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleExportVxml = () => {
    const vxml = generateVxml(nodes, edges, 'Hospital_Main_IVR')
    const blob = new Blob([vxml], { type: 'application/voicexml+xml;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'IVR-scenario.vxml'
    link.click()
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (event) => {
      const content = event.target?.result as string
      if (content) {
        try {
          const parsed = parseVxml(content)
          setNodes(parsed.nodes)
          alert('Successfully imported VoiceXML flow!')
        } catch (err: any) {
          alert('Failed to parse VXML file: ' + err.message)
        }
      }
    }
    reader.readAsText(file)
  }


  const selectedNode = nodes.find(n => n.id === selectedId) ?? null

  const handleMoveNode = useCallback((id: string, x: number, y: number) => {
    setNodes(ns => ns.map(n => n.id === id ? { ...n, x, y } : n))
  }, [])

  const handleDropNode = useCallback((type: string, x: number, y: number) => {
    const def = NODE_DEFS[type as NodeType]
    const id = `n${++nodeCounter}`
    const newNode: FlowNode = {
      id, type: type as NodeType, x, y,
      title: def.label,
      subtitle: def.description,
      status: 'idle',
      collapsed: false,
      disabled: false,
      ports: def.outputPorts.map(p => ({ ...p, type: 'output' })),
    }
    setNodes(ns => [...ns, newNode])
    setSelectedId(id)
  }, [])

  const handleCollapseNode = useCallback((id: string) => {
    setNodes(ns => ns.map(n => n.id === id ? { ...n, collapsed: !n.collapsed } : n))
  }, [])

  const handleContextMenu = useCallback((e: React.MouseEvent, nodeId: string) => {
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY, nodeId })
    setSelectedId(nodeId)
  }, [])

  const simulateFlow = () => {
    if (isSimulating) {
      setIsSimulating(false)
      setSimulatingId(null)
      if (simIntervalRef.current) clearInterval(simIntervalRef.current)
      return
    }
    const path = ['n1', 'n2', 'n3', 'n5', 'n6', 'n10']
    let i = 0
    setIsSimulating(true)
    setSimulatingId(path[0])
    simIntervalRef.current = window.setInterval(() => {
      i++
      if (i >= path.length) {
        setIsSimulating(false)
        setSimulatingId(null)
        clearInterval(simIntervalRef.current!)
        return
      }
      setSimulatingId(path[i])
    }, 900)
  }

  const handleSave = () => {
    setIsSaved(true)
    setTimeout(() => setIsSaved(false), 2000)
  }

  const handlePublish = () => {
    setIsPublished(true)
    setTimeout(() => setIsPublished(false), 2500)
  }

  const handleAiSend = () => {
    if (!aiInput.trim()) return
    const userMsg = aiInput
    setAiMessages(m => [...m, { role: 'user', text: userMsg }])
    setAiInput('')
    setAiGenerating(true)
    setTimeout(() => {
      setAiMessages(m => [...m, {
        role: 'ai',
        text: `Generating "${userMsg}" flow… I've created a complete IVR with Start → Business Hours → Greeting → DTMF Menu → relevant queues and an AI fallback. The flow has been added to your canvas.`,
      }])
      setAiGenerating(false)
    }, 1800)
  }

  return (
    <div className="flex flex-col h-screen bg-[#F8FAFC] overflow-hidden" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}
      onClick={() => { setContextMenu(null) }}>

      {/* ── TOP BAR ─────────────────────────────────────────────── */}
      <header className="h-12 bg-white border-b border-[#E5E7EB] flex items-center px-4 gap-2 flex-shrink-0 z-30">
        {/* Logo */}
        <div className="flex items-center gap-2.5 pr-4 border-r border-[#E5E7EB]">
          <div className="w-7 h-7 rounded-lg bg-[#2563EB] flex items-center justify-center flex-shrink-0">
            <Phone className="w-3.5 h-3.5 text-white" />
          </div>
          <div className="hidden sm:block">
            <div className="text-[#1F2937] font-bold text-xs leading-tight">NexusIVR</div>
            <div className="text-[#9CA3AF] text-[9px] leading-tight">IVR Builder</div>
          </div>
        </div>

        {/* Flow name */}
        <div className="flex items-center gap-2 px-3">
          <GitBranch className="w-3.5 h-3.5 text-[#9CA3AF]" />
          <span className="text-[#1F2937] font-semibold text-xs">Hospital Main IVR</span>
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-semibold bg-[#FEF9C3] text-[#A16207]">DRAFT</span>
        </div>

        {/* Separator */}
        <div className="w-px h-5 bg-[#E5E7EB] mx-1" />

        {/* Undo/Redo */}
        {[
          { icon: <Undo2 className="w-3.5 h-3.5" />, title: 'Undo (⌘Z)' },
          { icon: <Redo2 className="w-3.5 h-3.5" />, title: 'Redo (⌘⇧Z)' },
        ].map((btn, i) => (
          <button key={i} title={btn.title}
            className="w-7 h-7 rounded-lg flex items-center justify-center text-[#6B7280] hover:bg-[#F3F4F6] hover:text-[#1F2937] transition-colors">
            {btn.icon}
          </button>
        ))}

        <div className="w-px h-5 bg-[#E5E7EB] mx-1" />

        {/* Flow actions */}
        {[
          { icon: <LayoutGrid className="w-3.5 h-3.5" />, label: 'Auto Layout', title: 'Auto-arrange nodes' },
          { icon: <ShieldCheck className="w-3.5 h-3.5" />, label: 'Validate', title: 'Validate flow', onClick: () => setRightTab('validation') },
        ].map((btn) => (
          <button key={btn.label} onClick={btn.onClick} title={btn.title}
            className="flex items-center gap-1.5 h-7 px-2.5 rounded-lg text-[#6B7280] hover:bg-[#F3F4F6] hover:text-[#1F2937] transition-colors text-xs font-medium">
            {btn.icon}
            <span className="hidden md:inline">{btn.label}</span>
          </button>
        ))}

        {/* Simulate */}
        <button onClick={simulateFlow}
          className={`flex items-center gap-1.5 h-7 px-2.5 rounded-lg text-xs font-medium transition-all ${isSimulating ? 'bg-[#EF4444] text-white' : 'bg-[#F0FDF4] text-[#15803D] border border-[#BBF7D0] hover:bg-[#DCFCE7]'}`}>
          {isSimulating ? <Square className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
          <span className="hidden md:inline">{isSimulating ? 'Stop' : 'Preview'}</span>
          {isSimulating && <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse" />}
        </button>

        <div className="w-px h-5 bg-[#E5E7EB] mx-1" />

        {/* Hidden VXML file input */}
        <input type="file" ref={fileInputRef} onChange={handleFileUpload} accept=".vxml,.xml" className="hidden" />

        {/* VXML Standard Import / Export / View */}
        <button onClick={() => fileInputRef.current?.click()}
          className="flex items-center gap-1.5 h-7 px-2.5 rounded-lg text-[#6B7280] hover:bg-[#F3F4F6] hover:text-[#1F2937] transition-colors text-xs font-medium" title="Import VoiceXML (.vxml)">
          <Upload className="w-3.5 h-3.5" />
          <span className="hidden lg:inline">Import VXML</span>
        </button>

        <button onClick={handleExportVxml}
          className="flex items-center gap-1.5 h-7 px-2.5 rounded-lg text-[#6B7280] hover:bg-[#F3F4F6] hover:text-[#1F2937] transition-colors text-xs font-medium" title="Export VoiceXML (.vxml)">
          <Download className="w-3.5 h-3.5" />
          <span className="hidden lg:inline">Export VXML</span>
        </button>

        <button onClick={() => setVxmlModalOpen(true)}
          className="flex items-center gap-1.5 h-7 px-2.5 rounded-lg bg-[#EFF6FF] text-[#2563EB] border border-[#BFDBFE] hover:bg-[#DBEAFE] transition-colors text-xs font-semibold" title="View / Edit VoiceXML Source">
          <Code className="w-3.5 h-3.5" />
          <span className="hidden lg:inline">VXML Source</span>
        </button>


        <div className="w-px h-5 bg-[#E5E7EB] mx-1" />

        {/* AI Generate */}
        <button onClick={() => setAiOpen(!aiOpen)}
          className={`flex items-center gap-1.5 h-7 px-2.5 rounded-lg text-xs font-semibold transition-all ${aiOpen ? 'bg-[#7C3AED] text-white' : 'bg-gradient-to-r from-[#8B5CF6] to-[#2563EB] text-white hover:opacity-90'}`}>
          <Sparkles className="w-3.5 h-3.5" />
          <span className="hidden md:inline">AI Generate</span>
        </button>

        <button
          className="flex items-center gap-1.5 h-7 px-2.5 rounded-lg text-xs font-medium text-[#6B7280] hover:bg-[#F3F4F6] transition-colors border border-[#E5E7EB]">
          <Wand2 className="w-3.5 h-3.5" />
          <span className="hidden lg:inline">AI Improve</span>
        </button>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Save / Publish */}
        <button onClick={handleSave}
          className={`flex items-center gap-1.5 h-7 px-3 rounded-lg text-xs font-medium transition-all ${isSaved ? 'bg-[#DCFCE7] text-[#15803D] border border-[#BBF7D0]' : 'bg-white border border-[#E5E7EB] text-[#374151] hover:border-[#2563EB] hover:text-[#2563EB]'}`}>
          {isSaved ? <CheckCircle className="w-3.5 h-3.5" /> : <Save className="w-3.5 h-3.5" />}
          {isSaved ? 'Saved!' : 'Save'}
        </button>

        <button onClick={handlePublish}
          className={`flex items-center gap-1.5 h-7 px-3 rounded-lg text-xs font-semibold transition-all shadow-sm ${isPublished ? 'bg-[#22C55E] text-white' : 'bg-[#2563EB] text-white hover:bg-[#1E40AF] shadow-[#2563EB]/20'}`}>
          {isPublished ? <CheckCircle className="w-3.5 h-3.5" /> : <Zap className="w-3.5 h-3.5" />}
          {isPublished ? 'Published!' : 'Publish'}
        </button>

        <div className="w-px h-5 bg-[#E5E7EB] mx-2" />

        {/* User controls */}
        <button onClick={() => setDarkMode(!darkMode)}
          className={`w-7 h-7 rounded-lg flex items-center justify-center transition-colors ${darkMode ? 'bg-[#1F2937] text-white' : 'text-[#6B7280] hover:bg-[#F3F4F6]'}`}>
          <Moon className="w-3.5 h-3.5" />
        </button>
        <button className="relative w-7 h-7 rounded-lg flex items-center justify-center text-[#6B7280] hover:bg-[#F3F4F6] transition-colors">
          <Bell className="w-3.5 h-3.5" />
          <span className="absolute top-1 right-1 w-1.5 h-1.5 rounded-full bg-[#EF4444]" />
        </button>
        <button className="flex items-center gap-1.5 pl-1.5 pr-2.5 py-1 rounded-lg hover:bg-[#F3F4F6] transition-colors">
          <div className="w-6 h-6 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center text-white text-[9px] font-bold">MW</div>
          <ChevronDown className="w-3 h-3 text-[#9CA3AF]" />
        </button>
        <button onClick={onLogout} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:text-[#EF4444] hover:bg-[#FEF2F2] transition-colors">
          <LogOut className="w-3.5 h-3.5" />
        </button>
      </header>

      {/* ── MAIN BODY ────────────────────────────────────────────── */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left: Node Library */}
        <NodeLibrary collapsed={libCollapsed} onToggle={() => setLibCollapsed(!libCollapsed)} />

        {/* Center: Canvas + Bottom Panel */}
        <div className="flex-1 flex flex-col min-w-0 overflow-hidden relative">
          {/* Canvas */}
          <div className="flex-1 overflow-hidden relative">
            <FlowCanvas
              nodes={nodes}
              edges={edges}
              selectedId={selectedId}
              simulatingId={simulatingId}
              onSelectNode={setSelectedId}
              onMoveNode={handleMoveNode}
              onDropNode={handleDropNode}
              onCollapseNode={handleCollapseNode}
              onContextMenu={handleContextMenu}
            />

            {/* Simulation overlay banner */}
            {isSimulating && (
              <div className="absolute top-3 left-1/2 -translate-x-1/2 z-30 flex items-center gap-2.5 bg-[#2563EB] text-white px-4 py-2 rounded-xl shadow-lg shadow-[#2563EB]/30">
                <span className="w-2 h-2 rounded-full bg-white animate-ping" />
                <span className="text-xs font-semibold">Simulating call flow…</span>
                <button onClick={() => { setIsSimulating(false); setSimulatingId(null) }}
                  className="text-white/70 hover:text-white transition-colors">
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}

            {/* AI Assistant floating panel */}
            {aiOpen && (
              <div className="absolute top-3 right-3 z-40 w-80 bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl shadow-black/15 flex flex-col overflow-hidden"
                style={{ maxHeight: '420px' }}>
                <div className="flex items-center gap-2.5 px-4 py-3 border-b border-[#F3F4F6]"
                  style={{ background: 'linear-gradient(135deg, #EDE9FE, #EFF6FF)' }}>
                  <div className="w-7 h-7 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center">
                    <Bot className="w-4 h-4 text-white" />
                  </div>
                  <div className="flex-1">
                    <p className="text-[#1F2937] font-semibold text-xs">AI Flow Assistant</p>
                    <p className="text-[#9CA3AF] text-[10px]">Powered by GPT-4o</p>
                  </div>
                  <button onClick={() => setAiOpen(false)}
                    className="text-[#9CA3AF] hover:text-[#374151] transition-colors">
                    <X className="w-4 h-4" />
                  </button>
                </div>

                {/* Messages */}
                <div className="flex-1 overflow-y-auto p-3 space-y-2.5">
                  {aiMessages.map((msg, i) => (
                    <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[85%] px-3 py-2 rounded-xl text-xs leading-relaxed ${
                        msg.role === 'user'
                          ? 'bg-[#2563EB] text-white rounded-br-sm'
                          : 'bg-[#F3F4F6] text-[#374151] rounded-bl-sm'
                      }`}>
                        {msg.text}
                      </div>
                    </div>
                  ))}
                  {aiGenerating && (
                    <div className="flex justify-start">
                      <div className="bg-[#F3F4F6] rounded-xl rounded-bl-sm px-3 py-2 flex items-center gap-1.5">
                        {[0, 1, 2].map(d => (
                          <span key={d} className="w-1.5 h-1.5 rounded-full bg-[#9CA3AF] animate-bounce"
                            style={{ animationDelay: `${d * 0.15}s` }} />
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {/* Quick prompts */}
                <div className="px-3 py-2 border-t border-[#F3F4F6] flex gap-1.5 flex-wrap">
                  {AI_PROMPTS.slice(0, 3).map(p => (
                    <button key={p} onClick={() => setAiInput(p)}
                      className="text-[9px] font-medium bg-[#EFF6FF] text-[#2563EB] border border-[#BFDBFE] rounded-full px-2 py-0.5 hover:bg-[#DBEAFE] transition-colors">
                      {p}
                    </button>
                  ))}
                </div>

                {/* Quick action buttons */}
                <div className="px-3 py-2 flex gap-2">
                  {[
                    { icon: <RefreshCw className="w-3 h-3" />, label: 'Improve' },
                    { icon: <Wand2 className="w-3 h-3" />, label: 'Optimize' },
                    { icon: <Info className="w-3 h-3" />, label: 'Explain' },
                  ].map(b => (
                    <button key={b.label}
                      className="flex-1 flex items-center justify-center gap-1 h-6 rounded-lg bg-[#F5F3FF] text-[#7C3AED] text-[10px] font-medium hover:bg-[#EDE9FE] transition-colors">
                      {b.icon}{b.label}
                    </button>
                  ))}
                </div>

                {/* Input */}
                <div className="flex items-center gap-2 px-3 py-3 border-t border-[#F3F4F6]">
                  <input
                    value={aiInput}
                    onChange={e => setAiInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleAiSend()}
                    placeholder="Describe your IVR flow…"
                    className="flex-1 h-8 px-2.5 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#8B5CF6] focus:ring-2 focus:ring-[#8B5CF6]/10 transition-all"
                  />
                  <button onClick={handleAiSend}
                    className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center text-white hover:opacity-90 transition-opacity flex-shrink-0 shadow-md">
                    <Send className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* ── BOTTOM PANEL ──────────────────────────────────────── */}
          <div className={`bg-white border-t border-[#E5E7EB] flex-shrink-0 transition-all duration-200 ${bottomOpen ? 'h-44' : 'h-9'}`}>
            {/* Bottom tab bar */}
            <div className="flex items-center gap-0 h-9 border-b border-[#F3F4F6] px-2">
              {[
                { id: 'logs' as const, label: 'Execution Logs', icon: <Info className="w-3.5 h-3.5" /> },
                { id: 'ai' as const, label: 'AI Suggestions', icon: <Sparkles className="w-3.5 h-3.5" />, badge: AI_SUGGESTIONS.length },
              ].map(tab => (
                <button key={tab.id} onClick={() => { setBottomTab(tab.id); setBottomOpen(true) }}
                  className={`flex items-center gap-1.5 h-9 px-3 text-xs font-medium border-b-2 -mb-px transition-colors ${bottomTab === tab.id && bottomOpen ? 'border-[#2563EB] text-[#2563EB]' : 'border-transparent text-[#6B7280] hover:text-[#374151]'}`}>
                  {tab.icon}{tab.label}
                  {tab.badge && <span className="w-4 h-4 rounded-full bg-[#8B5CF6] text-white text-[9px] font-bold flex items-center justify-center">{tab.badge}</span>}
                </button>
              ))}

              {/* Validation quick summary */}
              <div className="flex items-center gap-2 ml-3 pl-3 border-l border-[#F3F4F6]">
                {VALIDATION_ITEMS.filter(i => i.type === 'error').length > 0
                  ? <span className="flex items-center gap-1 text-[#EF4444] text-[10px] font-medium"><XCircle className="w-3 h-3" />{VALIDATION_ITEMS.filter(i => i.type === 'error').length} errors</span>
                  : <span className="flex items-center gap-1 text-[#22C55E] text-[10px] font-medium"><CheckCircle className="w-3 h-3" />No errors</span>}
                {VALIDATION_ITEMS.filter(i => i.type === 'warning').length > 0 && (
                  <span className="flex items-center gap-1 text-[#F59E0B] text-[10px] font-medium">
                    <AlertTriangle className="w-3 h-3" />{VALIDATION_ITEMS.filter(i => i.type === 'warning').length} warnings
                  </span>
                )}
              </div>

              <div className="flex-1" />
              <span className="text-[#9CA3AF] text-[10px] pr-2">{nodes.length} nodes · {edges.length} connections</span>
              <button onClick={() => setBottomOpen(!bottomOpen)}
                className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] hover:text-[#374151] transition-colors">
                {bottomOpen ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronUp className="w-3.5 h-3.5" />}
              </button>
            </div>

            {/* Bottom content */}
            {bottomOpen && (
              <div className="h-[calc(100%-36px)] overflow-y-auto px-3 py-2">
                {bottomTab === 'logs' && (
                  <div className="space-y-1">
                    {LOG_LINES.map((line, i) => (
                      <div key={i} className="flex items-start gap-2.5 py-0.5">
                        <span className="text-[#9CA3AF] text-[10px] font-mono w-16 flex-shrink-0">{line.time}</span>
                        <span className={`text-[10px] font-bold uppercase w-8 flex-shrink-0 ${line.level === 'warn' ? 'text-[#F59E0B]' : 'text-[#9CA3AF]'}`}>{line.level}</span>
                        <span className="text-[#374151] text-[11px]">{line.msg}</span>
                      </div>
                    ))}
                  </div>
                )}
                {bottomTab === 'ai' && (
                  <div className="space-y-2">
                    {AI_SUGGESTIONS.map((s, i) => (
                      <div key={i} className="flex items-center gap-3 p-2.5 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] bg-[#FAFAFA] transition-colors">
                        <span className="text-base flex-shrink-0">{s.icon}</span>
                        <span className="text-[#374151] text-xs flex-1">{s.text}</span>
                        <button className="flex items-center gap-1 text-[#2563EB] text-[10px] font-semibold hover:underline flex-shrink-0">Apply</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Right: Properties Panel */}
        <PropertiesPanel
          selectedNode={selectedNode}
          versions={VERSIONS}
          validationItems={VALIDATION_ITEMS}
          activeTab={rightTab}
          onTabChange={setRightTab}
        />
      </div>

      {/* ── CONTEXT MENU ─────────────────────────────────────────── */}
      {contextMenu && (
        <div
          className="fixed z-[9999] bg-white rounded-xl border border-[#E5E7EB] shadow-2xl shadow-black/15 py-1.5 w-44 overflow-hidden"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={e => e.stopPropagation()}
        >
          {CONTEXT_MENU_ITEMS.map((item, i) =>
            item === null
              ? <div key={i} className="h-px bg-[#F3F4F6] my-1 mx-2" />
              : (
                <button key={item.label} onClick={() => setContextMenu(null)}
                  className={`w-full flex items-center gap-2.5 px-3.5 py-1.5 text-xs font-medium hover:bg-[#F9FAFB] transition-colors ${(item as any).danger ? 'text-[#EF4444]' : 'text-[#374151]'}`}>
                  {item.icon}
                  <span className="flex-1 text-left">{item.label}</span>
                  {item.shortcut && <span className="text-[#D1D5DB] text-[10px]">{item.shortcut}</span>}
                </button>
              )
          )}
        </div>
      )}

      {/* ── TOAST: published ─────────────────────────────────────── */}
      {isPublished && (
        <div className="fixed top-16 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-2.5 bg-[#22C55E] text-white px-5 py-2.5 rounded-xl shadow-lg shadow-[#22C55E]/30 animate-in">
          <CheckCircle className="w-4 h-4" />
          <span className="text-sm font-semibold">Flow published successfully!</span>
        </div>
      )}

      {/* VoiceXML Modal */}
      <VxmlModal
        isOpen={vxmlModalOpen}
        onClose={() => setVxmlModalOpen(false)}
        nodes={nodes}
        edges={edges}
        onImportVxml={(code) => {
          const parsed = parseVxml(code)
          setNodes(parsed.nodes)
        }}
      />
    </div>
  )
}
