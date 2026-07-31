import type { FlowNode, FlowEdge, FlowVersion } from './types'

export const INITIAL_NODES: FlowNode[] = [
  {
    id: 'n1', type: 'start', x: 100, y: 260, title: 'Incoming Call',
    subtitle: 'All channels', status: 'valid', collapsed: false, disabled: false,
    ports: [{ id: 'out', label: 'Continue', type: 'output', color: '#22C55E' }],
  },
  {
    id: 'n2', type: 'greeting', x: 380, y: 260, title: 'Welcome Greeting',
    subtitle: 'Main Greeting', status: 'valid', collapsed: false, disabled: false,
    ports: [{ id: 'out', label: 'Done', type: 'output', color: '#3B82F6' }],
  },
  {
    id: 'n3', type: 'end', x: 660, y: 260, title: 'End Call',
    subtitle: 'Graceful disconnect', status: 'valid', collapsed: false, disabled: false, ports: [],
  },
]

export const INITIAL_EDGES: FlowEdge[] = [
  { id: 'e1', sourceId: 'n1', sourcePort: 'out', targetId: 'n2', targetPort: 'in' },
  { id: 'e2', sourceId: 'n2', sourcePort: 'out', targetId: 'n3', targetPort: 'in' },
]

export const VERSIONS: FlowVersion[] = [
  { id: 'v4', versionId: 'v4', sessionId: 'default', createdAt: '2026-12-12', flow: { nodes: INITIAL_NODES, edges: INITIAL_EDGES }, label: 'v4 — Current Draft', tag: 'draft', savedAt: 'Dec 12 · 10:42 AM', author: 'Marcus Webb' },
  { id: 'v3', versionId: 'v3', sessionId: 'default', createdAt: '2026-12-10', flow: { nodes: INITIAL_NODES, edges: INITIAL_EDGES }, label: 'v3 — Published', tag: 'published', savedAt: 'Dec 10 · 03:15 PM', author: 'Marcus Webb' },
  { id: 'v2', versionId: 'v2', sessionId: 'default', createdAt: '2026-12-05', flow: { nodes: INITIAL_NODES, edges: INITIAL_EDGES }, label: 'v2 — After-hours fix', tag: 'archived', savedAt: 'Dec 5 · 09:30 AM', author: 'Natalie R.' },
  { id: 'v1', versionId: 'v1', sessionId: 'default', createdAt: '2026-11-28', flow: { nodes: INITIAL_NODES, edges: INITIAL_EDGES }, label: 'v1 — Initial release', tag: 'archived', savedAt: 'Nov 28 · 02:00 PM', author: 'Marcus Webb' },
]
