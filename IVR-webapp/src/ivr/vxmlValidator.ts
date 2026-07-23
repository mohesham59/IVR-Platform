import type { FlowNode, FlowEdge } from './types'

export interface VxmlValidationError {
  type: 'error' | 'warning' | 'info'
  message: string
  nodeId?: string
}

/**
 * validateVxmlFlow — Validates canvas flow against VoiceXML 2.1 W3C standard constraints.
 */
export function validateVxmlFlow(nodes: FlowNode[], edges: FlowEdge[]): VxmlValidationError[] {
  const errors: VxmlValidationError[] = []

  const nodeMap = new Map<string, FlowNode>()
  nodes.forEach(n => nodeMap.set(n.id, n))

  const hasStart = nodes.some(n => n.type === 'start')
  if (!hasStart) {
    errors.push({
      type: 'error',
      message: 'VXML Standard Violation: Document must have an entry point (<form id="start"> or initial node).',
    })
  }

  const hasEnd = nodes.some(n => n.type === 'end' || n.type === 'voicemail')
  if (!hasEnd) {
    errors.push({
      type: 'warning',
      message: 'VXML Recommendation: Document should contain an explicit <disconnect/> or <exit/> dialog.',
    })
  }

  // Check disconnected menu options
  nodes.forEach(n => {
    if (n.type === 'dtmf_menu') {
      const outgoing = edges.filter(e => e.sourceId === n.id)
      if (outgoing.length === 0) {
        errors.push({
          type: 'error',
          message: `<menu id="${n.id}"> has no <choice> paths connected.`,
          nodeId: n.id,
        })
      }
    }

    if (n.type === 'transfer' && (!n.vxmlDest || n.vxmlDest.trim() === '')) {
      errors.push({
        type: 'warning',
        message: `<transfer id="${n.id}"> has empty destination URI. Defaulting to SIP/100.`,
        nodeId: n.id,
      })
    }
  })

  return errors
}
