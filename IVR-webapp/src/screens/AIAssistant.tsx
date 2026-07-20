import { useState, useRef, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import {
  Send, Bot, Pin, Clock, BookOpen, Plus,
  ChevronRight, GitBranch, Mic, Paperclip, RefreshCw,
  Wand2, FileText, CheckCircle, AlertTriangle, Copy,
  ExternalLink,
} from 'lucide-react'

interface Message {
  id: string
  role: 'user' | 'ai'
  text: string
  type?: 'text' | 'code' | 'flow-preview' | 'suggestion-list'
  extra?: unknown
  ts: string
}

const QUICK_SUGGESTIONS = [
  { icon: '🏥', label: 'Hospital IVR', prompt: 'Build a complete IVR for a hospital with appointments, emergency, and billing departments' },
  { icon: '🍽️', label: 'Restaurant Booking', prompt: 'Create a restaurant reservation IVR with time selection and confirmation' },
  { icon: '🏦', label: 'Bank Support', prompt: 'Design a bank customer support IVR with account, loans, and fraud departments' },
  { icon: '🛡️', label: 'Insurance Claims', prompt: 'Build an insurance claims IVR with new claim, status check, and agent transfer' },
  { icon: '⚙️', label: 'Technical Support', prompt: 'Generate a tiered technical support IVR with triage and escalation' },
  { icon: '📅', label: 'Appointment Booking', prompt: 'Create an appointment booking IVR with date/time slot selection and reminders' },
  { icon: '🎙️', label: 'Generate Voice Prompt', prompt: 'Generate professional voice prompts for a welcome greeting in English and Spanish' },
  { icon: '✨', label: 'Improve Existing Flow', prompt: 'Analyze my current Hospital Main IVR and suggest improvements for call containment' },
]

const CHAT_HISTORY = [
  { id: 'h1', title: 'Hospital Main IVR', ts: '2h ago', pinned: true },
  { id: 'h2', title: 'Bank Support Flow v2', ts: '5h ago', pinned: true },
  { id: 'h3', title: 'After-Hours Routing', ts: 'Yesterday', pinned: false },
  { id: 'h4', title: 'Billing IVR Optimization', ts: 'Dec 10', pinned: false },
  { id: 'h5', title: 'Voice Prompts – Spanish', ts: 'Dec 8', pinned: false },
]

const TEMPLATES = [
  { icon: '🏥', name: 'Healthcare IVR', nodes: 14, complexity: 'Medium' },
  { icon: '🏦', name: 'Banking Support', nodes: 18, complexity: 'High' },
  { icon: '🏨', name: 'Hotel Concierge', nodes: 10, complexity: 'Low' },
  { icon: '🛒', name: 'E-Commerce Support', nodes: 12, complexity: 'Medium' },
]

const INITIAL_MESSAGES: Message[] = [
  {
    id: 'm0', role: 'ai', ts: '10:21 AM', type: 'text',
    text: "Hello Marcus! I'm your AI Flow Assistant powered by GPT-4o. I can build complete IVR flows, generate voice prompts, analyze your existing flows, and suggest improvements.\n\nWhat would you like to create today?",
  },
  {
    id: 'm1', role: 'user', ts: '10:22 AM', type: 'text',
    text: 'Build a complete IVR for a hospital with appointments, emergency, and billing departments',
  },
  {
    id: 'm2', role: 'ai', ts: '10:22 AM', type: 'flow-preview',
    text: "I've designed a complete **Hospital Support IVR** with 13 nodes covering all three departments. Here's the flow structure:",
    extra: {
      nodes: 13, complexity: 'Medium', duration: '2–4 min', score: 94,
      voicePrompts: ['welcome_hospital.wav', 'menu_main.wav', 'afterhours_msg.wav', 'billing_hold.wav'],
      structure: [
        { icon: '▶', label: 'Start', color: '#22C55E' },
        { icon: '🕒', label: 'Business Hours', color: '#F59E0B' },
        { icon: '👋', label: 'Welcome Greeting', color: '#3B82F6' },
        { icon: '☎', label: 'Main IVR Menu', color: '#8B5CF6' },
        { icon: '⏳', label: 'Appointments Queue', color: '#F59E0B' },
        { icon: '↗', label: 'Emergency Transfer', color: '#6366F1' },
        { icon: '⏳', label: 'Billing Queue', color: '#F59E0B' },
        { icon: '🤖', label: 'AI Assistant', color: '#7C3AED' },
        { icon: '⛔', label: 'End Call', color: '#EF4444' },
      ],
    },
  },
  {
    id: 'm3', role: 'user', ts: '10:24 AM', type: 'text',
    text: 'Add a Spanish language option as key 9 in the main menu',
  },
  {
    id: 'm4', role: 'ai', ts: '10:24 AM', type: 'text',
    text: "Done! I've added **Key 9 → Spanish Language** to the main menu. This creates a parallel branch that mirrors all departments but uses Spanish voice prompts.\n\nI also:\n• Generated 4 Spanish voice prompts using Polly.Lucia (es-US)\n• Added a language variable `{{lang}}` to all TTS nodes\n• Updated the welcome greeting to include 'Para Espanol, marque 9'",
  },
]

function FlowPreviewCard({ extra }: { extra: { nodes: number; complexity: string; duration: string; score: number; voicePrompts: string[]; structure: Array<{ icon: string; label: string; color: string }> } }) {
  return (
    <div className="mt-3 rounded-xl border border-[#E5E7EB] bg-white overflow-hidden shadow-sm">
      {/* Header */}
      <div className="flex items-center gap-2.5 px-4 py-3 border-b border-[#F3F4F6] bg-gradient-to-r from-[#EFF6FF] to-[#F5F3FF]">
        <GitBranch className="w-4 h-4 text-[#2563EB]" />
        <span className="text-[#1F2937] font-semibold text-sm">Generated Flow Preview</span>
        <span className="ml-auto inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#DCFCE7] border border-[#BBF7D0] text-[#15803D] text-[10px] font-bold">
          <CheckCircle className="w-3 h-3" />
          Valid
        </span>
      </div>

      {/* Flow nodes strip */}
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

      {/* Stats grid */}
      <div className="grid grid-cols-4 border-t border-[#F3F4F6]">
        {[
          { label: 'Nodes', value: extra.nodes },
          { label: 'Complexity', value: extra.complexity },
          { label: 'Avg Duration', value: extra.duration },
          { label: 'Score', value: `${extra.score}%` },
        ].map((s) => (
          <div key={s.label} className="px-3 py-2.5 text-center border-r border-[#F3F4F6] last:border-r-0">
            <p className="text-[#1F2937] font-bold text-sm">{s.value}</p>
            <p className="text-[#9CA3AF] text-[10px]">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Voice prompts */}
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

      {/* Actions */}
      <div className="flex gap-2 px-4 py-3 border-t border-[#F3F4F6] bg-[#F9FAFB]">
        <button className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/20">
          <ExternalLink className="w-3.5 h-3.5" /> Open in IVR Builder
        </button>
        <button className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
          <Copy className="w-3.5 h-3.5" /> Copy
        </button>
      </div>
    </div>
  )
}

function ChatBubble({ msg }: { msg: Message }) {
  const isUser = msg.role === 'user'
  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {/* Avatar */}
      {!isUser && (
        <div className="w-7 h-7 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center flex-shrink-0 mt-1">
          <Bot className="w-4 h-4 text-white" />
        </div>
      )}
      {isUser && (
        <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#2563EB] to-[#7C3AED] flex items-center justify-center flex-shrink-0 mt-1 text-white text-[9px] font-bold">
          MW
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
            {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
            <FlowPreviewCard extra={msg.extra as any} />
          </div>
        )}

        <span className="text-[#9CA3AF] text-[10px] px-1">{msg.ts}</span>
      </div>
    </div>
  )
}

export default function AIAssistant({ onLogout }: { onLogout: () => void }) {
  const [messages, setMessages] = useState<Message[]>(INITIAL_MESSAGES)
  const [input, setInput] = useState('')
  const [isTyping, setIsTyping] = useState(false)
  const [activeChat, setActiveChat] = useState('h1')
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, isTyping])

  const sendMessage = (text: string = input) => {
    if (!text.trim()) return
    const userMsg: Message = { id: Date.now().toString(), role: 'user', text, ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }), type: 'text' }
    setMessages(m => [...m, userMsg])
    setInput('')
    setIsTyping(true)
    setTimeout(() => {
      setIsTyping(false)
      const aiMsg: Message = {
        id: (Date.now() + 1).toString(), role: 'ai', ts: new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }), type: 'text',
        text: "I've processed your request and updated the flow accordingly. The changes have been applied and are ready to preview in the IVR Builder. Would you like me to explain any specific part of the flow in more detail?",
      }
      setMessages(m => [...m, aiMsg])
    }, 1800)
  }

  const rightPanel = (
    <div className="w-72 bg-white border-l border-[#E5E7EB] flex-shrink-0 flex flex-col">
      <div className="px-4 py-3.5 border-b border-[#F3F4F6]">
        <p className="text-[#1F2937] font-semibold text-sm">Flow Summary</p>
        <p className="text-[#9CA3AF] text-xs mt-0.5">Hospital Main IVR · Draft</p>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-5">
        {/* Metrics */}
        <div className="grid grid-cols-2 gap-2.5">
          {[
            { label: 'Nodes', value: '13', color: '#2563EB', bg: '#EFF6FF' },
            { label: 'Complexity', value: 'Medium', color: '#F59E0B', bg: '#FFFBEB' },
            { label: 'Avg Duration', value: '2–4 min', color: '#22C55E', bg: '#F0FDF4' },
            { label: 'Valid. Score', value: '94%', color: '#8B5CF6', bg: '#F5F3FF' },
          ].map(s => (
            <div key={s.label} className="p-3 rounded-xl border border-[#F3F4F6] bg-[#F9FAFB]">
              <div className="text-base font-bold" style={{ color: s.color }}>{s.value}</div>
              <div className="text-[#9CA3AF] text-[10px] mt-0.5">{s.label}</div>
            </div>
          ))}
        </div>

        {/* Validation */}
        <section>
          <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Validation</h4>
          <div className="space-y-1.5">
            <div className="flex items-center gap-2 p-2 rounded-lg bg-[#F0FDF4] border border-[#BBF7D0]">
              <CheckCircle className="w-3.5 h-3.5 text-[#22C55E] flex-shrink-0" />
              <span className="text-[#15803D] text-[11px]">No disconnected nodes</span>
            </div>
            <div className="flex items-center gap-2 p-2 rounded-lg bg-[#FFFBEB] border border-[#FDE68A]">
              <AlertTriangle className="w-3.5 h-3.5 text-[#F59E0B] flex-shrink-0" />
              <span className="text-[#A16207] text-[11px]">Transfer has no fallback</span>
            </div>
          </div>
        </section>

        {/* Voice prompts */}
        <section>
          <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider mb-2">Suggested Voice Prompts</h4>
          <div className="space-y-1.5">
            {['welcome_hospital.wav', 'menu_main.wav', 'afterhours_msg.wav', 'billing_hold.wav'].map(p => (
              <div key={p} className="flex items-center gap-2 p-2 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] bg-white transition-colors">
                <div className="w-6 h-6 rounded-md bg-[#EFF6FF] flex items-center justify-center text-xs flex-shrink-0">🔊</div>
                <span className="text-[#374151] text-[11px] flex-1 truncate">{p}</span>
                <CheckCircle className="w-3 h-3 text-[#22C55E] flex-shrink-0" />
              </div>
            ))}
          </div>
        </section>

        {/* Actions */}
        <section className="space-y-2 pt-1">
          <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/20">
            <ExternalLink className="w-3.5 h-3.5" /> Open in IVR Builder
          </button>
          <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-gradient-to-r from-[#8B5CF6] to-[#2563EB] text-white text-xs font-semibold hover:opacity-90 transition-opacity">
            <Wand2 className="w-3.5 h-3.5" /> Improve Flow
          </button>
          <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
            <FileText className="w-3.5 h-3.5" /> Export as JSON
          </button>
        </section>
      </div>
    </div>
  )

  return (
    <TenantLayout activeNav="ai" onLogout={onLogout} pageTitle="" pageSubtitle="">
      <div className="-mx-6 -mt-10 flex h-[calc(100vh-64px)] overflow-hidden">
        {/* Left: Chat History */}
        <div className="w-56 bg-white border-r border-[#E5E7EB] flex flex-col flex-shrink-0">
          <div className="flex items-center justify-between px-4 py-3.5 border-b border-[#F3F4F6]">
            <p className="text-[#1F2937] font-semibold text-sm">Conversations</p>
            <button className="w-7 h-7 rounded-lg flex items-center justify-center bg-[#EFF6FF] text-[#2563EB] hover:bg-[#DBEAFE] transition-colors">
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto py-2">
            {/* Pinned */}
            <p className="px-4 pt-2 pb-1 text-[9px] font-semibold uppercase tracking-wider text-[#9CA3AF] flex items-center gap-1">
              <Pin className="w-3 h-3" /> Pinned
            </p>
            {CHAT_HISTORY.filter(c => c.pinned).map(c => (
              <button key={c.id} onClick={() => setActiveChat(c.id)}
                className={`w-full flex items-start gap-2 px-4 py-2 text-left transition-colors ${activeChat === c.id ? 'bg-[#EFF6FF]' : 'hover:bg-[#F9FAFB]'}`}>
                <Bot className={`w-3.5 h-3.5 flex-shrink-0 mt-0.5 ${activeChat === c.id ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}`} />
                <div className="flex-1 min-w-0">
                  <p className={`text-xs font-medium truncate ${activeChat === c.id ? 'text-[#2563EB]' : 'text-[#374151]'}`}>{c.title}</p>
                  <p className="text-[#9CA3AF] text-[10px]">{c.ts}</p>
                </div>
              </button>
            ))}

            <div className="h-px bg-[#F3F4F6] mx-4 my-2" />

            {/* Recent */}
            <p className="px-4 pt-1 pb-1 text-[9px] font-semibold uppercase tracking-wider text-[#9CA3AF] flex items-center gap-1">
              <Clock className="w-3 h-3" /> Recent
            </p>
            {CHAT_HISTORY.filter(c => !c.pinned).map(c => (
              <button key={c.id} onClick={() => setActiveChat(c.id)}
                className={`w-full flex items-start gap-2 px-4 py-2 text-left transition-colors ${activeChat === c.id ? 'bg-[#EFF6FF]' : 'hover:bg-[#F9FAFB]'}`}>
                <Bot className={`w-3.5 h-3.5 flex-shrink-0 mt-0.5 ${activeChat === c.id ? 'text-[#2563EB]' : 'text-[#9CA3AF]'}`} />
                <div className="flex-1 min-w-0">
                  <p className={`text-xs font-medium truncate ${activeChat === c.id ? 'text-[#2563EB]' : 'text-[#374151]'}`}>{c.title}</p>
                  <p className="text-[#9CA3AF] text-[10px]">{c.ts}</p>
                </div>
              </button>
            ))}

            <div className="h-px bg-[#F3F4F6] mx-4 my-2" />

            {/* Templates */}
            <p className="px-4 pt-1 pb-1 text-[9px] font-semibold uppercase tracking-wider text-[#9CA3AF] flex items-center gap-1">
              <BookOpen className="w-3 h-3" /> Templates
            </p>
            {TEMPLATES.map(t => (
              <button key={t.name}
                className="w-full flex items-center gap-2 px-4 py-2 hover:bg-[#F9FAFB] transition-colors">
                <span className="text-base flex-shrink-0">{t.icon}</span>
                <div className="flex-1 min-w-0 text-left">
                  <p className="text-[#374151] text-xs font-medium truncate">{t.name}</p>
                  <p className="text-[#9CA3AF] text-[10px]">{t.nodes} nodes · {t.complexity}</p>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Center: Chat */}
        <div className="flex-1 flex flex-col min-w-0 bg-[#F8FAFC]">
          {/* Chat header */}
          <div className="flex items-center gap-3 px-5 py-3 bg-white border-b border-[#E5E7EB]">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center">
              <Bot className="w-4 h-4 text-white" />
            </div>
            <div>
              <p className="text-[#1F2937] font-semibold text-sm">AI Flow Assistant</p>
              <div className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#22C55E]" />
                <p className="text-[#9CA3AF] text-[10px]">GPT-4o · Online</p>
              </div>
            </div>
            <div className="ml-auto flex gap-2">
              <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white border border-[#E5E7EB] text-[#374151] text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all">
                <RefreshCw className="w-3.5 h-3.5" /> New Chat
              </button>
            </div>
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
            {messages.map(msg => <ChatBubble key={msg.id} msg={msg} />)}
            {isTyping && (
              <div className="flex gap-3">
                <div className="w-7 h-7 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center flex-shrink-0">
                  <Bot className="w-4 h-4 text-white" />
                </div>
                <div className="bg-white border border-[#E5E7EB] rounded-2xl rounded-bl-sm px-4 py-3 shadow-sm flex items-center gap-1.5">
                  {[0, 1, 2].map(d => (
                    <span key={d} className="w-2 h-2 rounded-full bg-[#9CA3AF] animate-bounce"
                      style={{ animationDelay: `${d * 0.15}s` }} />
                  ))}
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick suggestions */}
          <div className="px-5 py-3 border-t border-[#E5E7EB] bg-white">
            <div className="flex gap-2 flex-wrap mb-3">
              {QUICK_SUGGESTIONS.slice(0, 4).map(s => (
                <button key={s.label} onClick={() => sendMessage(s.prompt)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[#F3F4F6] hover:bg-[#EFF6FF] hover:text-[#2563EB] border border-transparent hover:border-[#BFDBFE] text-[#374151] text-xs font-medium transition-all">
                  <span>{s.icon}</span>{s.label}
                </button>
              ))}
            </div>

            {/* Input */}
            <div className="flex items-end gap-2">
              <div className="flex-1 relative">
                <textarea
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }}
                  placeholder="Describe an IVR flow, ask for improvements, or generate voice prompts…"
                  rows={2}
                  className="w-full px-4 py-3 pr-12 rounded-xl border border-[#E5E7EB] bg-white text-sm text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all resize-none"
                />
                <div className="absolute right-3 bottom-3 flex gap-1.5">
                  <button className="text-[#9CA3AF] hover:text-[#374151] transition-colors"><Paperclip className="w-4 h-4" /></button>
                  <button className="text-[#9CA3AF] hover:text-[#374151] transition-colors"><Mic className="w-4 h-4" /></button>
                </div>
              </div>
              <button onClick={() => sendMessage()}
                className="w-11 h-11 rounded-xl bg-gradient-to-br from-[#8B5CF6] to-[#2563EB] flex items-center justify-center text-white hover:opacity-90 transition-opacity shadow-lg flex-shrink-0">
                <Send className="w-4 h-4" />
              </button>
            </div>
            <p className="text-[#9CA3AF] text-[10px] mt-1.5 text-center">Press Enter to send · Shift+Enter for new line</p>
          </div>
        </div>

        {/* Right panel */}
        {rightPanel}
      </div>
    </TenantLayout>
  )
}
