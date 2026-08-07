import { useState, useEffect, useMemo } from 'react'
import { aiApi } from '../api/aiApi'
import { useAIAssistant, type GenerationStage, type Message } from '../hooks/useAIAssistant'
import {
  Send, Bot, Plus, Trash2, ChevronRight, ChevronDown, ChevronUp,
  Wand2, FileText, ExternalLink, X, CheckCircle,
  AlertTriangle, XCircle, Loader2, Sparkles,
  Paperclip, Mic,
} from 'lucide-react'

function GenerationStepper({ stage }: { stage: GenerationStage }) {
  const stages: { key: GenerationStage; label: string; icon: typeof Loader2 }[] = [
    { key: 'understanding', label: 'Understanding request', icon: Sparkles },
    { key: 'analysis', label: 'Business analysis', icon: Sparkles },
    { key: 'planning', label: 'Planning flow', icon: Sparkles },
    { key: 'template', label: 'Selecting template/domain', icon: Sparkles },
    { key: 'generating', label: 'Generating VXML', icon: Loader2 },
    { key: 'validating', label: 'Validating', icon: CheckCircle },
    { key: 'converting', label: 'Converting to nodes', icon: Sparkles },
    { key: 'rendering', label: 'Rendering canvas', icon: Sparkles },
  ]

  if (stage === 'idle') return null

  const activeIndex = stages.findIndex(s => s.key === stage)

  return (
    <div className="px-3 py-2.5 border-b border-[#F3F4F6] bg-gradient-to-r from-[#EFF6FF] to-[#F5F3FF]">
      <p className="text-[10px] font-bold text-[#2563EB] uppercase tracking-wider mb-2 flex items-center gap-1.5">
        <Loader2 className="w-3 h-3 animate-spin" />
        Generating Flow
      </p>
      <div className="space-y-1.5">
        {stages.map((s, i) => {
          const isDone = i < activeIndex
          const isActive = i === activeIndex
          return (
            <div key={s.key} className="flex items-center gap-2">
              <div className={`w-4 h-4 rounded-full flex items-center justify-center flex-shrink-0 ${
                isDone ? 'bg-[#22C55E]' : isActive ? 'bg-[#2563EB] animate-pulse' : 'bg-[#E5E7EB]'
              }`}>
                {isDone ? <CheckCircle className="w-2.5 h-2.5 text-white" /> :
                 isActive ? <Loader2 className="w-2.5 h-2.5 text-white animate-spin" /> :
                 <div className="w-1.5 h-1.5 rounded-full bg-white" />}
              </div>
              <span className={`text-[11px] ${isActive ? 'text-[#2563EB] font-bold' : isDone ? 'text-[#22C55E]' : 'text-[#9CA3AF]'}`}>
                {s.label}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function ChatBubble({
  msg,
  onInspect,
  isSelected,
  onApplyFlow,
  onOpenInBuilder,
}: {
  msg: Message
  onInspect?: () => void
  isSelected?: boolean
  onApplyFlow?: (flow?: { nodes?: any[]; edges?: any[]; flowName?: string }) => void
  onOpenInBuilder?: (flow?: { nodes?: any[]; edges?: any[]; flowName?: string }) => void
}) {
  const isUser = msg.role === 'user'
  return (
    <div className={`flex gap-2.5 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {!isUser && (
        <div className="w-6 h-6 rounded-lg bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center flex-shrink-0 mt-0.5">
          <Bot className="w-3 h-3 text-white" />
        </div>
      )}
      {isUser && (
        <div className="w-6 h-6 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center flex-shrink-0 mt-0.5 text-white text-[8px] font-bold">
          YOU
        </div>
      )}
      <div className={`max-w-[85%] space-y-1 ${isUser ? 'items-end' : 'items-start'} flex flex-col`}>
        <div className={`px-3 py-2 rounded-2xl text-xs leading-relaxed ${
          isUser
            ? 'bg-[#2563EB] text-white rounded-br-sm'
            : 'bg-white border border-[#E5E7EB] text-[#374151] rounded-bl-sm shadow-sm'
        }`}>
          {msg.text.split('\n').map((line, i) => (
            <p key={i} className={i > 0 ? 'mt-1' : ''}>
              {line.startsWith('•') ? (
                <span className="flex items-start gap-1">
                  <span className={`mt-1 w-1 h-1 rounded-full flex-shrink-0 ${isUser ? 'bg-white/60' : 'bg-[#2563EB]'}`} />
                  <span>{line.slice(2)}</span>
                </span>
              ) : (
                line.replace(/\*\*(.*?)\*\*/g, '$1')
              )}
            </p>
          ))}
        </div>

        {msg.type === 'flow-preview' && !!msg.extra && (
          <div className="w-full space-y-2">
            {/* Domain badge */}
            {msg.domain && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#F5F3FF] border border-[#DDD6FE] text-[#7C3AED] text-[10px] font-bold">
                🔮 {msg.domain}
              </span>
            )}
            {/* Flow structure strip */}
            <div className="flex items-center gap-1 flex-wrap">
              {(msg.extra as any).structure?.map((n: any, i: number) => (
                <div key={i} className="flex items-center gap-1">
                  <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-md border text-[10px] font-medium"
                    style={{ borderColor: n.color + '55', backgroundColor: n.color + '11', color: n.color }}>
                    <span>{n.icon}</span>
                    <span className="hidden sm:inline">{n.label}</span>
                  </div>
                  {i < (msg.extra as any).structure.length - 1 && <ChevronRight className="w-2.5 h-2.5 text-[#D1D5DB] flex-shrink-0" />}
                </div>
              ))}
            </div>
            {/* Stats */}
            <div className="grid grid-cols-4 border border-[#E5E7EB] rounded-lg overflow-hidden">
              {[
                { label: 'Nodes', value: (msg.extra as any).nodes },
                { label: 'Complexity', value: (msg.extra as any).complexity },
                { label: 'Duration', value: (msg.extra as any).duration },
                { label: 'Score', value: `${(msg.extra as any).score}%` },
              ].map(s => (
                <div key={s.label} className="px-2 py-1.5 text-center border-r border-[#F3F4F6] last:border-r-0">
                  <p className="text-[#1F2937] font-bold text-xs">{s.value}</p>
                  <p className="text-[#9CA3AF] text-[9px]">{s.label}</p>
                </div>
              ))}
            </div>
            {/* Voice Prompts */}
            <div>
              <p className="text-[9px] font-bold text-[#9CA3AF] uppercase tracking-wider mb-1">Voice Prompts</p>
              <div className="flex flex-wrap gap-1">
                {(msg.extra as any).voicePrompts?.map((p: string) => (
                  <span key={p} className="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-[#F3F4F6] text-[#374151] text-[9px] font-medium">
                    🔊 {p}
                  </span>
                ))}
              </div>
            </div>
            {/* Actions */}
            <div className="flex gap-1.5">
              <button onClick={() => {
                const ex = msg.extra as any
                onApplyFlow?.({ nodes: ex.flowNodes, edges: ex.flowEdges, flowName: ex.flowName })
              }} className="flex-1 flex items-center justify-center gap-1 py-1.5 rounded-lg bg-[#2563EB] text-white text-[10px] font-bold hover:bg-[#1E40AF] transition-colors">
                <CheckCircle className="w-3 h-3" /> Use This Flow
              </button>
              <button onClick={() => {
                const ex = msg.extra as any
                onOpenInBuilder?.({ nodes: ex.flowNodes, edges: ex.flowEdges, flowName: ex.flowName })
              }} className="flex items-center justify-center gap-1 px-2 py-1.5 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-[10px] font-medium hover:border-[#2563EB] hover:text-[#2563EB]">
                <ExternalLink className="w-3 h-3" /> Open
              </button>
            </div>
          </div>
        )}

        {msg.snapshotId && (
          <div className="flex gap-2 mt-0.5 px-0.5 items-center">
            <span className={`text-[9px] px-1.5 py-0.5 rounded-full font-bold border ${
              isSelected ? 'bg-[#EEF2FF] border-[#818CF8] text-[#4F46E5]' : 'bg-white border-[#E5E7EB] text-[#6B7280]'
            }`}>
              V{msg.version}
            </span>
            <button onClick={onInspect} className={`text-[9px] font-semibold transition-colors ${
              isSelected ? 'text-[#4F46E5] underline cursor-default' : 'text-[#6B7280] hover:text-[#4F46E5] hover:underline'
            }`}>
              {isSelected ? 'Inspecting' : 'Inspect version'}
            </button>
          </div>
        )}
        <span className="text-[#9CA3AF] text-[9px] px-0.5">{msg.ts}</span>
      </div>
    </div>
  )
}

export default function AiAssistantPanel({ onClose, onFlowGenerated }: { onClose?: () => void; onFlowGenerated?: (flow: { nodes: any[]; edges: any[]; flowName: string }) => void }) {
  const {
    messages,
    input,
    setInput,
    isTyping,
    sessions,
    sessionId,
    latestFlow,
    selectedProvider,
    setSelectedProvider,
    providers,
    quotaWarnings,
    validationResult,
    selectedMessageId,
    generationStage,
    messagesEndRef,
    sendMessage,
    handleNewChat,
    handleDeleteSession,
    handleSelectSession,
    handleInspectMessage,
    handleOpenInBuilder,
    handleApplyFlow,
    handleImproveFlow,
    handleValidateFlow,
    handleExportJson,
    extractVoicePrompts,
  } = useAIAssistant({
    onFlowGenerated: onFlowGenerated ?? undefined,
  })

  const [sessionsCollapsed, setSessionsCollapsed] = useState(false)
  const [rightTab, _setRightTab] = useState<'summary' | 'voice' | 'actions'>('summary')
  const [miniPanelCollapsed, setMiniPanelCollapsed] = useState(true)

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose?.()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])

  const flowForPanel = useMemo(() => {
    if (latestFlow) return latestFlow
    const flowMsg = messages.filter(m => m.type === 'flow-preview').reverse()[0]
    return flowMsg?.extra as any
  }, [latestFlow, messages])
  const flowNodes = flowForPanel?.nodes ?? []
  const flowName = flowForPanel?.flowName || 'New IVR Flow Session'
  const voicePrompts = flowForPanel ? extractVoicePrompts(flowForPanel.nodes) : []
  const complexity = flowNodes.length > 8 ? 'High' : flowNodes.length > 4 ? 'Medium' : 'Low'
  const validationIssues = validationResult?.issues ?? []

  return (
    <div className="h-full flex flex-col bg-white border-l border-[#E5E7EB] shadow-xl shadow-black/5">
      {/* ── Header ────────────────────────────────────────────── */}
      <div className="flex-shrink-0 border-b border-[#F3F4F6]">
        <div className="flex items-center justify-between px-3 py-2.5">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center">
              <Bot className="w-3.5 h-3.5 text-white" />
            </div>
            <div>
              <h3 className="text-xs font-bold text-[#1F2937] leading-tight">AI Assistant</h3>
              <div className="flex items-center gap-1 mt-0.5">
                <span className="w-1 h-1 rounded-full bg-[#22C55E]" />
                <span className="text-[9px] text-[#9CA3AF] capitalize">{selectedProvider} AI</span>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <div className="flex items-center bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-2 py-1">
              <span className="text-[10px] mr-1">🤖</span>
              <select
                value={selectedProvider}
                onChange={e => {
                  const val = e.target.value
                  setSelectedProvider(val)
                  localStorage.setItem('ai_provider', val)
                  aiApi.setProvider(val).catch(() => {})
                }}
                className="bg-transparent text-[10px] font-bold text-[#374151] outline-none cursor-pointer capitalize"
              >
                {Object.keys(providers).map(p => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
            </div>
            <button
              onClick={handleNewChat}
              className="w-6 h-6 rounded-md flex items-center justify-center bg-[#EFF6FF] text-[#2563EB] hover:bg-[#DBEAFE] transition-colors"
              title="New Chat"
            >
              <Plus className="w-3 h-3" />
            </button>
            <button
              onClick={onClose}
              className="w-6 h-6 rounded-md flex items-center justify-center text-[#9CA3AF] hover:text-[#EF4444] hover:bg-[#FEF2F2] transition-colors"
              title="Close panel"
            >
              <X className="w-3 h-3" />
            </button>
          </div>
        </div>

        {/* Quota warning */}
        {quotaWarnings.length > 0 && (
          <div className="px-3 pb-2">
            <div className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg bg-[#FFFBEB] border border-[#FDE68A]">
              <AlertTriangle className="w-3 h-3 text-[#D97706] flex-shrink-0" />
              <span className="text-[10px] text-[#92400E] font-medium">API quota limits reached. Using fallback.</span>
            </div>
          </div>
        )}
      </div>

      {/* ── Generation Stepper ───────────────────────────────── */}
      <GenerationStepper stage={generationStage} />

      {/* ── Sessions Collapsible Bar ─────────────────────────── */}
      <div className="flex-shrink-0 border-b border-[#F3F4F6]">
        <button
          onClick={() => setSessionsCollapsed(c => !c)}
          className="w-full flex items-center justify-between px-3 py-1.5 hover:bg-[#F9FAFB] transition-colors"
        >
          <span className="text-[10px] font-bold text-[#9CA3AF] uppercase tracking-wider">
            Conversations ({sessions.length})
          </span>
          {sessionsCollapsed ? <ChevronDown className="w-3 h-3 text-[#9CA3AF]" /> : <ChevronUp className="w-3 h-3 text-[#9CA3AF]" />}
        </button>
        {!sessionsCollapsed && (
          <div className="max-h-24 overflow-y-auto px-2 pb-2 space-y-0.5">
            {sessions.length === 0 ? (
              <p className="text-[10px] text-[#9CA3AF] text-center py-1">No conversations yet</p>
            ) : (
              sessions.map(s => (
                <div
                  key={s.id}
                  onClick={() => handleSelectSession(s.id)}
                  className={`group flex items-center justify-between px-2 py-1.5 rounded-lg cursor-pointer transition-colors ${
                    sessionId === s.id ? 'bg-[#EFF6FF]' : 'hover:bg-[#F9FAFB]'
                  }`}
                >
                  <div className="flex items-center gap-1.5 min-w-0 flex-1">
                    <Bot className={`w-3 h-3 flex-shrink-0 ${sessionId === s.id ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}`} />
                    <div className="min-w-0">
                      <p className={`text-[11px] font-medium truncate ${sessionId === s.id ? 'text-[#2563EB]' : 'text-[#374151]'}`}>{s.title}</p>
                      <p className="text-[9px] text-[#9CA3AF]">{s.ts}</p>
                    </div>
                  </div>
                  <button
                    onClick={(e) => handleDeleteSession(e, s.id)}
                    className="opacity-0 group-hover:opacity-100 p-0.5 text-[#9CA3AF] hover:text-[#EF4444] rounded transition-all flex-shrink-0"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* ── Messages ─────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto px-3 py-3 space-y-3">
        {messages.length === 0 && (
          <div className="text-center py-6 text-[#9CA3AF]">
            <Bot className="w-8 h-8 mx-auto mb-1.5 text-[#8B5CF6]" />
            <p className="text-xs font-medium text-[#374151]">Start a conversation</p>
            <p className="text-[10px] mt-0.5">Describe an IVR flow or ask for improvements.</p>
          </div>
        )}
            {messages.map(msg => (
              <ChatBubble
                key={msg.id}
                msg={msg}
                onInspect={() => handleInspectMessage(msg)}
                isSelected={selectedMessageId === msg.id}
                onApplyFlow={(flow) => handleApplyFlow(flow)}
                onOpenInBuilder={(flow) => handleOpenInBuilder(flow)}
              />
            ))}
        {isTyping && (
          <div className="flex gap-2.5">
            <div className="w-6 h-6 rounded-lg bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center flex-shrink-0">
              <Bot className="w-3 h-3 text-white" />
            </div>
            <div className="bg-white border border-[#E5E7EB] rounded-2xl rounded-bl-sm px-3 py-2 shadow-sm flex items-center gap-1">
              {[0, 1, 2].map(d => (
                <span key={d} className="w-1.5 h-1.5 rounded-full bg-[#9CA3AF] animate-bounce"
                  style={{ animationDelay: `${d * 0.15}s` }} />
              ))}
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* ── Quick Suggestions ───────────────────────────────── */}
      <div className="flex-shrink-0 px-3 pt-2 pb-1 border-t border-[#F3F4F6] bg-white">
        <div className="flex gap-1 items-center justify-between flex-wrap mb-2">
          <div className="flex gap-1 flex-wrap">
            {['🏥 Hospital', '🏦 Banking', '🏨 Hotel', '🛡️ Insurance', '🎓 University', '🍕 Restaurant'].map(s => (
              <button
                key={s}
                onClick={() => setInput(s.split(' ').slice(1).join(' '))}
                className="px-2 py-1 rounded-full bg-[#F3F4F6] hover:bg-[#EFF6FF] hover:text-[#2563EB] border border-transparent hover:border-[#BFDBFE] text-[#374151] text-[10px] font-medium transition-all"
              >
                {s.split(' ')[0]} {s.split(' ').slice(1).join(' ')}
              </button>
            ))}
          </div>
        </div>

        {/* Input */}
        <div className="flex items-end gap-1.5 pb-2">
          <div className="flex-1 relative">
            <textarea
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  sendMessage()
                }
              }}
              placeholder="Describe an IVR flow…"
              rows={1}
              className="w-full px-3 py-2 pr-16 rounded-xl border border-[#E5E7EB] bg-white text-xs text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all resize-none"
            />
            <div className="absolute right-2 bottom-1.5 flex gap-1">
              <button className="text-[#9CA3AF] hover:text-[#374151] transition-colors p-0.5">
                <Paperclip className="w-3 h-3" />
              </button>
              <button className="text-[#9CA3AF] hover:text-[#374151] transition-colors p-0.5">
                <Mic className="w-3 h-3" />
              </button>
            </div>
          </div>
          <button
            onClick={() => sendMessage()}
            disabled={!input.trim()}
            className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center text-white hover:opacity-90 transition-opacity shadow-lg disabled:opacity-40 flex-shrink-0"
          >
            <Send className="w-3.5 h-3.5" />
          </button>
        </div>
        <p className="text-[#9CA3AF] text-[9px] text-center pb-1">Enter to send · Shift+Enter for new line</p>
      </div>

      {/* ── Right Mini Panel (Flow Summary, Validation, Voice, Actions) ── */}
      <div className="flex-shrink-0 border-t border-[#E5E7EB] bg-[#F9FAFB]">
        <button
          onClick={() => setMiniPanelCollapsed(c => !c)}
          className="w-full flex items-center justify-between px-3 py-1.5 hover:bg-[#F9FAFB] transition-colors"
        >
          <span className="text-[10px] font-bold text-[#9CA3AF] uppercase tracking-wider">Flow Summary</span>
          {miniPanelCollapsed ? <ChevronDown className="w-3 h-3 text-[#9CA3AF]" /> : <ChevronUp className="w-3 h-3 text-[#9CA3AF]" />}
        </button>
        {!miniPanelCollapsed && (
        <div className="px-3 py-2.5 max-h-48 overflow-y-auto">
          {rightTab === 'summary' && (
            <div className="space-y-2.5">
              <div className="flex items-center justify-between">
                <p className="text-[10px] font-bold text-[#1F2937] truncate max-w-[70%]">{flowName}</p>
                <span className="text-[9px] text-[#9CA3AF]">Draft</span>
              </div>
              {validationResult?.templateFallback && (
                <div className="p-2 rounded-lg bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] text-[10px] flex items-start gap-1.5 shadow-sm">
                  <AlertTriangle className="w-3.5 h-3.5 text-[#D97706] flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="font-semibold text-[#B45309]">Template Fallback Mode</p>
                    <p className="text-[9px] text-[#B45309]/90 mt-0.5">
                      All AI providers offline. Generated using built-in template rules, not AI assistance.
                    </p>
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-1.5">
                {[
                  { label: 'Nodes', value: String(flowNodes.length), color: '#2563EB' },
                  { label: 'Complexity', value: complexity, color: flowNodes.length > 8 ? '#EF4444' : '#F59E0B' },
                  { label: 'Duration', value: '2–4 min', color: '#22C55E' },
                  { label: 'Score', value: validationResult?.templateFallback ? 'N/A' : (validationResult ? (validationResult.score !== undefined ? `${validationResult.score}%` : (validationResult.valid && validationIssues.length === 0 ? '100%' : `${Math.max(50, 100 - validationIssues.length * 5)}%`)) : '95%'), color: validationResult?.templateFallback ? '#D97706' : '#8B5CF6' },
                ].map(s => (
                  <div key={s.label} className="p-2 rounded-lg border border-[#E5E7EB] bg-white">
                    <div className="text-xs font-bold" style={{ color: s.color }}>{s.value}</div>
                    <div className="text-[9px] text-[#9CA3AF]">{s.label}</div>
                  </div>
                ))}
              </div>

              {/* Validation issues */}
              <div>
                <p className="text-[9px] font-bold text-[#9CA3AF] uppercase tracking-wider mb-1">Validation</p>
                {validationResult?.templateFallback ? (
                  <div className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg bg-[#FFFBEB] border border-[#FDE68A]">
                    <AlertTriangle className="w-3 h-3 text-[#D97706] flex-shrink-0" />
                    <span className="text-[10px] text-[#B45309] font-medium">Valid (Built-in Template Fallback)</span>
                  </div>
                ) : validationIssues.length === 0 ? (
                  <div className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg bg-[#F0FDF4] border border-[#BBF7D0]">
                    <CheckCircle className="w-3 h-3 text-[#22C55E] flex-shrink-0" />
                    <span className="text-[10px] text-[#15803D]">Valid (Clean)</span>
                  </div>
                ) : validationResult?.valid ? (
                  <div className="space-y-1 max-h-24 overflow-y-auto">
                    <div className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-[#FFFBEB] border border-[#FDE68A] text-[10px] text-[#B45309] font-medium">
                      <AlertTriangle className="w-3 h-3 text-[#D97706] flex-shrink-0" />
                      <span>Valid ({validationIssues.length} warning{validationIssues.length === 1 ? '' : 's'})</span>
                    </div>
                    {validationIssues.map((issue, idx) => (
                      <div key={idx} className="flex items-start gap-1 px-2 py-1 rounded-md text-[10px] bg-[#FFFBEB] border border-[#FDE68A] text-[#A16207]">
                        <AlertTriangle className="w-3 h-3 flex-shrink-0 mt-0.5" />
                        <span className="flex-1 leading-tight">{issue.message}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="space-y-1 max-h-24 overflow-y-auto">
                    {validationIssues.map((issue, idx) => (
                      <div key={idx} className={`flex items-start gap-1 px-2 py-1 rounded-md text-[10px] ${
                        issue.severity === 'error' ? 'bg-[#FEF2F2] border border-[#FCA5A5] text-[#DC2626]' : 'bg-[#FFFBEB] border border-[#FDE68A] text-[#A16207]'
                      }`}>
                        {issue.severity === 'error' ? <XCircle className="w-3 h-3 flex-shrink-0 mt-0.5" /> : <AlertTriangle className="w-3 h-3 flex-shrink-0 mt-0.5" />}
                        <span className="flex-1 leading-tight">{issue.message}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {rightTab === 'voice' && (
            <div className="space-y-1.5">
              <p className="text-[9px] font-bold text-[#9CA3AF] uppercase tracking-wider">Voice Prompts</p>
              {voicePrompts.length === 0 ? (
                <p className="text-[10px] text-[#9CA3AF]">Generate a flow first.</p>
              ) : (
                <div className="space-y-1 max-h-28 overflow-y-auto">
                  {voicePrompts.map(p => (
                    <div key={p} className="flex items-center gap-1.5 px-2 py-1 rounded-md border border-[#E5E7EB] bg-white">
                      <div className="w-4 h-4 rounded bg-[#EFF6FF] flex items-center justify-center text-[10px] flex-shrink-0">🔊</div>
                      <span className="text-[10px] text-[#374151] flex-1 truncate">{p}</span>
                      <CheckCircle className="w-3 h-3 text-[#22C55E] flex-shrink-0" />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {rightTab === 'actions' && (
            <div className="space-y-1.5">
              <button onClick={() => handleOpenInBuilder()} disabled={!latestFlow}
                className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg bg-[#2563EB] text-white text-[10px] font-bold hover:bg-[#1E40AF] disabled:opacity-40 transition-colors">
                <ExternalLink className="w-3 h-3" /> Open in Builder
              </button>
              <button onClick={handleImproveFlow} disabled={!latestFlow || isTyping}
                className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg bg-gradient-to-r from-[#8B5CF6] to-[#2563EB] text-white text-[10px] font-bold hover:opacity-90 disabled:opacity-40 transition-opacity">
                <Wand2 className="w-3 h-3" /> Improve Flow
              </button>
              <button onClick={handleValidateFlow} disabled={!latestFlow}
                className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-[10px] font-medium hover:border-[#2563EB] hover:text-[#2563EB] disabled:opacity-40 transition-all">
                <CheckCircle className="w-3 h-3" /> Validate Flow
              </button>
              <button onClick={handleExportJson} disabled={!latestFlow}
                className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-[10px] font-medium hover:border-[#2563EB] hover:text-[#2563EB] disabled:opacity-40 transition-all">
                <FileText className="w-3 h-3" /> Export JSON
              </button>
            </div>
          )}
        </div>
        )}  
      </div>  
    </div>
  )
}
