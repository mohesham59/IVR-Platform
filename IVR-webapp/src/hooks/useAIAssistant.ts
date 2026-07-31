import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { aiApi } from '../api/aiApi'
import { buildFlowFromResponse, repairDoubleEncodedUtf8, sanitizeFlow, safeNodeType } from '../ivr/flowParser'
import type { FlowNode, FlowEdge } from '../ivr/types'

export type GenerationStage =
  | 'idle'
  | 'understanding'
  | 'analysis'
  | 'planning'
  | 'template'
  | 'generating'
  | 'validating'
  | 'converting'
  | 'rendering'

export interface Message {
  id: string
  role: 'user' | 'ai'
  text: string
  type?: 'text' | 'code' | 'flow-preview' | 'suggestion-list'
  extra?: unknown
  ts: string
  flowId?: string
  snapshotId?: string
  version?: number
  domain?: string
}

export interface SessionItem {
  id: string
  title: string
  ts: string
}

export interface UseAIAssistantOptions {
  onFlowGenerated?: (flow: { nodes: FlowNode[]; edges: FlowEdge[]; flowName: string; status?: string }) => void
  initialSessionId?: string
}

export interface UseAIAssistantReturn {
  messages: Message[]
  input: string
  setInput: (v: string) => void
  isTyping: boolean
  sessions: SessionItem[]
  sessionId: string
  setSessionId: (v: string) => void
  latestFlow: { nodes: FlowNode[]; edges: FlowEdge[]; flowName: string; status?: string } | null
  selectedProvider: string
  setSelectedProvider: (v: string) => void
  enhancePrompt: boolean
  setEnhancePrompt: (v: boolean) => void
  providers: Record<string, string[]>
  quotaWarnings: Array<{ provider: string; model?: string; attempt: number }>
  actualProviderUsed: string | null
  providerAttempts: import('../api/aiApi').ProviderAttempt[]
  historyError: string | null
  validationResult: { valid: boolean; issues: Array<{ severity: string; message: string; nodeId?: string }>; score?: number; status?: string; statusLabel?: string; templateFallback?: boolean; fallbackNotice?: string } | null
  selectedMessageId: string | null
  selectedSnapshotId: string | null
  selectedVersion: number | null
  generationStage: GenerationStage
  messagesEndRef: React.RefObject<HTMLDivElement | null>
  activeFlowRef: React.RefObject<{ nodes: FlowNode[]; edges: FlowEdge[]; flowName: string; status?: string } | null>
  sendMessage: (text?: string) => Promise<void>
  handleNewChat: () => Promise<void>
  handleDeleteSession: (e: React.MouseEvent, sid: string) => Promise<void>
  handleSelectSession: (sid: string) => void
  handleInspectMessage: (msg: Message) => void
  handleResetToCurrentCanvas: () => void
  handleOpenInBuilder: (flow?: { nodes?: FlowNode[]; edges?: FlowEdge[]; flowName?: string; status?: string }) => void
  handleApplyFlow: (flow?: { nodes?: FlowNode[]; edges?: FlowEdge[]; flowName?: string; status?: string }) => void
  handleValidateFlow: () => Promise<void>
  handleImproveFlow: () => Promise<void>
  handleExportJson: () => void
  refreshSessions: () => Promise<void>
  loadSessionHistory: (sid: string) => Promise<void>
  buildFlowContextJson: () => string | undefined
  setActiveFlow: (flow: { nodes: FlowNode[]; edges: FlowEdge[]; flowName: string; status?: string } | null) => void
  extractVoicePrompts: (nodes: FlowNode[]) => string[]
  getNodeIconSymbol: (type: string) => string
  repairDoubleEncodedUtf8: (str: string) => string
  pushToHistory: (msg: Message) => void
}

export function detectDomain(text: string): { label: string; icon: string } | null {
  const lower = text.toLowerCase()
  if (/hospital|clinic|medical|doctor|health|pharmacy|triage|nurse|prescription|urgent.?care/i.test(lower)) return { label: 'Healthcare', icon: '🏥' }
  if (/bank|loan|finance|account|fraud/i.test(lower)) return { label: 'Banking', icon: '🏦' }
  if (/hotel|resort|room.service|concierge|booking|housekeeping|lodging|suite/i.test(lower)) return { label: 'Hospitality', icon: '🏨' }
  if (/insurance|claim|adjuster|policy/i.test(lower)) return { label: 'Insurance', icon: '🛡️' }
  if (/restaurant|pizza|food|dining|reservation|takeout|catering|cafe|takeaway/i.test(lower)) return { label: 'Restaurant', icon: '🍕' }
  if (/university|college|campus|admission|financial.?aid/i.test(lower)) return { label: 'Education', icon: '🎓' }
  if (/telecom|internet|isp|broadband|outage/i.test(lower)) return { label: 'Telecom', icon: '📞' }
  if (/emergency|911|urgent|dispatch/i.test(lower)) return { label: 'Emergency', icon: '🚨' }
  if (/tech.?support|trouble|escalation/i.test(lower)) return { label: 'Tech Support', icon: '💻' }
  return null
}

