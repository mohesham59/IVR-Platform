import type { FlowNode, FlowEdge, NodeType } from './types'
import { NODE_DEFS } from './nodeConfig'

const warnedEdges = new Set<string>()

let idSeq = 0

export const generateUniqueId = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `n_${(idSeq++).toString(36)}_${Math.random().toString(36).substring(2, 11)}_${Date.now().toString(36)}`
}

export function repairDoubleEncodedUtf8(str: string): string {
  if (!str) return str
  for (let i = 0; i < str.length; i++) {
    if (str.charCodeAt(i) > 255) {
      return str
    }
  }
  try {
    const bytes = new Uint8Array(str.length)
    for (let i = 0; i < str.length; i++) {
      bytes[i] = str.charCodeAt(i)
    }
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch (e) {
    return str
  }
}

export function validateGraph(
  nodes: FlowNode[],
  edges: FlowEdge[]
): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const finalNodes: FlowNode[] = []
  const finalEdges: FlowEdge[] = []

  const seenNodeIds = new Set<string>()
  const nodeMap = new Map<string, string>() // original duplicate id -> new unique id

  // 1. Validate nodes and resolve duplicate node ids
  const rawNodes = nodes || []
  rawNodes.forEach(node => {
    if (!node || !node.id) return

    const repairedNode: FlowNode = {
      ...node,
      title: repairDoubleEncodedUtf8(node.title || ''),
      subtitle: repairDoubleEncodedUtf8(node.subtitle || ''),
      ports: node.ports ? node.ports.map(p => ({
        ...p,
        label: repairDoubleEncodedUtf8(p.label || '')
      })) : []
    }

    if (seenNodeIds.has(repairedNode.id)) {
      const newId = generateUniqueId()
      console.warn(`[validateGraph] Duplicate node id detected: ${repairedNode.id}. Regenerated as: ${newId}`)
      nodeMap.set(repairedNode.id, newId)
      finalNodes.push({
        ...repairedNode,
        id: newId
      })
      seenNodeIds.add(newId)
    } else {
      finalNodes.push(repairedNode)
      seenNodeIds.add(repairedNode.id)
    }
  })

  const validNodeIds = new Set<string>(finalNodes.map(n => n.id))
  const seenEdgeIds = new Set<string>()

  // 2. Validate edges
  const rawEdges = edges || []
  rawEdges.forEach(edge => {
    if (!edge || !edge.id) return

    const updatedSource = nodeMap.get(edge.sourceId) ?? edge.sourceId
    const updatedTarget = nodeMap.get(edge.targetId) ?? edge.targetId

    // Check for circular self edges
    if (updatedSource === updatedTarget) {
      const edgeSig = `${edge.sourceId}:${edge.sourcePort ?? 'out'}->${edge.targetId}:${edge.targetPort ?? 'in'}`
      if (!warnedEdges.has(edgeSig)) {
        warnedEdges.add(edgeSig)
        console.warn(`[validateGraph] Circular self-edge detected and removed: source=${edge.sourceId}, target=${edge.targetId}`)
      }
      return
    }

    // Check for dangling edges / missing nodes
    if (!validNodeIds.has(updatedSource) || !validNodeIds.has(updatedTarget)) {
      console.warn(`[validateGraph] Dangling edge detected and removed: id=${edge.id}, source=${updatedSource}, target=${updatedTarget}`)
      return
    }

    // Check for duplicate edge ids
    let edgeId = edge.id
    if (seenEdgeIds.has(edgeId) || !edgeId.includes('_')) {
      edgeId = `e_${generateUniqueId()}`
      console.warn(`[validateGraph] Duplicate or invalid edge id detected: ${edge.id}. Regenerated as: ${edgeId}`)
    }

    finalEdges.push({
      ...edge,
      id: edgeId,
      sourceId: updatedSource,
      targetId: updatedTarget
    })
    seenEdgeIds.add(edgeId)
  })

  return { nodes: finalNodes, edges: finalEdges }
}

export function sanitizeFlow(flow: { nodes: FlowNode[]; edges: FlowEdge[] }): { nodes: FlowNode[]; edges: FlowEdge[] } {
  return validateGraph(flow.nodes, flow.edges)
}

/**
 * Converts a backend generateFlow API response into canvas-ready FlowNode[] and FlowEdge[].
 *
 * The backend returns a Flow object with `flowJson` (a string containing the AI-generated
 * flow structure). This parser attempts to extract nodes from flowJson when available,
 * otherwise builds a flow graph from the description and response metadata.
 */

const VALID_NODE_TYPES = new Set<string>(Object.keys(NODE_DEFS))

const X_START = 100
const X_STEP = 280
const Y_CENTER = 260

