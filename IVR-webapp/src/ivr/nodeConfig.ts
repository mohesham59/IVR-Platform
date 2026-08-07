import type { NodeType } from './types'

export interface NodeDef {
  type: NodeType
  label: string
  description: string
  category: string
  color: string        // border/accent
  bg: string           // node header bg
  textColor: string
  iconBg: string
  outputPorts: Array<{ id: string; label: string; color: string }>
}

export const NODE_DEFS: Record<NodeType, NodeDef> = {
  start: {
    type: 'start', label: 'Start', description: 'Flow entry point',
    category: 'Flow', color: '#22C55E', bg: '#F0FDF4', textColor: '#15803D', iconBg: '#DCFCE7',
    outputPorts: [{ id: 'out', label: 'Continue', color: '#22C55E' }],
  },
  greeting: {
    type: 'greeting', label: 'Greeting', description: 'Play a welcome message',
    category: 'Audio', color: '#3B82F6', bg: '#EFF6FF', textColor: '#1D4ED8', iconBg: '#DBEAFE',
    outputPorts: [{ id: 'out', label: 'Done', color: '#3B82F6' }],
  },
  playback: {
    type: 'playback', label: 'Playback', description: 'Play an audio file',
    category: 'Audio', color: '#3B82F6', bg: '#EFF6FF', textColor: '#1D4ED8', iconBg: '#DBEAFE',
    outputPorts: [{ id: 'out', label: 'Done', color: '#3B82F6' }],
  },
  tts: {
    type: 'tts', label: 'Text to Speech', description: 'Convert text to voice',
    category: 'Audio', color: '#06B6D4', bg: '#ECFEFF', textColor: '#0E7490', iconBg: '#CFFAFE',
    outputPorts: [{ id: 'out', label: 'Done', color: '#06B6D4' }],
  },
  dtmf_menu: {
    type: 'dtmf_menu', label: 'DTMF Menu', description: 'Multi-option dial menu',
    category: 'Input', color: '#8B5CF6', bg: '#F5F3FF', textColor: '#6D28D9', iconBg: '#EDE9FE',
    outputPorts: [
      { id: 'key1', label: 'Key 1', color: '#8B5CF6' },
      { id: 'key2', label: 'Key 2', color: '#7C3AED' },
      { id: 'key3', label: 'Key 3', color: '#6D28D9' },
      { id: 'key0', label: 'Key 0', color: '#5B21B6' },
      { id: 'timeout', label: 'Timeout', color: '#F59E0B' },
    ],
  },
  dtmf_input: {
    type: 'dtmf_input', label: 'DTMF Input', description: 'Collect digit input',
    category: 'Input', color: '#A78BFA', bg: '#F5F3FF', textColor: '#6D28D9', iconBg: '#EDE9FE',
    outputPorts: [
      { id: 'success', label: 'Got Input', color: '#8B5CF6' },
      { id: 'timeout', label: 'Timeout', color: '#F59E0B' },
    ],
  },
  voicemail: {
    type: 'voicemail', label: 'Voicemail', description: 'Record a voicemail',
    category: 'Audio', color: '#EC4899', bg: '#FDF2F8', textColor: '#BE185D', iconBg: '#FCE7F3',
    outputPorts: [{ id: 'done', label: 'Recorded', color: '#EC4899' }],
  },
  api: {
    type: 'api', label: 'API Request', description: 'Call external REST API',
    category: 'Integration', color: '#06B6D4', bg: '#ECFEFF', textColor: '#0E7490', iconBg: '#CFFAFE',
    outputPorts: [
      { id: 'success', label: 'Success', color: '#22C55E' },
      { id: 'error', label: 'Error', color: '#EF4444' },
    ],
  },
  variable: {
    type: 'variable', label: 'Set Variable', description: 'Set a flow variable',
    category: 'Logic', color: '#6B7280', bg: '#F9FAFB', textColor: '#374151', iconBg: '#F3F4F6',
    outputPorts: [{ id: 'out', label: 'Next', color: '#6B7280' }],
  },
  ai: {
    type: 'ai', label: 'AI Assistant', description: 'NLP-powered conversation',
    category: 'AI', color: '#7C3AED', bg: '#F5F3FF', textColor: '#6D28D9', iconBg: '#EDE9FE',
    outputPorts: [
      { id: 'resolved', label: 'Resolved', color: '#22C55E' },
      { id: 'escalate', label: 'Escalate', color: '#F59E0B' },
    ],
  },
  end: {
    type: 'end', label: 'End Call', description: 'Terminate the call',
    category: 'Flow', color: '#EF4444', bg: '#FEF2F2', textColor: '#B91C1C', iconBg: '#FEE2E2',
    outputPorts: [],
  },
}

export const CATEGORIES = ['Flow', 'Audio', 'Input', 'Logic', 'Integration', 'AI']

export const NODE_ICONS: Record<NodeType, string> = {
  start: '▶',
  greeting: '👋',
  playback: '🔊',
  tts: '💬',
  dtmf_menu: '☎',
  dtmf_input: '🔢',
  voicemail: '📩',
  api: '⚡',
  variable: '📦',
  ai: '🤖',
  end: '⛔',
}
