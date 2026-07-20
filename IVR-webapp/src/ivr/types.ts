export type NodeType =
  | 'start'
  | 'greeting'
  | 'playback'
  | 'tts'
  | 'dtmf_menu'
  | 'dtmf_input'
  | 'queue'
  | 'transfer'
  | 'extension'
  | 'voicemail'
  | 'record'
  | 'api'
  | 'database'
  | 'hours'
  | 'holiday'
  | 'condition'
  | 'variable'
  | 'webhook'
  | 'ai'
  | 'end'

export interface NodePort {
  id: string
  label: string
  type: 'output' | 'input'
  color: string
}

export interface FlowNode {
  id: string
  type: NodeType
  x: number
  y: number
  title: string
  subtitle: string
  status: 'valid' | 'warning' | 'error' | 'idle'
  collapsed: boolean
  disabled: boolean
  ports: NodePort[]
}

export interface FlowEdge {
  id: string
  sourceId: string
  sourcePort: string
  targetId: string
  targetPort: string
  label?: string
  animated?: boolean
}

export interface FlowVersion {
  id: string
  label: string
  tag: 'draft' | 'published' | 'archived'
  savedAt: string
  author: string
}