export function safeNodeType(raw: string | undefined): NodeType {
  if (!raw) return 'greeting'
  const lower = raw.toLowerCase().replace(/[\s_-]+/g, '_')
  if (VALID_NODE_TYPES.has(lower)) return lower as NodeType
  // common aliases
  if (lower.includes('menu') || lower.includes('dtmf')) return 'dtmf_menu'
  if (lower.includes('queue') || lower.includes('wait')) return 'queue'
  if (lower.includes('transfer') || lower.includes('agent')) return 'transfer'
  if (lower.includes('greet') || lower.includes('welcome')) return 'greeting'
  if (lower.includes('voice') || lower.includes('mail')) return 'voicemail'
  if (lower.includes('hour') || lower.includes('schedule') || lower.includes('time')) return 'hours'
  if (lower.includes('end') || lower.includes('hangup') || lower.includes('disconnect')) return 'end'
  if (lower.includes('play') || lower.includes('audio')) return 'playback'
  if (lower.includes('tts') || lower.includes('speech') || lower.includes('say') || lower.includes('prompt')) return 'tts'
  if (lower.includes('record')) return 'record'
  if (lower.includes('condition') || lower.includes('branch') || lower.includes('if')) return 'condition'
  if (lower.includes('api') || lower.includes('http') || lower.includes('rest')) return 'api'
  if (lower.includes('ai') || lower.includes('bot') || lower.includes('nlp')) return 'ai'
  if (lower.includes('webhook')) return 'webhook'
  if (lower.includes('holiday')) return 'holiday'
  if (lower.includes('variable') || lower.includes('set')) return 'variable'
  if (lower.includes('extension') || lower.includes('dial') || lower.includes('sip')) return 'extension'
  if (lower.includes('input') || lower.includes('collect') || lower.includes('digit')) return 'dtmf_input'
  if (lower.includes('database') || lower.includes('lookup') || lower.includes('db')) return 'database'
  return 'greeting'
}

function makeNode(id: string, type: NodeType, x: number, y: number, title: string, subtitle: string): FlowNode {
  const def = NODE_DEFS[type]
  return {
    id,
    type,
    x,
    y,
    title: title || def.label,
    subtitle: subtitle || def.description,
    status: 'valid',
    collapsed: false,
    disabled: false,
    ports: def.outputPorts.map(p => ({ ...p, type: 'output' as const })),
  }
}

interface ParsedFlowJson {
  nodes?: Array<{ id?: string; type?: string; label?: string; title?: string; name?: string; description?: string; prompt?: string }>
  edges?: Array<{ source?: string; target?: string; sourceId?: string; targetId?: string; from?: string; to?: string; sourcePort?: string; label?: string; condition?: string }>
}

function tryParseFlowJson(flowJson: string | undefined): ParsedFlowJson | null {
  if (!flowJson) return null
  try {
    const parsed = typeof flowJson === 'string' ? JSON.parse(flowJson) : flowJson
    if (parsed && typeof parsed === 'object') return parsed as ParsedFlowJson
  } catch {
    // AI response was non-JSON text
  }
  return null
}

function layoutTree(nodeCount: number, edgeMap: Map<string, string[]>, startIdx: number): Map<number, { x: number; y: number }> {
  const positions = new Map<number, { x: number; y: number }>()

  const NODE_H = 108
  const Y_GAP = 80
  const LEAF_HEIGHT = NODE_H + Y_GAP

  // Build parent-child tree mapping (only keep first visited edge to make it a tree)
  const treeChildren = new Map<number, number[]>()
  const visited = new Set<number>()
  const depthMap = new Map<number, number>()

  function traverse(u: number, depth: number) {
    visited.add(u)
    depthMap.set(u, depth)
    const children = edgeMap.get(String(u)) ?? []
    const validChildren: number[] = []
    for (const cStr of children) {
      const c = Number(cStr)
      if (!visited.has(c)) {
        validChildren.push(c)
        traverse(c, depth + 1)
      }
    }
    treeChildren.set(u, validChildren)
  }

  if (startIdx >= 0 && startIdx < nodeCount) {
    traverse(startIdx, 0)
  }

  // Calculate subtree height for each visited node
  const subtreeHeight = new Map<number, number>()
  function calcHeight(u: number): number {
    const children = treeChildren.get(u) ?? []
    if (children.length === 0) {
      subtreeHeight.set(u, LEAF_HEIGHT)
      return LEAF_HEIGHT
    }
    let total = 0
    for (const c of children) {
      total += calcHeight(c)
    }
    subtreeHeight.set(u, total)
    return total
  }

  if (startIdx >= 0 && startIdx < nodeCount) {
    calcHeight(startIdx)
  }

  // Position nodes recursively
  function positionNode(u: number, x: number, y: number) {
    positions.set(u, { x, y })
    const children = treeChildren.get(u) ?? []
    if (children.length === 0) return

    const totalHeight = subtreeHeight.get(u) ?? LEAF_HEIGHT
    let currentY = y - totalHeight / 2

    for (const c of children) {
      const childH = subtreeHeight.get(c) ?? LEAF_HEIGHT
      const childY = currentY + childH / 2
      positionNode(c, x + X_STEP, childY)
      currentY += childH
    }
  }

  const startY = Y_CENTER
  if (startIdx >= 0 && startIdx < nodeCount) {
    positionNode(startIdx, X_START, startY)
  }

  // Position any leftover/unvisited nodes (e.g. disconnected nodes, if any)
  let leftoverIndex = 0
  for (let i = 0; i < nodeCount; i++) {
    if (!positions.has(i)) {
      positions.set(i, { x: X_START + (depthMap.size + leftoverIndex) * X_STEP, y: startY })
      leftoverIndex++
    }
  }

  return positions
}

