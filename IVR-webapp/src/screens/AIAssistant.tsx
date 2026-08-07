import { useState, useRef } from 'react'
import TenantLayout from '../components/TenantLayout'
import QuotaWarningBanner from '../components/QuotaWarningBanner'
import {
  Send, Square, Bot, Clock, Plus, Trash2,
  ChevronRight, GitBranch, Mic, Paperclip, RefreshCw,
  Download, CheckCircle, AlertTriangle, XCircle, Copy,
  ExternalLink, X,
} from 'lucide-react'
import {
  useAIAssistant,
  type Message,
  extractVoicePrompts,
} from '../hooks/useAIAssistant'
import { exportAsVxml } from '../ivr/vxmlExporter'
import { GenerationStepper } from '../components/GenerationStepper'
import { aiApi } from '../api/aiApi'

function FlowPreviewCard({
  extra,
  onOpenInBuilder,
  sessionId,
}: {
  extra: {
    nodes: number
    complexity: string
    duration: string
    score: number
    voicePrompts: string[]
    structure: Array<{ icon: string; label: string; color: string }>
    flowNodes?: any[]
    flowEdges?: any[]
    flowName?: string
  }
  onOpenInBuilder?: () => void
  sessionId?: string
}) {
  const [vxmlCopied, setVxmlCopied] = useState(false)

  const handleCopyVxml = async () => {
    let nodes = extra.flowNodes ?? []
    let edges = extra.flowEdges ?? []
    let name  = extra.flowName  ?? 'IVR Flow'

    if (sessionId) {
      const stored = localStorage.getItem(`nexus_flow_${sessionId}`)
      if (stored) {
        try {
          const parsed = JSON.parse(stored)
          if (parsed && parsed.nodes && parsed.nodes.length > 0) {
            nodes = parsed.nodes
            edges = parsed.edges || []
            name  = parsed.flowName || name
          }
        } catch { /* ignore */ }
      }
    }

    try {
      const flowJsonStr = JSON.stringify({ name, nodes, edges })
      const { vxml } = await aiApi.exportVxml(flowJsonStr)
      navigator.clipboard.writeText(vxml).then(() => {
        setVxmlCopied(true)
        setTimeout(() => setVxmlCopied(false), 2000)
      }).catch(() => {
        // clipboard unavailable (e.g. non-https) — silently ignore
      })
    } catch (err) {
      console.error('[FlowPreviewCard] Failed to copy VXML:', err)
    }
  }

  return (
    <div className="mt-3 rounded-xl border border-[#E5E7EB] bg-white overflow-hidden shadow-sm">
      <div className="flex items-center gap-2.5 px-4 py-3 border-b border-[#F3F4F6] bg-gradient-to-r from-[#EFF6FF] to-[#F5F3FF]">
        <GitBranch className="w-4 h-4 text-[#2563EB]" />
        <span className="text-[#1F2937] font-semibold text-sm">Generated Flow Preview</span>
        <span className="ml-auto inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#DCFCE7] border border-[#BBF7D0] text-[#15803D] text-[10px] font-bold">
          <CheckCircle className="w-3 h-3" />
          Valid
        </span>
      </div>
      <div className="px-4 py-3 flex items-center gap-1 flex-wrap">
        {extra.structure.map((n, i) => (
          <div key={i} className="flex items-center gap-1">
            <div className="flex items-center gap-1.5 px-2 py-1 rounded-lg border text-xs font-medium"
              style={{ borderColor: n.color + '55', backgroundColor: n.color + '11', color: n.color }}>
              <span>{n.icon}</span>
              <span className="hidden sm:inline">{n.label}</span>
            </div>
            {i < extra.structure.length - 1 && <ChevronRight className="w-3 h-3 text-[#D1D5DB] flex-shrink-0" />}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-4 border-t border-[#F3F4F6]">
        {[
          { label: 'Nodes', value: extra.nodes },
          { label: 'Complexity', value: extra.complexity },
          { label: 'Avg Duration', value: extra.duration },
          { label: 'Score', value: `${extra.score}%` },
        ].map(s => (
          <div key={s.label} className="px-3 py-2.5 text-center border-r border-[#F3F4F6] last:border-r-0">
            <p className="text-[#1F2937] font-bold text-sm">{s.value}</p>
            <p className="text-[#9CA3AF] text-[10px]">{s.label}</p>
          </div>
        ))}
      </div>
      <div className="px-4 py-3 border-t border-[#F3F4F6]">
        <p className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Voice Prompts</p>
        <div className="flex flex-wrap gap-1.5">
          {extra.voicePrompts.map(p => (
            <span key={p} className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-[#F3F4F6] text-[#374151] text-[10px] font-medium">
              🔊 {p}
            </span>
          ))}
        </div>
      </div>
      <div className="flex gap-2 px-4 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
        <button onClick={onOpenInBuilder}
          className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/20">
          <ExternalLink className="w-3.5 h-3.5" /> Open in Builder
        </button>
        <button
          onClick={handleCopyVxml}
          className={`flex items-center gap-1.5 px-3 py-2 rounded-lg border text-xs font-medium transition-all ${
            vxmlCopied
              ? 'bg-[#F0FDF4] border-[#BBF7D0] text-[#15803D]'
              : 'bg-white border-[#E5E7EB] text-[#374151] hover:border-[#2563EB] hover:text-[#2563EB]'
          }`}
        >
          {vxmlCopied
            ? <><CheckCircle className="w-3.5 h-3.5" /> Copied!</>
            : <><Copy className="w-3.5 h-3.5" /> Copy VXML</>}
        </button>
      </div>
    </div>
  )
}

function ChatBubble({
  msg,
  onOpenInBuilder,
  onInspect,
  isSelected,
  sessionId,
}: {
  msg: Message
  onOpenInBuilder?: (flow?: any) => void
  onInspect?: () => void
  isSelected?: boolean
  sessionId?: string
}) {
  const isUser = msg.role === 'user'
  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {!isUser && (
        <div className="w-7 h-7 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center flex-shrink-0 mt-1">
          <Bot className="w-4 h-4 text-white" />
        </div>
      )}
      {isUser && (
        <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center flex-shrink-0 mt-1 text-white text-[9px] font-bold">
          USER
        </div>
      )}
      <div className={`max-w-[78%] space-y-1 ${isUser ? 'items-end' : 'items-start'} flex flex-col`}>
        <div className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
          isUser
            ? 'bg-[#2563EB] text-white rounded-br-sm'
            : 'bg-white border border-[#E5E7EB] text-[#374151] rounded-bl-sm shadow-sm'
        }`}>
          {msg.text.split('\n').map((line, i) => (
            <p key={i} className={i > 0 ? 'mt-1.5' : ''}>
              {line.startsWith('•') ? (
                <span className="flex items-start gap-1.5">
                  <span className={`mt-1 w-1.5 h-1.5 rounded-full flex-shrink-0 ${isUser ? 'bg-white/60' : 'bg-[#2563EB]'}`} />
                  <span>{line.slice(2)}</span>
                </span>
              ) : (
                line.replace(/\*\*(.*?)\*\*/g, '$1')
              )}
            </p>
          ))}
        </div>

        {msg.type === 'flow-preview' && !!msg.extra && (
          <div className="w-full">
            <FlowPreviewCard
              extra={msg.extra as any}
              onOpenInBuilder={() => {
                const ex = msg.extra as any
                onOpenInBuilder?.({ nodes: ex.flowNodes, edges: ex.flowEdges, flowName: ex.flowName })
              }}
              sessionId={sessionId}
            />
          </div>
        )}

        {msg.snapshotId && (
          <div className="flex gap-2.5 mt-1 px-1 items-center">
            <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold border ${
              isSelected
                ? 'bg-[#EEF2FF] border-[#818CF8] text-[#4F46E5]'
                : 'bg-white border-[#E5E7EB] text-[#6B7280]'
            }`}>
              Version V{msg.version}
            </span>
            <button
              onClick={onInspect}
              className={`text-[10px] font-semibold transition-colors ${
                isSelected
                  ? 'text-[#4F46E5] underline cursor-default'
                  : 'text-[#6B7280] hover:text-[#4F46E5] hover:underline'
              }`}
            >
              {isSelected ? 'Inspecting Snapshot' : 'Inspect this version'}
            </button>
          </div>
        )}
        <span className="text-[#9CA3AF] text-[10px] px-1">{msg.ts}</span>
      </div>
    </div>
  )
}

