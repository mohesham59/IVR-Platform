import type { FlowNode, FlowEdge, FlowVersion } from './types'

export const INITIAL_NODES: FlowNode[] = [
  {
    id: 'n1', type: 'start', x: 100, y: 260, title: 'Incoming Call',
    subtitle: 'All channels', status: 'valid', collapsed: false, disabled: false,
    ports: [{ id: 'out', label: 'Continue', type: 'output', color: '#22C55E' }],
  },
  {
    id: 'n2', type: 'hours', x: 310, y: 240, title: 'Business Hours',
    subtitle: 'Mon–Fri 08:00–18:00', status: 'valid', collapsed: false, disabled: false,
    ports: [
      { id: 'open', label: 'Open', type: 'output', color: '#22C55E' },
      { id: 'closed', label: 'Closed', type: 'output', color: '#EF4444' },
    ],
  },
  {
    id: 'n3', type: 'greeting', x: 570, y: 140, title: 'Welcome Greeting',
    subtitle: 'welcome_hospital.wav', status: 'valid', collapsed: false, disabled: false,
    ports: [{ id: 'out', label: 'Done', type: 'output', color: '#3B82F6' }],
  },
  {
    id: 'n4', type: 'voicemail', x: 570, y: 360, title: 'After-Hours VM',
    subtitle: 'afterhours_msg.wav', status: 'valid', collapsed: false, disabled: false,
    ports: [{ id: 'done', label: 'Recorded', type: 'output', color: '#EC4899' }],
  },
  {
    id: 'n5', type: 'dtmf_menu', x: 820, y: 120, title: 'Main IVR Menu',
    subtitle: '1·Appt  2·ER  3·Billing  0·Agent',
    status: 'valid', collapsed: false, disabled: false,
    ports: [
      { id: 'key1', label: 'Key 1', type: 'output', color: '#8B5CF6' },
      { id: 'key2', label: 'Key 2', type: 'output', color: '#7C3AED' },
      { id: 'key3', label: 'Key 3', type: 'output', color: '#6D28D9' },
      { id: 'key0', label: 'Key 0', type: 'output', color: '#5B21B6' },
      { id: 'timeout', label: 'Timeout', type: 'output', color: '#F59E0B' },
    ],
  },
  {
    id: 'n6', type: 'queue', x: 1090, y: 40, title: 'Appointments Queue',
    subtitle: 'appt-queue · Strategy: Round Robin',
    status: 'valid', collapsed: false, disabled: false,
    ports: [
      { id: 'answered', label: 'Answered', type: 'output', color: '#22C55E' },
      { id: 'abandoned', label: 'Abandoned', type: 'output', color: '#EF4444' },
      { id: 'overflow', label: 'Overflow', type: 'output', color: '#F59E0B' },
    ],
  },
  {
    id: 'n7', type: 'transfer', x: 1090, y: 220, title: 'Emergency Transfer',
    subtitle: 'Ext. 911 · Direct',
    status: 'warning', collapsed: false, disabled: false,
    ports: [
      { id: 'success', label: 'Transferred', type: 'output', color: '#6366F1' },
      { id: 'fail', label: 'Failed', type: 'output', color: '#EF4444' },
    ],
  },
  {
    id: 'n8', type: 'queue', x: 1090, y: 390, title: 'Billing Queue',
    subtitle: 'billing-queue · Strategy: Least Recent',
    status: 'valid', collapsed: false, disabled: false,
    ports: [
      { id: 'answered', label: 'Answered', type: 'output', color: '#22C55E' },
      { id: 'abandoned', label: 'Abandoned', type: 'output', color: '#EF4444' },
    ],
  },
  {
    id: 'n9', type: 'ai', x: 1090, y: 550, title: 'AI Assistant',
    subtitle: 'GPT-4 · General Inquiries',
    status: 'valid', collapsed: false, disabled: false,
    ports: [
      { id: 'resolved', label: 'Resolved', type: 'output', color: '#22C55E' },
      { id: 'escalate', label: 'Escalate', type: 'output', color: '#F59E0B' },
    ],
  },
  {
    id: 'n10', type: 'end', x: 1380, y: 100, title: 'End Call',
    subtitle: 'Graceful disconnect',
    status: 'valid', collapsed: false, disabled: false, ports: [],
  },
  {
    id: 'n11', type: 'end', x: 1380, y: 260, title: 'End Call',
    subtitle: 'After transfer',
    status: 'valid', collapsed: false, disabled: false, ports: [],
  },
  {
    id: 'n12', type: 'end', x: 1380, y: 590, title: 'End Call',
    subtitle: 'AI resolved',
    status: 'valid', collapsed: false, disabled: false, ports: [],
  },
  {
    id: 'n13', type: 'tts', x: 820, y: 360, title: 'After-Hours Prompt',
    subtitle: '"We are currently closed…"',
    status: 'valid', collapsed: false, disabled: false,
    ports: [{ id: 'out', label: 'Done', type: 'output', color: '#06B6D4' }],
  },
]

export const INITIAL_EDGES: FlowEdge[] = [
  { id: 'e1', sourceId: 'n1', sourcePort: 'out', targetId: 'n2', targetPort: 'in' },
  { id: 'e2', sourceId: 'n2', sourcePort: 'open', targetId: 'n3', targetPort: 'in' },
  { id: 'e3', sourceId: 'n2', sourcePort: 'closed', targetId: 'n13', targetPort: 'in' },
  { id: 'e4', sourceId: 'n3', sourcePort: 'out', targetId: 'n5', targetPort: 'in' },
  { id: 'e5', sourceId: 'n5', sourcePort: 'key1', targetId: 'n6', targetPort: 'in' },
  { id: 'e6', sourceId: 'n5', sourcePort: 'key2', targetId: 'n7', targetPort: 'in' },
  { id: 'e7', sourceId: 'n5', sourcePort: 'key3', targetId: 'n8', targetPort: 'in' },
  { id: 'e8', sourceId: 'n5', sourcePort: 'key0', targetId: 'n9', targetPort: 'in' },
  { id: 'e9', sourceId: 'n6', sourcePort: 'answered', targetId: 'n10', targetPort: 'in' },
  { id: 'e10', sourceId: 'n7', sourcePort: 'success', targetId: 'n11', targetPort: 'in' },
  { id: 'e11', sourceId: 'n9', sourcePort: 'resolved', targetId: 'n12', targetPort: 'in' },
  { id: 'e12', sourceId: 'n13', sourcePort: 'out', targetId: 'n4', targetPort: 'in' },
  { id: 'e13', sourceId: 'n5', sourcePort: 'timeout', targetId: 'n9', targetPort: 'in' },
  { id: 'e14', sourceId: 'n8', sourcePort: 'abandoned', targetId: 'n9', targetPort: 'in' },
]

export const VERSIONS: FlowVersion[] = [
  { id: 'v4', label: 'v4 — Current Draft', tag: 'draft', savedAt: 'Dec 12 · 10:42 AM', author: 'Marcus Webb' },
  { id: 'v3', label: 'v3 — Published', tag: 'published', savedAt: 'Dec 10 · 03:15 PM', author: 'Marcus Webb' },
  { id: 'v2', label: 'v2 — After-hours fix', tag: 'archived', savedAt: 'Dec 5 · 09:30 AM', author: 'Natalie R.' },
  { id: 'v1', label: 'v1 — Initial release', tag: 'archived', savedAt: 'Nov 28 · 02:00 PM', author: 'Marcus Webb' },
]