export function buildFlowFromResponse(response: {
  flowJson?: string
  name?: string
  description?: string
  nodes?: any[]
  edges?: any[]
  status?: string
}): { nodes: FlowNode[]; edges: FlowEdge[]; status?: string } {
  const directNodes = response.nodes
  const directEdges = response.edges
  const status = response.status

  const parsed = tryParseFlowJson(response.flowJson)

  const rawNodes = directNodes ?? parsed?.nodes
  const rawEdges = directEdges ?? parsed?.edges

  if (rawNodes && rawNodes.length > 0) {
    const idMap = new Map<string, string>()
    const edgeMap = new Map<string, string[]>()
    const nodes: FlowNode[] = []

    rawNodes.forEach((raw: any, i: number) => {
      const rawId = raw.id ?? `n${i + 1}`
      const nodeId = `n${i + 1}`
      idMap.set(String(rawId), nodeId)
      const type = safeNodeType(raw.type)
      const title = raw.title ?? raw.label ?? raw.name ?? NODE_DEFS[type].label
      const subtitle = raw.description ?? raw.subtitle ?? raw.prompt ?? NODE_DEFS[type].description
      nodes.push(makeNode(nodeId, type, 0, 0, title, subtitle))
    })

    if (rawEdges && rawEdges.length > 0) {
      rawEdges.forEach((raw: any) => {
        const srcOrig = String(raw.source ?? raw.sourceId ?? raw.from ?? '')
        const tgtOrig = String(raw.target ?? raw.targetId ?? raw.to ?? '')
        const srcId = idMap.get(srcOrig)
        const tgtId = idMap.get(tgtOrig)
        if (srcId && tgtId) {
          const srcIdx = nodes.findIndex(n => n.id === srcId)
          const tgtIdx = nodes.findIndex(n => n.id === tgtId)
          const children = edgeMap.get(String(srcIdx)) ?? []
          children.push(String(tgtIdx))
          edgeMap.set(String(srcIdx), children)
        }
      })
    }

    const startIdx = nodes.findIndex(n => n.type === 'start')
    const positions = layoutTree(nodes.length, edgeMap, startIdx >= 0 ? startIdx : 0)
    nodes.forEach((n, i) => {
      const pos = positions.get(i) ?? { x: X_START + i * X_STEP, y: Y_CENTER }
      n.x = pos.x
      n.y = pos.y
    })

    const edges: FlowEdge[] = []
    if (rawEdges && rawEdges.length > 0) {
      rawEdges.forEach((raw: any, i: number) => {
        const srcOrig = String(raw.source ?? raw.sourceId ?? raw.from ?? '')
        const tgtOrig = String(raw.target ?? raw.targetId ?? raw.to ?? '')
        const srcId = idMap.get(srcOrig)
        const tgtId = idMap.get(tgtOrig)
      // Fix 6a: Drop self-loop edges before they enter state
      if (srcId && tgtId && srcId !== tgtId) {
        const srcNode = nodes.find(n => n.id === srcId)
        let srcPort = raw.sourcePort
        if (!srcPort || !srcNode) {
          srcPort = srcNode?.ports[0]?.id ?? 'out'
        } else {
          const validPorts = srcNode.ports.map(p => p.id)
          if (!validPorts.includes(srcPort)) {
            srcPort = srcNode.ports[0]?.id ?? 'out'
          }
        }
        edges.push({
          id: `e_edge_${i + 1}`,
          sourceId: srcId,
          sourcePort: srcPort,
          targetId: tgtId,
          targetPort: 'in',
          label: raw.label ?? raw.condition,
        })
      }
      })
    } else {
      for (let i = 0; i < nodes.length - 1; i++) {
        const srcPort = nodes[i].ports[0]?.id ?? 'out'
        edges.push({
          id: `e_edge_${i + 1}`,
          sourceId: nodes[i].id,
          sourcePort: srcPort,
          targetId: nodes[i + 1].id,
          targetPort: 'in',
        })
      }
    }

    return { nodes, edges, status }
  }

  // No nodes were present in the backend response (flowJson was empty, absent, or unparseable).
  // Return an empty flow with status='empty' so callers can surface a proper error to the user
  // rather than silently rendering a pre-baked template that bypasses the LLM→FlowModel pipeline.
  console.warn('[buildFlowFromResponse] Backend returned no nodes — flow generation produced an empty result.')
  return { nodes: [], edges: [], status: 'empty' }
}