export function formatProviderAttemptsSummary(
  attempts: import('../api/aiApi').ProviderAttempt[] | undefined,
  actualProviderUsed: string | null,
  selectedProvider: string
): string {
  const provName = (p?: string) => (p ? p.charAt(0).toUpperCase() + p.slice(1) : 'Provider');

  if (!attempts || attempts.length === 0) {
    const isTemplate = actualProviderUsed === 'template-generator';
    if (isTemplate) {
      return `${provName(selectedProvider)} unavailable. Showing a basic template response instead — try again shortly or switch providers manually.`;
    }
    return `${provName(selectedProvider)} unavailable. Response generated using ${actualProviderUsed}.`;
  }

  const rateLimited: { name: string; cooldownSecs: number }[] = [];
  const timedOut: string[] = [];
  const otherFailed: { name: string; reason: string }[] = [];

  for (const att of attempts) {
    const name = provName(att.provider);
    if (att.status === 429 || (att.reason && att.reason.toLowerCase().includes('quota'))) {
      rateLimited.push({ name, cooldownSecs: att.cooldownSeconds || 300 });
    } else if (att.status === 0 || (att.reason && att.reason.toLowerCase().includes('time'))) {
      timedOut.push(name);
    } else {
      otherFailed.push({ name, reason: att.reason || 'failed' });
    }
  }

  const parts: string[] = [];
  if (rateLimited.length > 0) {
    const names = rateLimited.map(r => r.name).join(' and ');
    const maxCooldownMin = Math.max(...rateLimited.map(r => Math.max(1, Math.round(r.cooldownSecs / 60))));
    parts.push(`${names} ${rateLimited.length > 1 ? 'are' : 'is'} rate-limited (quota exceeded, ~${maxCooldownMin} min cooldown)`);
  }
  if (timedOut.length > 0) {
    const names = timedOut.join(' and ');
    parts.push(`${names} timed out`);
  }
  if (otherFailed.length > 0) {
    const names = otherFailed.map(o => o.name).join(' and ');
    parts.push(`${names} unreachable (${otherFailed[0].reason})`);
  }

  const isTemplateFallback = actualProviderUsed === 'template-generator';
  let message = parts.join('. ') + '.';
  if (isTemplateFallback) {
    message = '⚠️ All AI providers are currently unavailable. This flow was generated using a basic template and may not fully reflect your request. Please try again shortly for a fully AI-generated flow.';
  } else if (actualProviderUsed) {
    message += ` Response generated using ${actualProviderUsed}.`;
  }

  return message;
}