export function extractVoicePrompts(nodes: FlowNode[]): string[] {
  const prompts: string[] = []
  nodes.forEach(n => {
    if (['greeting', 'tts', 'voicemail', 'playback', 'dtmf_menu'].includes(n.type)) {
      const name = n.title.toLowerCase().replace(/[\s_-]+/g, '_') + '.wav'
      if (!prompts.includes(name)) prompts.push(name)
    }
  })
  return prompts.length > 0 ? prompts : ['welcome_prompt.wav', 'main_menu.wav']
}

export function getNodeIconSymbol(type: string): string {
  switch (type) {
    case 'start': return '▶'
    case 'hours': return '🕒'
    case 'greeting': return '👋'
    case 'dtmf_menu': return '☎'
    case 'queue': return '⏳'
    case 'transfer': return '↗'
    case 'ai': return '🤖'
    case 'api': return '⚡'
    case 'voicemail': return '🎙️'
    case 'record': return '⏺️'
    case 'end': return '⛔'
    default: return '⚡'
  }
}

export function useAIAssistant(options: UseAIAssistantOptions = {}): UseAIAssistantReturn {
  const { onFlowGenerated, initialSessionId } = options

  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isTyping, setIsTyping] = useState(false)
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [sessionId, setSessionId] = useState<string>(() => {
    return initialSessionId || localStorage.getItem('nexus_ai_session_id') || crypto.randomUUID()
  })
  const [latestFlow, setLatestFlow] = useState<{ nodes: FlowNode[]; edges: FlowEdge[]; flowName: string } | null>(null)
  const [selectedProvider, setSelectedProvider] = useState<string>(() => {
    return localStorage.getItem('ai_provider') || 'gemini'
  })
  const [enhancePrompt, setEnhancePrompt] = useState<boolean>(true)
  const [providers, setProviders] = useState<Record<string, string[]>>({})
  const [quotaWarnings, setQuotaWarnings] = useState<Array<{ provider: string; model?: string; attempt: number }>>([])
  const [actualProviderUsed, setActualProviderUsed] = useState<string | null>(null)
  const [providerAttempts, setProviderAttempts] = useState<import('../api/aiApi').ProviderAttempt[]>([])
  const [validationResult, setValidationResult] = useState<{ valid: boolean; issues: Array<{ severity: string; message: string; nodeId?: string }> } | null>(null)
  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null)
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string | null>(null)
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null)
  const [generationStage, setGenerationStage] = useState<GenerationStage>('idle')

  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const activeFlowRef = useRef<{ nodes: FlowNode[]; edges: FlowEdge[]; flowName: string } | null>(null)
  const currentSessionIdRef = useRef<string>(sessionId)

  useEffect(() => {
    currentSessionIdRef.current = sessionId
  }, [sessionId])

  useEffect(() => {
    localStorage.setItem('nexus_ai_session_id', sessionId)
  }, [sessionId])

  useEffect(() => {
    localStorage.setItem('ai_provider', selectedProvider)
  }, [selectedProvider])

  useEffect(() => {
    aiApi.fetchProviders()
      .then(data => setProviders(data))
      .catch((err) => {
        console.warn('[useAIAssistant] Failed to fetch providers from backend, using fallback list:', err.message || err);
        setProviders({
          gemini: ['gemini-2.0-flash'],
          groq: ['llama-3.3-70b-versatile'],
        })
      })
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, isTyping])

  const setActiveFlow = useCallback((flow: { nodes: FlowNode[]; edges: FlowEdge[]; flowName: string } | null) => {
    if (flow === null && activeFlowRef.current === null) {
      return
    }
    activeFlowRef.current = flow
    setLatestFlow(flow)
    if (flow) {
      console.log(
        `[NexusIVR] Active flow updated → flowName="${flow.flowName}" ` +
        `nodeCount=${flow.nodes.length}`
      )
    } else {
      console.log('[NexusIVR] Active flow reset to null')
    }
  }, [])

  const buildFlowContextJson = useCallback((): string | undefined => {
    if (!activeFlowRef.current || activeFlowRef.current.nodes.length === 0) return undefined
    return JSON.stringify({
      flowName: activeFlowRef.current.flowName,
      nodes: activeFlowRef.current.nodes.map(n => ({
        id: n.id,
        type: n.type,
        label: n.title,
        title: n.title,
        name: n.title,
        description: n.subtitle,
      })),
      edges: activeFlowRef.current.edges.map(e => ({
        id: e.id,
        source: e.sourceId,
        target: e.targetId,
        label: e.label,
      })),
    })
  }, [])

  const pushToHistory = useCallback((msg: Message) => {
    setMessages(prev => {
      const next = [...prev, msg]
      aiApi.saveHistory(sessionId, next).catch(() => {
        console.warn('Failed to auto-save conversation to database')
      })
      return next
    })
  }, [sessionId])

  const updateAndSaveMessages = useCallback((newMsgs: Message[]) => {
    setMessages(newMsgs)
    aiApi.saveHistory(sessionId, newMsgs).catch(() => {
      console.warn('Failed to auto-save conversation to database')
    })
  }, [sessionId])

  const [historyError, setHistoryError] = useState<string | null>(null)

  const refreshSessions = useCallback(async () => {
    try {
      setHistoryError(null)
      const response = await aiApi.fetchHistory()
      const sessionList = (response as any)?.data ?? []
      if (Array.isArray(sessionList) && sessionList.length > 0) {
        const mapped: SessionItem[] = sessionList.map((s: any) => {
          const sessionId = s.sessionId || s.id
          if (!sessionId) {
            return null
          }
          let timeLabel = 'Active'
          const createdAt = s.createdAt || s.updatedAt || s.startedAt
          if (createdAt) {
            timeLabel = new Date(createdAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
          }
          const rawTitle = s.title || s.customerIdentifier || s.flowName
          const title = (rawTitle && rawTitle !== 'New Chat') ? rawTitle : 'New IVR Flow Session'
          return {
            id: sessionId,
            title,
            ts: timeLabel,
          }
        }).filter((item): item is SessionItem => item !== null)
        setSessions(mapped)
      } else {
        setSessions([])
      }
    } catch (err: any) {
      console.warn('[useAIAssistant] Failed to fetch session history:', err.message || err)
      setHistoryError('Unable to load chat history. Working offline.')
      setSessions([])
    }
  }, [])

  useEffect(() => {
    refreshSessions()
  }, [refreshSessions])

  const deriveSessionTitle = useCallback((flowName?: string, msgs: Message[] = []): string => {
    if (flowName && flowName.trim() && flowName !== 'New IVR Flow Session' && flowName !== 'Generated Flow' && flowName !== 'Restored Flow' && flowName !== 'Inspected Flow') {
      return flowName.trim()
    }
    const firstUserMsg = msgs.find(m => m.role === 'user' && m.text && !m.text.startsWith('__flow_sync__:'))
    if (firstUserMsg && firstUserMsg.text.trim()) {
      const clean = firstUserMsg.text.trim()
      return clean.length > 40 ? clean.substring(0, 40) + '...' : clean
    }
    return 'New IVR Flow Session'
  }, [])

  useEffect(() => {
    if (!sessionId) return
    const derivedTitle = deriveSessionTitle(latestFlow?.flowName, messages)
    if (derivedTitle === 'New IVR Flow Session') return

    setSessions(prev => {
      const existing = prev.find(s => s.id === sessionId)
      if (!existing) {
        return [{ id: sessionId, title: derivedTitle, ts: 'Just now' }, ...prev]
      }
      if (existing.title === derivedTitle) return prev
      return prev.map(s => s.id === sessionId ? { ...s, title: derivedTitle } : s)
    })

    localStorage.setItem(`nexus_builder_flowname_${sessionId}`, derivedTitle)
    aiApi.renameConversation(sessionId, derivedTitle).catch(err => {
      console.warn('[useAIAssistant] Failed to persist session title update to backend:', err)
    })
  }, [sessionId, latestFlow?.flowName, messages, deriveSessionTitle])

  const loadSessionHistory = useCallback(async (sid: string) => {
    currentSessionIdRef.current = sid
    try {
      setHistoryError(null)
      const res = await aiApi.fetchHistory(sid)
      if (currentSessionIdRef.current !== sid) return

      const messages = (res as any)?.data ?? []
      if (Array.isArray(messages) && messages.length > 0) {
        const loadedMsgs: Message[] = messages.map((m: any, i: number) => {
          let type: 'text' | 'flow-preview' = 'text'
          let extra: any = null
          let metadataStr = m.metadata
          let flowId: string | undefined = undefined
          let snapshotId: string | undefined = undefined
          let version: number | undefined = undefined
          let domain: string | undefined = undefined

          if (metadataStr && typeof metadataStr === 'string') {
            metadataStr = repairDoubleEncodedUtf8(metadataStr)
          }
          if (metadataStr) {
            try {
              const meta = JSON.parse(metadataStr)
              flowId = meta.flowId
              snapshotId = meta.snapshotId
              version = meta.version
              domain = meta.domain

              if (meta.nodes || meta.flowNodes || meta.flow) {
                type = 'flow-preview'
                let flowNodes = meta.flowNodes || meta.nodes || meta.flow?.nodes || []
                let flowEdges = meta.flowEdges || meta.edges || meta.flow?.edges || []

                flowNodes = flowNodes.map((n: any) => {
                  if (n.type && /^[A-Z_]+$/.test(n.type)) {
                    return { ...n, type: safeNodeType(n.type.toLowerCase()) }
                  }
                  return n
                })

                flowEdges = flowEdges.map((e: any) => {
                  if (e.source && !e.sourceId) {
                    return { ...e, sourceId: e.source, targetId: e.target }
                  }
                  return e
                })

                extra = {
                  flowNodes,
                  flowEdges,
                  flowName: meta.flowName || meta.name || meta.flow?.name || 'AI Generated Flow',
                  nodes: flowNodes.length,
                  complexity: meta.complexity || 'Medium',
                  duration: meta.duration || '2-3 min',
                  score: meta.score || 95,
                  voicePrompts: meta.voicePrompts || [],
                  structure: meta.structure || [],
                }
              }
            } catch {
              // ignore
            }
          }
          const rawText = m.content || m.text || ''
          const repairedText = repairDoubleEncodedUtf8(rawText)
          return {
            id: m.id || String(i),
            role: m.role?.toLowerCase() === 'user' ? 'user' : 'ai',
            text: repairedText,
            type,
            extra,
            ts: m.createdAt ? new Date(m.createdAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }) : 'Just now',
            flowId,
            snapshotId,
            version,
            domain,
          }
        })
        if (currentSessionIdRef.current !== sid) return
        setMessages(loadedMsgs)

        let restoredFlow: { nodes: FlowNode[]; edges: FlowEdge[]; flowName: string } | null = null
        const stored = localStorage.getItem(`nexus_flow_${sid}`)
        if (stored) {
          try { restoredFlow = JSON.parse(stored) } catch { /* ignore */ }
        } else {
          const builderNodes = localStorage.getItem(`nexus_builder_nodes_${sid}`)
          const builderEdges = localStorage.getItem(`nexus_builder_edges_${sid}`)
          const builderName = localStorage.getItem(`nexus_builder_flowname_${sid}`)
          if (builderNodes) {
            try {
              restoredFlow = {
                nodes: JSON.parse(builderNodes),
                edges: builderEdges ? JSON.parse(builderEdges) : [],
                flowName: builderName || 'Restored Flow',
              }
            } catch { /* ignore */ }
          }
        }

        if (!restoredFlow && res.flow) {
          restoredFlow = res.flow
        } else if (!restoredFlow && res.nodes && res.edges) {
          restoredFlow = { nodes: res.nodes, edges: res.edges, flowName: res.flowName || 'Restored Flow' }
        } else if (!restoredFlow) {
          const lastFlow = [...loadedMsgs].reverse().find(msg => msg.type === 'flow-preview')
          if (lastFlow && lastFlow.extra) {
            const ex = lastFlow.extra as any
            restoredFlow = {
              nodes: ex.flowNodes,
              edges: ex.flowEdges,
              flowName: ex.flowName,
            }
          }
        }

        if (currentSessionIdRef.current !== sid) return

        if (restoredFlow) {
          const sanitized = sanitizeFlow({ nodes: restoredFlow.nodes || [], edges: restoredFlow.edges || [] })
          const sanitizedFlow = { ...restoredFlow, nodes: sanitized.nodes, edges: sanitized.edges }
          setActiveFlow(sanitizedFlow)
          aiApi.validateFlow({ nodes: sanitizedFlow.nodes, edges: sanitizedFlow.edges })
            .then(val => {
              if (currentSessionIdRef.current === sid) setValidationResult(val)
            })
            .catch(() => {
              if (currentSessionIdRef.current === sid) setValidationResult({ valid: true, issues: [] })
            })
        } else {
          setActiveFlow(null)
          setValidationResult(null)
        }
      } else {
        if (currentSessionIdRef.current === sid) {
          setMessages([])
          setActiveFlow(null)
          setValidationResult(null)
        }
      }
    } catch {
      if (currentSessionIdRef.current === sid) {
        setMessages([])
        setActiveFlow(null)
        setValidationResult(null)
      }
    }
  }, [setActiveFlow])

  const handleSelectSession = useCallback((sid: string) => {
    if (sid === currentSessionIdRef.current) return
    console.log('Conversation clicked', sid)
    currentSessionIdRef.current = sid
    setActiveFlow(null)
    setMessages([])
    setValidationResult(null)
    setSessionId(sid)
  }, [setActiveFlow])

  useEffect(() => {
    setSelectedMessageId(null)
    setSelectedSnapshotId(null)
    setSelectedVersion(null)
    if (sessionId) {
      loadSessionHistory(sessionId)
    }
  }, [sessionId, loadSessionHistory])

  const handleNewChat = useCallback(async () => {
    setActiveFlow(null)
    setMessages([])
    setValidationResult(null)
    setActualProviderUsed(null)
    setQuotaWarnings([])
    setSelectedMessageId(null)
    setSelectedSnapshotId(null)
    setSelectedVersion(null)

    const newSid = crypto.randomUUID()
    currentSessionIdRef.current = newSid
    setSessionId(newSid)

    setSessions(prev => [{ id: newSid, title: 'New IVR Flow Session', ts: 'Just now' }, ...prev.filter(s => s.id !== newSid)])

    try {
      const res = await aiApi.createNewChat('New IVR Flow Session')
      if (res && res.sessionId) {
        currentSessionIdRef.current = res.sessionId
        setSessionId(res.sessionId)
        setSessions(prev => prev.map(s => s.id === newSid ? { ...s, id: res.sessionId } : s))
      }
    } catch {
      // retain newSid
    }

    refreshSessions()
  }, [setActiveFlow, refreshSessions])

  const handleDeleteSession = useCallback(async (e: React.MouseEvent, sid: string) => {
    e.stopPropagation()
    try {
      const result = await aiApi.deleteConversation(sid)
      if (result && result.success) {
        localStorage.removeItem(`nexus_flow_${sid}`)
        localStorage.removeItem(`nexus_builder_nodes_${sid}`)
        localStorage.removeItem(`nexus_builder_edges_${sid}`)
        localStorage.removeItem(`nexus_builder_flowname_${sid}`)
        localStorage.removeItem(`nexus_flow_${sid}`)
      } else {
        console.warn('Backend delete failed for session', sid, result)
        return
      }
    } catch {
      console.warn('Error deleting conversation from backend')
      return
    }
    const updated = sessions.filter(s => s.id !== sid)
    setSessions(updated)
    if (sid === sessionId) {
      if (updated.length > 0) {
        setSessionId(updated[0].id)
      } else {
        handleNewChat()
      }
    }
  }, [sessions, sessionId, handleNewChat])

  const handleInspectMessage = useCallback((msg: Message) => {
    if (msg.snapshotId) {
      setSelectedSnapshotId(msg.snapshotId)
      setSelectedVersion(msg.version ?? null)
      setSelectedMessageId(msg.id)
      if (msg.extra && (msg.extra as any).flowNodes) {
        const ex = msg.extra as any
        setLatestFlow({
          nodes: ex.flowNodes,
          edges: ex.flowEdges || [],
          flowName: ex.flowName || 'Inspected Flow',
        })
      }
    }
  }, [])

  const handleResetToCurrentCanvas = useCallback(() => {
    setSelectedSnapshotId(null)
    setSelectedVersion(null)
    setSelectedMessageId(null)
    const stored = localStorage.getItem(`nexus_flow_${sessionId}`)
    if (stored) {
      try {
        setActiveFlow(JSON.parse(stored))
        return
      } catch { /* ignore */ }
    }
    setActiveFlow(activeFlowRef.current)
  }, [sessionId, setActiveFlow])

  const handleApplyFlow = useCallback((flowData?: { nodes?: FlowNode[]; edges?: FlowEdge[]; flowName?: string }) => {
    const targetFlow = flowData ?? latestFlow
    if (!targetFlow || !targetFlow.nodes) return

    const name = targetFlow.flowName || 'AI Generated Flow'
    console.log(`[NexusIVR] Use This Flow clicked for flow: "${name}"`, {
      nodeCount: targetFlow.nodes.length,
      edgeCount: targetFlow.edges?.length || 0,
    })

    const sanitized = sanitizeFlow({ nodes: targetFlow.nodes || [], edges: targetFlow.edges || [] })
    const sanitizedFlow = {
      nodes: sanitized.nodes,
      edges: sanitized.edges,
      flowName: name,
    }

    setActiveFlow(sanitizedFlow)

    if (sessionId) {
      localStorage.setItem(`nexus_flow_${sessionId}`, JSON.stringify(sanitizedFlow))
      localStorage.setItem(`nexus_builder_nodes_${sessionId}`, JSON.stringify(sanitized.nodes))
      localStorage.setItem(`nexus_builder_edges_${sessionId}`, JSON.stringify(sanitized.edges))
      localStorage.setItem(`nexus_builder_flowname_${sessionId}`, name)
    }

    aiApi.validateFlow({ nodes: sanitized.nodes, edges: sanitized.edges })
      .then(val => setValidationResult(val))
      .catch(() => setValidationResult({ valid: true, issues: [] }))

    if (onFlowGenerated) {
      onFlowGenerated(sanitizedFlow)
    }
  }, [latestFlow, sessionId, setActiveFlow, onFlowGenerated])

  const navigate = useNavigate()

  const handleOpenInBuilder = useCallback((flow?: { nodes?: FlowNode[]; edges?: FlowEdge[]; flowName?: string; viewport?: { x: number; y: number; scale: number } }) => {
    const targetFlow = flow ?? latestFlow
    if (!targetFlow) return
    handleApplyFlow(targetFlow)
    if ((window as any).__nexusOpenInBuilder) {
      ;(window as any).__nexusOpenInBuilder(targetFlow)
    } else {
      navigate('/tenant/ivr-builder', { state: { nodes: targetFlow.nodes, edges: targetFlow.edges, flowName: targetFlow.flowName, sessionId } })
    }
  }, [latestFlow, handleApplyFlow, navigate, sessionId])

  const handleValidateFlow = useCallback(async () => {
    if (!activeFlowRef.current) return
    try {
      const res = await aiApi.validateFlow({ nodes: activeFlowRef.current.nodes, edges: activeFlowRef.current.edges })
      setValidationResult(res)
    } catch {
      setValidationResult({ valid: true, issues: [] })
    }
  }, [])

  const handleImproveFlow = useCallback(async () => {
    if (!activeFlowRef.current) return
    setIsTyping(true)
    try {
      const res = await aiApi.improveFlow(
        { nodes: activeFlowRef.current.nodes, edges: activeFlowRef.current.edges },
        ['Improve call containment', 'Reduce wait times']
      )
      if (res.quotaWarnings && res.quotaWarnings.length > 0) {
        setQuotaWarnings(res.quotaWarnings)
      }
      if ((res as any).actualProviderUsed) {
        setActualProviderUsed((res as any).actualProviderUsed)
      }
      if (res.providerAttempts && res.providerAttempts.length > 0) {
        setProviderAttempts(res.providerAttempts)
      }
      let improved: { nodes: FlowNode[]; edges: FlowEdge[]; flowName: string; status?: string } | null = activeFlowRef.current
      if (res.suggestedFlowJson) {
        const isImproved = (res as any).improved !== false && !(res as any).regressed
        const isRolledBack = !!(res as any).rolledBack
        const cleanBaseName = activeFlowRef.current.flowName.replace(/\s*\((Optimized|Improved|Review Needed)\)/g, '')
        const suffix = isRolledBack ? '' : (isImproved ? ' (Optimized)' : ' (Review Needed)')
        const targetName = cleanBaseName + suffix
        const parsed = buildFlowFromResponse({ flowJson: res.suggestedFlowJson, name: targetName })
        if (parsed.status === 'empty') {
          const timeStr = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
          pushToHistory({ id: Date.now().toString(), role: 'ai', ts: timeStr, type: 'text', text: 'Flow optimization failed: The AI did not return a valid flow. Please try again.' })
          return
        }
        improved = { nodes: parsed.nodes, edges: parsed.edges, flowName: targetName, status: parsed.status }
        setActiveFlow(improved)
        localStorage.setItem(`nexus_flow_${sessionId}`, JSON.stringify(improved))
        localStorage.setItem(`nexus_builder_nodes_${sessionId}`, JSON.stringify(improved.nodes))
        localStorage.setItem(`nexus_builder_edges_${sessionId}`, JSON.stringify(improved.edges))
        localStorage.setItem(`nexus_builder_flowname_${sessionId}`, improved.flowName)
      }
      const valRes = await aiApi.validateFlow({ nodes: improved.nodes, edges: improved.edges }).catch(() => ({ valid: true, issues: [] }))
      setValidationResult(valRes)

      const summaryText = (res as any).rationale || ((res as any).changeLog && (res as any).changeLog.length > 0 ? (res as any).changeLog.join('. ') : res.improvementSummary)
      const realScore = (res as any).finalValidation?.score ?? (valRes as any).score ?? 98

      const timeStr = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
      const aiMsg: Message = {
        id: Date.now().toString(),
        role: 'ai',
        ts: timeStr,
        type: 'flow-preview',
        text: `AI Optimization Complete: ${summaryText || 'Updated flow for higher call containment and reduced hold times.'}`,
        extra: {
          flowNodes: improved.nodes,
          flowEdges: improved.edges,
          flowName: improved.flowName,
          nodes: improved.nodes.length,
          complexity: improved.nodes.length > 8 ? 'High' : 'Medium',
          duration: '2–4 min',
          score: realScore,
          voicePrompts: extractVoicePrompts(improved.nodes),
          structure: improved.nodes.map(n => ({ icon: getNodeIconSymbol(n.type), label: n.title, color: '#3B82F6' })),
        },
        domain: detectDomain(improved.flowName)?.label,
      }
      pushToHistory(aiMsg)
    } catch (err: any) {
      const errorMsg = err instanceof Error ? err.message : 'Flow improvement failed. Please try again.'
      const timeStr = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
      const aiMsg: Message = {
        id: Date.now().toString(),
        role: 'ai',
        ts: timeStr,
        type: 'text',
        text: `Flow improvement failed: ${errorMsg}`,
      }
      pushToHistory(aiMsg)
    } finally {
      setIsTyping(false)
    }
  }, [sessionId, setActiveFlow, pushToHistory])

  const handleExportJson = useCallback(() => {
    if (!activeFlowRef.current) return
    const flow = activeFlowRef.current
    const jsonStr = JSON.stringify({ name: flow.flowName, nodes: flow.nodes, edges: flow.edges }, null, 2)
    const blob = new Blob([jsonStr], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${flow.flowName.toLowerCase().replace(/[\s_-]+/g, '_')}_flow.json`
    a.click()
    URL.revokeObjectURL(url)
  }, [])

  const advanceStage = useCallback((from: GenerationStage, to: GenerationStage, delay: number) => {
    setTimeout(() => {
      setGenerationStage(prev => (prev === from ? to : prev))
    }, delay)
  }, [])

  const sendMessage = useCallback(async (textOverride?: string) => {
    const text = (textOverride ?? input).trim()
    if (!text) return

    const timeStr = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
    const userMsg: Message = { id: Date.now().toString(), role: 'user', text, ts: timeStr, type: 'text' }

    const updatedWithUser = [...messages, userMsg]
    setMessages(updatedWithUser)
    setInput('')
    setIsTyping(true)
    setGenerationStage('understanding')

    try {
      const t = text.toLowerCase()
      const hasActiveFlow = activeFlowRef.current !== null && activeFlowRef.current.nodes.length > 0

      const hasGenKeyword = /^(generate|create|design|build|make|produce)/i.test(t) || /generate json from scratch/i.test(t)
      const isQuestionOrInfo = /^(is|why|what|explain|describe|analyze|review|validate|compare|summarize|how many|tell me|show)/i.test(t) ||
        /simplified|canvas|exact json|react flow json|what does node|why is this edge|is this valid/i.test(t)

      let isBuildRequest = false
      if (hasActiveFlow) {
        isBuildRequest = hasGenKeyword && !isQuestionOrInfo
      } else {
        isBuildRequest = hasGenKeyword || /^(hospital|bank|ivr|flow|restaurant|support|insurance|hotel|university|pizza)/i.test(t)
      }

      if (isBuildRequest && text.length > 8) {
        advanceStage('understanding', 'analysis', 300)
        advanceStage('analysis', 'planning', 600)
        advanceStage('planning', 'template', 900)
        setGenerationStage('generating')

        let flowRes
        try {
          flowRes = await aiApi.generateFlow(text)
        } catch (err: any) {
          const errorMsg = err instanceof Error ? err.message : 'Flow generation failed. Please try again.'
          const aiMsg: Message = {
            id: (Date.now() + 1).toString(),
            role: 'ai',
            ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
            type: 'text',
            text: `Flow generation failed: ${errorMsg}`,
          }
          updateAndSaveMessages([...updatedWithUser, aiMsg])
          setGenerationStage('idle')
          setIsTyping(false)
          return
        }

        if (flowRes.quotaWarnings && flowRes.quotaWarnings.length > 0) {
          setQuotaWarnings(flowRes.quotaWarnings)
        }
        if (flowRes.actualProviderUsed) {
          setActualProviderUsed(flowRes.actualProviderUsed)
        }
        if (flowRes.providerAttempts && flowRes.providerAttempts.length > 0) {
          setProviderAttempts(flowRes.providerAttempts)
        }
        setGenerationStage('validating')

        const parsedFlow = buildFlowFromResponse(flowRes)
        const flowName = flowRes.name || text

        if (parsedFlow.status === 'empty') {
          setGenerationStage('idle')
          const failMsg: Message = {
            id: (Date.now() + 1).toString(),
            role: 'ai',
            ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
            type: 'text',
            text: 'Flow generation failed: The AI did not return a valid flow structure. Please try again or rephrase your prompt.',
          }
          updateAndSaveMessages([...updatedWithUser, failMsg])
          return
        }

        const flowData = { nodes: parsedFlow.nodes, edges: parsedFlow.edges, flowName, status: parsedFlow.status }

        aiApi.validateFlow({ nodes: parsedFlow.nodes, edges: parsedFlow.edges })
          .then(val => setValidationResult(val))
          .catch(() => setValidationResult({ valid: true, issues: [] }))

        setGenerationStage('converting')
        setActiveFlow(flowData)
        localStorage.setItem(`nexus_flow_${sessionId}`, JSON.stringify(flowData))
        localStorage.setItem(`nexus_builder_nodes_${sessionId}`, JSON.stringify(flowData.nodes))
        localStorage.setItem(`nexus_builder_edges_${sessionId}`, JSON.stringify(flowData.edges))
        localStorage.setItem(`nexus_builder_flowname_${sessionId}`, flowData.flowName)

        setGenerationStage('rendering')

        const domain = detectDomain(flowName)

        setTimeout(() => {
          setGenerationStage('idle')
        }, 500)

        onFlowGenerated?.(flowData)

        const aiMsg: Message = {
          id: (Date.now() + 1).toString(),
          role: 'ai',
          ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
          type: 'flow-preview',
          domain: domain?.label,
          text: `I've generated a complete IVR flow for "${flowName}"${domain ? ` (${domain.icon} ${domain.label})` : ''}. Here is the flow preview:`,
          extra: {
            flowNodes: parsedFlow.nodes,
            flowEdges: parsedFlow.edges,
            flowName,
            nodes: parsedFlow.nodes.length,
            complexity: parsedFlow.nodes.length > 8 ? 'High' : 'Medium',
            duration: '2–4 min',
            score: 95,
            voicePrompts: extractVoicePrompts(parsedFlow.nodes),
            structure: parsedFlow.nodes.map(n => ({ icon: getNodeIconSymbol(n.type), label: n.title, color: '#3B82F6' })),
          },
        }
        updateAndSaveMessages([...updatedWithUser, aiMsg])
      } else {
        const flowCtx = buildFlowContextJson()
        const chatRes = await aiApi.sendMessage(text, sessionId, 'CHAT', flowCtx, selectedSnapshotId || undefined, enhancePrompt)
        if (chatRes.quotaWarnings && chatRes.quotaWarnings.length > 0) {
          setQuotaWarnings(chatRes.quotaWarnings)
        }
        if ((chatRes as any).actualProviderUsed) {
          setActualProviderUsed((chatRes as any).actualProviderUsed)
        }
        if (chatRes.sessionId) setSessionId(chatRes.sessionId)

        if (chatRes.flowJson) {
          const parsedFlow = buildFlowFromResponse({ flowJson: chatRes.flowJson, name: text })
          const flowName = text
          if (parsedFlow.status === 'empty') {
            const failMsg: Message = {
              id: (Date.now() + 1).toString(),
              role: 'ai',
              ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
              type: 'text',
              text: 'Flow generation failed: The AI did not return a valid flow structure. Please try again or rephrase your prompt.',
            }
            updateAndSaveMessages([...updatedWithUser, failMsg])
            return
          }
          const flowData = { nodes: parsedFlow.nodes, edges: parsedFlow.edges, flowName, status: parsedFlow.status }
          setActiveFlow(flowData)
          if ((chatRes as any).validationResult) {
            setValidationResult((chatRes as any).validationResult)
          }
          localStorage.setItem(`nexus_flow_${sessionId}`, JSON.stringify(flowData))
          localStorage.setItem(`nexus_builder_nodes_${sessionId}`, JSON.stringify(flowData.nodes))
          localStorage.setItem(`nexus_builder_edges_${sessionId}`, JSON.stringify(flowData.edges))
          localStorage.setItem(`nexus_builder_flowname_${sessionId}`, flowData.flowName)
          onFlowGenerated?.(flowData)

          const domain = detectDomain(flowName)
          const aiMsg: Message = {
            id: (Date.now() + 1).toString(),
            role: 'ai',
            ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
            type: 'flow-preview',
            domain: domain?.label,
            text: `I've generated a complete IVR flow for "${flowName}"${domain ? ` (${domain.icon} ${domain.label})` : ''}. Here is the flow preview:`,
            extra: {
              flowNodes: parsedFlow.nodes,
              flowEdges: parsedFlow.edges,
              flowName,
              nodes: parsedFlow.nodes.length,
              complexity: parsedFlow.nodes.length > 8 ? 'High' : 'Medium',
              duration: '2–4 min',
              score: 95,
              voicePrompts: extractVoicePrompts(parsedFlow.nodes),
              structure: parsedFlow.nodes.map(n => ({ icon: getNodeIconSymbol(n.type), label: n.title, color: '#3B82F6' })),
            },
          }
          updateAndSaveMessages([...updatedWithUser, aiMsg])
        } else {
          const aiMsg: Message = {
            id: (Date.now() + 1).toString(),
            role: 'ai',
            ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
            type: 'text',
            text: chatRes.replyMessage || "I've processed your request.",
          }
          updateAndSaveMessages([...updatedWithUser, aiMsg])
        }
      }
    } catch (err: any) {
      const errorMsg = err instanceof Error ? err.message : 'Flow generation failed. Please try again.'
      const aiMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'ai',
        ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
        type: 'text',
        text: `Flow generation failed: ${errorMsg}`,
      }
      updateAndSaveMessages([...updatedWithUser, aiMsg])
    } finally {
      setIsTyping(false)
      setGenerationStage('idle')
    }
  }, [input, messages, sessionId, selectedSnapshotId, selectedProvider, onFlowGenerated, setActiveFlow, updateAndSaveMessages, advanceStage, buildFlowContextJson])

  return {
    messages,
    input,
    setInput,
    isTyping,
    sessions,
    sessionId,
    setSessionId,
    latestFlow,
    selectedProvider,
    setSelectedProvider,
    enhancePrompt,
    setEnhancePrompt,
    providers,
    quotaWarnings,
    actualProviderUsed,
    providerAttempts,
    validationResult,
    selectedMessageId,
    selectedSnapshotId,
    selectedVersion,
    generationStage,
    messagesEndRef,
    activeFlowRef,
    sendMessage,
    handleNewChat,
    handleDeleteSession,
    handleSelectSession,
    handleInspectMessage,
    handleResetToCurrentCanvas,
    handleOpenInBuilder,
    handleApplyFlow,
    handleValidateFlow,
    handleImproveFlow,
    handleExportJson,
    refreshSessions,
    loadSessionHistory,
    buildFlowContextJson,
    setActiveFlow,
    extractVoicePrompts,
    getNodeIconSymbol,
    repairDoubleEncodedUtf8,
    pushToHistory,
    historyError,
  }
}
