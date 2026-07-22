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
  vxmlFormId?: string
  vxmlPrompt?: string
  vxmlAudioFile?: string
  vxmlVarName?: string
  vxmlDest?: string
  vxmlChoices?: Record<string, string>
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

export interface VxmlProperty {
  name: string
  value: string
}

export interface VxmlVar {
  name: string
  expr?: string
}

export interface VxmlDialog {
  id: string
  type: 'form' | 'menu'
  prompt?: string
  audio?: string
  fields?: Array<{
    name: string
    prompt?: string
    audio?: string
    type?: string
    length?: number
  }>
  choices?: Array<{
    dtmf: string
    next: string
    label?: string
  }>
  next?: string
  transferDest?: string
}

export interface VxmlDocumentModel {
  version: '2.1'
  xmlNamespace: 'http://www.w3.org/2001/vxml'
  rootApp?: string
  vars: VxmlVar[]
  dialogs: VxmlDialog[]
}