export default function AIAssistant({ onLogout }: { onLogout: () => void }) {
  const {
    messages,
    input,
    setInput,
    isTyping,
    sessions,
    sessionId,
    latestFlow,
    selectedProvider,
    providers,
    quotaWarnings,
    actualProviderUsed,
    providerAttempts,
    validationResult,
    selectedMessageId,
    selectedVersion,
    generationStage,
    messagesEndRef,
    sendMessage,
    handleNewChat,
    handleDeleteSession,
    handleSelectSession,
    handleInspectMessage,
    handleResetToCurrentCanvas,
    handleOpenInBuilder,
    handleExportVxml,
    setSelectedProvider,
    historyError,
    isListening,
    voiceError,
    toggleVoiceInput,
    handleFileUpload,
    attachedFileName,
    clearAttachedFile,
    stopGeneration,
  } = useAIAssistant()

  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const rightPanel = (
    <div className="w-72 bg-white border-l border-[#E5E7EB] flex-shrink-0 flex flex-col">
      <div className="px-4 py-3.5 border-b border-[#F3F4F6]">
        <p className="text-[#1F2937] font-semibold text-sm">Flow Summary</p>
        <p className="text-[#9CA3AF] text-xs mt-0.5">{latestFlow?.flowName ? latestFlow.flowName : 'New IVR Flow Session'} · Draft</p>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-5">
        <div className="grid grid-cols-2 gap-2.5">
          {[
            { label: 'Nodes', value: latestFlow ? `${latestFlow.nodes.length}` : '0', color: '#2563EB', bg: '#EFF6FF' },
            { label: 'Complexity', value: latestFlow ? (latestFlow.nodes.length > 8 ? 'High' : 'Medium') : 'None', color: '#F59E0B', bg: '#FFFBEB' },
            { label: 'Avg Duration', value: '2–4 min', color: '#22C55E', bg: '#F0FDF4' },
            { label: 'Valid. Score', value: validationResult ? (validationResult.score !== undefined ? `${validationResult.score}%` : (validationResult.valid ? '100%' : `${Math.max(50, 100 - validationResult.issues.length * 10)}%`)) : 'N/A', color: '#8B5CF6', bg: '#F5F3FF' },
          ].map(s => (
            <div key={s.label} className="p-3 rounded-xl border border-[#F3F4F6] bg-[#F9FAFB]">
              <div className="text-base font-bold" style={{ color: s.color }}>{s.value}</div>
              <div className="text-[#9CA3AF] text-[10px] mt-0.5">{s.label}</div>
            </div>
          ))}
        </div>
        <section>
          <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Validation</h4>
          <div className="space-y-1.5">
            {validationResult?.issues && validationResult.issues.length > 0 ? (
              validationResult.issues.map((issue, idx) => (
                <div key={idx} className={`flex items-center gap-2 p-2 rounded-lg border text-[11px] ${
                  issue.severity === 'error' ? 'bg-[#FEF2F2] border-[#FCA5A5] text-[#DC2626]' : 'bg-[#FFFBEB] border-[#FDE68A] text-[#A16207]'
                }`}>
                  {issue.severity === 'error' ? <XCircle className="w-3.5 h-3.5 flex-shrink-0" /> : <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />}
                  <span>{issue.message}</span>
                </div>
              ))
            ) : (
              <div className="flex items-center gap-2 p-2 rounded-lg bg-[#F0FDF4] border border-[#BBF7D0]">
                <CheckCircle className="w-3.5 h-3.5 text-[#22C55E] flex-shrink-0" />
                <span className="text-[#15803D] text-[11px]">Flow is valid with no errors</span>
              </div>
            )}
          </div>
        </section>
        <section>
          <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Suggested Voice Prompts</h4>
          <div className="space-y-1.5">
            {latestFlow ? (
              extractVoicePrompts(latestFlow.nodes).map(p => (
                <div key={p} className="flex items-center gap-2 p-2 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] bg-white transition-colors">
                  <div className="w-6 h-6 rounded-md bg-[#EFF6FF] flex items-center justify-center text-xs flex-shrink-0">🔊</div>
                  <span className="text-[#374151] text-[11px] flex-1 truncate">{p}</span>
                  <CheckCircle className="w-3 h-3 text-[#22C55E] flex-shrink-0" />
                </div>
              ))
            ) : (
              <p className="text-xs text-[#9CA3AF]">Generate a flow to extract voice prompts.</p>
            )}
          </div>
        </section>
        <section className="space-y-2 pt-1">
          <button onClick={() => handleOpenInBuilder()} disabled={!latestFlow}
            className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1E40AF] disabled:opacity-50 transition-colors shadow-md shadow-[#2563EB]/20">
            <ExternalLink className="w-3.5 h-3.5" /> Open in IVR Builder
          </button>
          <button onClick={handleExportVxml} disabled={!latestFlow}
            className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] disabled:opacity-50 transition-all">
            <Download className="w-3.5 h-3.5" /> Download VXML
          </button>
        </section>
      </div>
    </div>
  )

  return (
    <TenantLayout activeNav="ai" onLogout={onLogout} pageTitle="" pageSubtitle="">
      <div className="-mx-6 -mt-10 flex h-[calc(100vh-64px)] overflow-hidden">
        <div className="w-56 bg-white border-r border-[#E5E7EB] flex flex-col flex-shrink-0">
          <div className="flex items-center justify-between px-4 py-3.5 border-b border-[#F3F4F6]">
            <p className="text-[#1F2937] font-semibold text-sm">Conversations</p>
            <button onClick={handleNewChat} className="w-7 h-7 rounded-lg flex items-center justify-center bg-[#EFF6FF] text-[#2563EB] hover:bg-[#DBEAFE] transition-colors" title="New Chat">
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto py-2">
            <p className="px-4 pt-1 pb-1 text-[9px] font-semibold uppercase tracking-wider text-[#9CA3AF] flex items-center gap-1">
              <Clock className="w-3 h-3" /> Recent Sessions
            </p>
            {historyError && (
              <div className="mx-3 mb-2 px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-red-700">
                <p className="text-[10px] font-medium">{historyError}</p>
              </div>
            )}
            {sessions.map(s => (
              <div key={s.id} onClick={() => handleSelectSession(s.id)}
                className={`group w-full flex items-center justify-between px-3 py-2 text-left cursor-pointer transition-colors ${sessionId === s.id ? 'bg-[#EFF6FF]' : 'hover:bg-[#F9FAFB]'}`}>
                <div className="flex items-start gap-2 min-w-0 flex-1">
                  <Bot className={`w-3.5 h-3.5 flex-shrink-0 mt-0.5 ${sessionId === s.id ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}`} />
                  <div className="flex-1 min-w-0">
                    <p className={`text-xs font-medium truncate ${sessionId === s.id ? 'text-[#2563EB]' : 'text-[#374151]'}`}>{s.title}</p>
                    <p className="text-[#9CA3AF] text-[10px]">{s.ts}</p>
                  </div>
                </div>
                <button onClick={(e) => handleDeleteSession(e, s.id)}
                  className="opacity-0 group-hover:opacity-100 p-1 text-[#9CA3AF] hover:text-[#EF4444] rounded transition-all"
                  title="Delete Chat">
                  <Trash2 className="w-3 h-3" />
                </button>
              </div>
            ))}
          </div>
        </div>
        <div className="flex-1 flex flex-col min-w-0 bg-[#F8FAFC]">
          <div className="flex items-center gap-3 px-5 py-3 bg-white border-b border-[#E5E7EB]">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center">
              <Bot className="w-4 h-4 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <p className="text-[#1F2937] font-semibold text-sm">AI Flow Assistant</p>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold border bg-purple-50 border-purple-200 text-purple-700 capitalize">
                  {selectedProvider} AI
                </span>
                {selectedVersion ? (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold border bg-blue-50 border-blue-200 text-blue-700">
                    Inspecting V{selectedVersion}
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold border bg-green-50 border-green-200 text-green-700">
                    Active Canvas
                  </span>
                )}
              </div>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className="w-1.5 h-1.5 rounded-full bg-purple-500" />
                <p className="text-[#9CA3AF] text-[10px] capitalize">
                  {selectedProvider && actualProviderUsed && selectedProvider !== actualProviderUsed
                    ? <>Provider: {selectedProvider} / Used: {actualProviderUsed} (Fallback)</>
                    : <>Provider: {selectedProvider}</>}
                  {selectedVersion && ' · Viewing History Snapshot'}
                </p>
              </div>
            </div>
            {selectedVersion && (
              <button onClick={handleResetToCurrentCanvas} className="ml-3 text-[10px] text-blue-600 hover:text-blue-800 font-semibold px-2 py-1 bg-blue-50 hover:bg-blue-100 border border-blue-200 rounded-lg transition-all">
                Back to Canvas
              </button>
            )}
            <div className="ml-auto flex items-center gap-2">
              <div className="flex items-center gap-1.5 bg-[#F9FAFB] border border-[#E5E7EB] rounded-lg px-2.5 py-1">
                <span className="text-xs">🤖</span>
                <select
                  value={selectedProvider}
                  onChange={e => setSelectedProvider(e.target.value)}
                  className="bg-transparent text-xs font-semibold text-[#374151] outline-none cursor-pointer capitalize"
                >
                  {Object.keys(providers).filter(p => p.toLowerCase() !== 'mock').map(prov => (
                    <option key={prov} value={prov}>{prov}</option>
                  ))}
                </select>
              </div>
              <button onClick={handleNewChat} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                <RefreshCw className="w-3.5 h-3.5" /> New Chat
              </button>
            </div>
          </div>
          <div className="px-5 pt-3 space-y-2">
            <QuotaWarningBanner warnings={quotaWarnings} />
            {selectedProvider && actualProviderUsed && (selectedProvider !== actualProviderUsed || actualProviderUsed === 'template-generator') && (
              <div className={`flex items-start gap-2 px-3 py-2 rounded-lg border ${
                actualProviderUsed === 'template-generator'
                  ? 'bg-amber-50 border-amber-200 text-amber-900'
                  : 'bg-blue-50 border-blue-200 text-blue-900'
              }`}>
                <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                <span className="text-xs font-medium leading-relaxed">
                  {formatProviderAttemptsSummary(providerAttempts, actualProviderUsed, selectedProvider)}
                </span>
              </div>
            )}
          </div>
          <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
            {messages.length === 0 && (
              <div className="text-center py-12 text-[#9CA3AF]">
                <Bot className="w-10 h-10 mx-auto mb-2 text-[#8B5CF6]" />
                <p className="text-sm font-medium text-[#374151]">Start a new conversation</p>
                <p className="text-xs mt-1">Select a prompt below or describe your IVR flow to generate it with AI.</p>
              </div>
            )}
            {messages.map(msg => (
              <ChatBubble
                key={msg.id}
                msg={msg}
                onOpenInBuilder={(flow) => handleOpenInBuilder(flow)}
                onInspect={() => handleInspectMessage(msg)}
                isSelected={selectedMessageId === msg.id}
                sessionId={sessionId}
              />
            ))}
            {isTyping && (
              <div className="flex gap-3">
                <div className="w-7 h-7 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center flex-shrink-0 mt-1">
                  <Bot className="w-4 h-4 text-white" />
                </div>
                {generationStage !== 'idle' ? (
                  <div className="max-w-[80%]">
                    <GenerationStepper stage={generationStage} />
                  </div>
                ) : (
                  <div className="bg-white border border-[#E5E7EB] rounded-2xl rounded-bl-sm px-4 py-3 shadow-sm flex items-center gap-1.5">
                    {[0, 1, 2].map(d => (
                      <span key={d} className="w-2 h-2 rounded-full bg-[#9CA3AF] animate-bounce"
                        style={{ animationDelay: `${d * 0.15}s` }} />
                    ))}
                  </div>
                )}
              </div>
            )}
            <div ref={messagesEndRef as any} />
          </div>
          <div className="px-5 py-3 border-t border-[#E5E7EB] bg-white">
            <div className="flex gap-2 items-center justify-between flex-wrap mb-3">
              <div className="flex gap-2 flex-wrap">
                {[
                  { label: '🏥 Hospital Appointment Booking', prompt: 'Create a comprehensive hospital IVR system for appointment booking. Include departments for Appointments, Pharmacy, Billing, and Triage. Features: greeting, main menu with options 1-Appointments 2-Pharmacy 3-Billing 4-Triage 0-Nurse Line, appointment scheduling with date/time selection, queue for high call volume, transfer to emergency or nurse line, business hours check, and professional closing.' },
                  { label: '🏨 Hotel Concierge', prompt: 'Create a professional hotel concierge IVR. Include departments for Reservations, Front Desk, Room Service, Housekeeping, and Concierge. Features: welcome greeting, main menu with options 1-Reservations 2-Front Desk 3-Room Service 4-Housekeeping 0-Concierge, room reservation with date selection, room service ordering, housekeeping requests, business hours awareness, and polite closing.' },
                  { label: '🏦 Banking IVR', prompt: 'Create a secure banking IVR system with authentication. Include departments for Billing, Cards, Loans, and Agent. Features: security greeting, account number and PIN authentication, main menu with options 1-Balance 2-Cards 3-Loans 4-Agent 0-End, balance inquiry with last 4 digits of account, card services menu, loan status check, transfer to fraud hotline or live agent, session timeout handling, and secure closing.' },
                  { label: '📞 Telecom Customer Support', prompt: 'Create a telecom customer support IVR. Include departments for Billing, Roaming, SIM Support, and Broadband. Features: greeting, main menu with options 1-Billing 2-Roaming 3-SIM Support 4-Internet 0-Specialist, outage reporting, technical support troubleshooting, plan changes, SIM activation, broadband speed test, transfer to specialist, and call recording notice.' },
                  { label: '🎓 University Helpdesk', prompt: 'Create a university helpdesk IVR. Include departments for Admissions, Financial Aid, Student Services, and Security. Features: academic greeting, main menu with options 1-Admissions 2-Financial Aid 3-Student Services 4-Emergency 0-Main Desk, admission application status, financial aid inquiry, campus safety alerts, emergency contacts, business hours, and transfer to main desk.' },
                  { label: '🍕 Pizza Restaurant', prompt: 'Create a pizza restaurant IVR for orders and reservations. Include departments for Takeout Orders, Reservations, and Hostess. Features: welcome greeting, main menu with options 1-Takeout Orders 2-Reservations 3-Hours & Location 0-Hostess, order taking with size/topping selection, reservation booking with date/time, store hours and location information, transfer to hostess for large parties, and thank you closing.' },
                ].map(s => (
                  <button key={s.label} onClick={() => setInput(s.prompt)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[#F3F4F6] hover:bg-[#EFF6FF] hover:text-[#2563EB] border border-transparent hover:border-[#BFDBFE] text-[#374151] text-xs font-medium transition-all">
                    <span>{s.label.split(' ')[0]}</span> {s.label.split(' ').slice(1).join(' ')}
                  </button>
                ))}
              </div>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".txt,.json,.xml,.vxml,.md,.csv"
              className="hidden"
              onChange={e => {
                const file = e.target.files?.[0]
                if (file) {
                  handleFileUpload(file)
                  e.target.value = ''
                }
              }}
            />
            {attachedFileName && (
              <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-[#EFF6FF] border border-[#BFDBFE] text-[#1D4ED8] text-xs mb-2 w-fit">
                <Paperclip className="w-3.5 h-3.5 flex-shrink-0" />
                <span className="truncate max-w-[250px] font-medium">{attachedFileName} attached</span>
                <button
                  type="button"
                  onClick={clearAttachedFile}
                  className="ml-1 text-[#1D4ED8]/70 hover:text-[#1D4ED8]"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}
            {isListening && (
              <div className="flex items-center gap-2 px-2.5 py-1 rounded-lg bg-[#FEF2F2] border border-[#FCA5A5] text-[#DC2626] text-xs mb-2 w-fit animate-pulse">
                <span className="w-2 h-2 rounded-full bg-[#EF4444]" />
                <span className="font-medium">Listening... Speak into your microphone</span>
              </div>
            )}
            <div className="flex items-end gap-2">
              <div className="flex-1 relative">
                <textarea
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter' && !e.shiftKey && !isTyping) {
                      e.preventDefault()
                      sendMessage()
                    }
                  }}
                  placeholder="Describe an IVR flow, ask for improvements, or generate voice prompts…"
                  rows={2}
                  className="w-full px-4 py-3 pr-16 rounded-xl border border-[#E5E7EB] bg-white text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all resize-none"
                />
                <div className="absolute right-3 bottom-3 flex gap-1.5">
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    title="Attach flow spec file (.json, .vxml, .txt, .md, .csv)"
                    className={`transition-colors p-1 rounded-md ${
                      attachedFileName
                        ? 'text-[#2563EB] bg-[#EFF6FF]'
                        : 'text-[#9CA3AF] hover:text-[#374151] hover:bg-[#F3F4F6]'
                    }`}
                  >
                    <Paperclip className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={toggleVoiceInput}
                    title={
                      isListening
                        ? 'Listening... Click to stop'
                        : voiceError
                        ? voiceError
                        : 'Voice Input (Speech to Text)'
                    }
                    className={`transition-colors p-1 rounded-md ${
                      isListening
                        ? 'text-[#EF4444] bg-[#FEF2F2] animate-pulse ring-2 ring-[#EF4444]/30'
                        : 'text-[#9CA3AF] hover:text-[#374151] hover:bg-[#F3F4F6]'
                    }`}
                  >
                    <Mic className="w-4 h-4" />
                  </button>
                </div>
              </div>
              {isTyping ? (
                <button
                  id="stop-generation-btn"
                  onClick={stopGeneration}
                  title="Stop generating"
                  className="w-11 h-11 rounded-xl bg-gradient-to-br from-[#EF4444] to-[#DC2626] flex items-center justify-center text-white hover:opacity-90 transition-opacity shadow-lg flex-shrink-0 ring-2 ring-[#EF4444]/30"
                >
                  <Square className="w-4 h-4 fill-white" />
                </button>
              ) : (
                <button
                  id="send-message-btn"
                  onClick={() => sendMessage()}
                  title="Send message"
                  className="w-11 h-11 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center text-white hover:opacity-90 transition-opacity shadow-lg flex-shrink-0"
                >
                  <Send className="w-4 h-4" />
                </button>
              )}
            </div>
            <p className="text-[#9CA3AF] text-[10px] mt-1.5 text-center">Press Enter to send · Shift+Enter for new line</p>
          </div>
        </div>
        {rightPanel}
      </div>
    </TenantLayout>
  )
}
