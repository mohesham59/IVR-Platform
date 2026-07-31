import type { FlowNode, FlowEdge } from './types'
import { generateUniqueId, repairDoubleEncodedUtf8 } from './flowParser'

export interface GraphDiagnostics {
  disconnectedNodes: FlowNode[]
  orphanNodes: FlowNode[]
  deadEnds: FlowNode[]
  convergingNodes: Array<{ id: string; title: string; count: number }>
  missingStart: boolean
  multipleStarts: FlowNode[]
  missingHangup: boolean
  missingErrorPath: FlowNode[]
  duplicateEdges: FlowEdge[]
  selfLoops: FlowEdge[]
  circularLoops: string[][]
  duplicateMenus: FlowNode[]
  valid: boolean
  score: number
}

/**
 * 1. validateGraph — Validates graph integrity, removes duplicate node/edge IDs,
 * circular self-loops, and dangling edges. Returns clean, immutable graph arrays.
 */
export function validateGraph(
  nodes: FlowNode[],
  edges: FlowEdge[]
): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const finalNodes: FlowNode[] = []
  const finalEdges: FlowEdge[] = []

  const seenNodeIds = new Set<string>()
  const nodeMap = new Map<string, string>() // original duplicate id -> new unique id

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
      nodeMap.set(repairedNode.id, newId)
      finalNodes.push({ ...repairedNode, id: newId })
      seenNodeIds.add(newId)
    } else {
      finalNodes.push(repairedNode)
      seenNodeIds.add(repairedNode.id)
    }
  })

  const validNodeIds = new Set<string>(finalNodes.map(n => n.id))
  const seenEdgeSignatures = new Set<string>()
  const seenEdgeIds = new Set<string>()

  const rawEdges = edges || []
  rawEdges.forEach(edge => {
    if (!edge || !edge.id) return

    const updatedSource = nodeMap.get(edge.sourceId) ?? edge.sourceId
    const updatedTarget = nodeMap.get(edge.targetId) ?? edge.targetId

    // Remove self loops
    if (updatedSource === updatedTarget) return

    // Remove dangling edges (missing source or target node)
    if (!validNodeIds.has(updatedSource) || !validNodeIds.has(updatedTarget)) return

    // Remove duplicate edges (same source, sourcePort, target, targetPort)
    const signature = `${updatedSource}:${edge.sourcePort ?? 'out'}->${updatedTarget}:${edge.targetPort ?? 'in'}`
    if (seenEdgeSignatures.has(signature)) return
    seenEdgeSignatures.add(signature)

    let edgeId = edge.id
    if (seenEdgeIds.has(edgeId) || !edgeId.includes('_')) {
      edgeId = `e_${updatedSource}_${updatedTarget}`
      if (seenEdgeIds.has(edgeId)) {
        edgeId = `e_${generateUniqueId()}`
      }
    }

    finalEdges.push({
      ...edge,
      id: edgeId,
      sourceId: updatedSource,
      targetId: updatedTarget,
      sourcePort: edge.sourcePort || 'out',
      targetPort: edge.targetPort || 'in'
    })
    seenEdgeIds.add(edgeId)
  })

  return { nodes: finalNodes, edges: finalEdges }
}

/**
 * 2. analyzeGraph — Performance-optimized (O(N) with Adjacency Maps) graph diagnostics.
 */
export function analyzeGraph(nodes: FlowNode[], edges: FlowEdge[]): GraphDiagnostics {
  const inMap = new Map<string, FlowEdge[]>()
  const outMap = new Map<string, FlowEdge[]>()

  nodes.forEach(n => {
    inMap.set(n.id, [])
    outMap.set(n.id, [])
  })

  const duplicateEdges: FlowEdge[] = []
  const selfLoops: FlowEdge[] = []
  const seenSigs = new Set<string>()

  edges.forEach(e => {
    if (e.sourceId === e.targetId) {
      selfLoops.push(e)
      return
    }
    const sig = `${e.sourceId}:${e.sourcePort}->${e.targetId}:${e.targetPort}`
    if (seenSigs.has(sig)) {
      duplicateEdges.push(e)
    } else {
      seenSigs.add(sig)
    }

    const ins = inMap.get(e.targetId)
    if (ins) ins.push(e)

    const outs = outMap.get(e.sourceId)
    if (outs) outs.push(e)
  })

  const starts = nodes.filter(n => n.type === 'start')
  const missingStart = starts.length === 0
  const multipleStarts = starts.length > 1 ? starts : []
  const missingHangup = !nodes.some(n => n.type === 'end')

  const disconnectedNodes: FlowNode[] = []
  const orphanNodes: FlowNode[] = []
  const deadEnds: FlowNode[] = []
  const missingErrorPath: FlowNode[] = []
  const convergingNodes: Array<{ id: string; title: string; count: number }> = []

  nodes.forEach(n => {
    const ins = inMap.get(n.id) || []
    const outs = outMap.get(n.id) || []

    if (ins.length === 0 && outs.length === 0 && n.type !== 'start') {
      disconnectedNodes.push(n)
    } else {
      if (ins.length === 0 && n.type !== 'start') {
        orphanNodes.push(n)
      }
      if (outs.length === 0 && n.type !== 'end') {
        deadEnds.push(n)
      }
    }

    const isMenuType = (t: string) => t === 'dtmf_menu' || t === 'menu' || t === 'MENU'
    const isConditionType = (t: string) => t === 'condition' || t === 'CONDITION'

    // Detect converging paths (multiple distinct non-menu source nodes converging directly to non-menu, non-start node)
    if (ins.length >= 2 && !isMenuType(n.type) && n.type !== 'start') {
      const distinctSourceIds = Array.from(new Set(ins.map(e => e.sourceId)))
      const nonMenuSources = distinctSourceIds.filter(srcId => {
        const srcNode = nodes.find(sn => sn.id === srcId)
        return srcNode && !isMenuType(srcNode.type)
      })
      if (nonMenuSources.length >= 2) {
        convergingNodes.push({ id: n.id, title: n.title || n.id, count: nonMenuSources.length })
      }
    }

    // Detect outgoing collapsed branching hubs (non-menu, non-condition node with >= 2 outgoing edges)
    if (outs.length >= 2 && !isMenuType(n.type) && !isConditionType(n.type)) {
      const distinctTargets = new Set(outs.map(e => e.targetId))
      if (distinctTargets.size >= 2) {
        if (!convergingNodes.some(c => c.id === n.id)) {
          convergingNodes.push({ id: n.id, title: n.title || n.id, count: outs.length })
        }
      }
    }

    if (n.type === 'api' || isMenuType(n.type) || n.type === 'dtmf_input') {
      const hasErrorOrTimeout = outs.some(e => 
        e.sourcePort === 'error' || e.sourcePort === 'timeout' || e.sourcePort === 'invalid'
      )
      if (!hasErrorOrTimeout) {
        missingErrorPath.push(n)
      }
    }
  })

  // Detect duplicate or redundant Menu nodes
  const duplicateMenus: FlowNode[] = []
  const isMenuType = (t: string) => t === 'dtmf_menu' || t === 'menu' || t === 'MENU'
  const menuNodes = nodes.filter(n => isMenuType(n.type))
  if (menuNodes.length >= 2) {
    const seenMenuTitles = new Set<string>()
    menuNodes.forEach(m => {
      const titleKey = (m.title || 'Main Menu').trim().toLowerCase()
      if (seenMenuTitles.has(titleKey)) {
        duplicateMenus.push(m)
      } else {
        seenMenuTitles.add(titleKey)
      }
    })
  }

  // Cycle detection via DFS
  const circularLoops: string[][] = []
  const visited = new Set<string>()
  const recStack = new Set<string>()

  function dfs(currId: string, path: string[]) {
    visited.add(currId)
    recStack.add(currId)
    path.push(currId)

    const nextEdges = outMap.get(currId) || []
    for (const edge of nextEdges) {
      const nextId = edge.targetId
      if (!visited.has(nextId)) {
        dfs(nextId, [...path])
      } else if (recStack.has(nextId)) {
        const cycleStart = path.indexOf(nextId)
        if (cycleStart !== -1) {
          circularLoops.push([...path.slice(cycleStart), nextId])
        }
      }
    }
    recStack.delete(currId)
  }

  starts.forEach(s => {
    if (!visited.has(s.id)) dfs(s.id, [])
  })

  let score = 100
  if (missingStart) score -= 30
  if (missingHangup) score -= 15
  score -= orphanNodes.length * 10
  score -= deadEnds.length * 5
  score -= missingErrorPath.length * 5
  score -= duplicateEdges.length * 5
  score -= selfLoops.length * 10
  score -= duplicateMenus.length * 15
  score = Math.max(0, Math.min(100, score))

  const valid = missingStart === false && 
                orphanNodes.length === 0 && 
                duplicateEdges.length === 0 && 
                selfLoops.length === 0 &&
                duplicateMenus.length === 0

  return {
    disconnectedNodes,
    orphanNodes,
    deadEnds,
    convergingNodes,
    missingStart,
    multipleStarts,
    missingHangup,
    missingErrorPath,
    duplicateEdges,
    selfLoops,
    circularLoops,
    duplicateMenus,
    valid,
    score
  }
}

/**
 * 3. reconnectAfterDelete — Smart Node Deletion with Intelligent Graph Healing.
 * When deleting a node, reconnects incoming nodes to outgoing nodes automatically.
 */
export function reconnectAfterDelete(
  nodes: FlowNode[],
  edges: FlowEdge[],
  deletedNodeId: string
): { nodes: FlowNode[]; edges: FlowEdge[]; reconnectedCount: number; wasStartNode: boolean; wasOnlyStart: boolean } {
  const targetNode = nodes.find(n => n.id === deletedNodeId)
  if (!targetNode) return { nodes, edges, reconnectedCount: 0, wasStartNode: false, wasOnlyStart: false }

  const wasStartNode = targetNode.type === 'start'
  const startNodes = nodes.filter(n => n.type === 'start')
  const wasOnlyStart = wasStartNode && startNodes.length === 1

  const incomingEdges = edges.filter(e => e.targetId === deletedNodeId)
  const outgoingEdges = edges.filter(e => e.sourceId === deletedNodeId)

  const remainingNodes = nodes.filter(n => n.id !== deletedNodeId)
  let remainingEdges = edges.filter(e => e.sourceId !== deletedNodeId && e.targetId !== deletedNodeId)

  let reconnectedCount = 0
  const newEdges: FlowEdge[] = []

  incomingEdges.forEach(inc => {
    outgoingEdges.forEach(out => {
      if (inc.sourceId !== out.targetId) {
        const edgeId = `e_heal_${inc.sourceId}_${out.targetId}`
        newEdges.push({
          id: edgeId,
          sourceId: inc.sourceId,
          sourcePort: inc.sourcePort || 'out',
          targetId: out.targetId,
          targetPort: out.targetPort || 'in',
          label: 'Auto-Healed'
        })
        reconnectedCount++
      }
    })
  })

  const mergedEdges = [...remainingEdges, ...newEdges]
  const validated = validateGraph(remainingNodes, mergedEdges)

  return {
    nodes: validated.nodes,
    edges: validated.edges,
    reconnectedCount,
    wasStartNode,
    wasOnlyStart
  }
}

/**
 * 4. optimizeFlow — Normalizes, layout-ranks, and heals graph structure cleanly.
 */
export function optimizeFlow(
  nodes: FlowNode[],
  edges: FlowEdge[]
): { nodes: FlowNode[]; edges: FlowEdge[]; summary: string } {
  const validated = validateGraph(nodes, edges)
  let currentNodes = validated.nodes
  let currentEdges = validated.edges

  const typeOrder: Record<string, number> = {
    start: 0,
    greeting: 1,
    playback: 1,
    tts: 1,
    dtmf_menu: 2,
    dtmf_input: 2,
    api: 3,
    database: 3,
    hours: 3,
    condition: 3,
    queue: 4,
    transfer: 4,
    voicemail: 4,
    end: 5
  }

  const sorted = [...currentNodes].sort((a, b) => (typeOrder[a.type] ?? 3) - (typeOrder[b.type] ?? 3))
  const rankCounts: Record<number, number> = {}

  const repositioned = sorted.map(n => {
    const rank = typeOrder[n.type] ?? 3
    rankCounts[rank] = (rankCounts[rank] ?? 0) + 1
    return {
      ...n,
      x: 100 + rank * 280,
      y: 80 + (rankCounts[rank] - 1) * 140
    }
  })

  const finalValidated = validateGraph(repositioned, currentEdges)
  return {
    nodes: finalValidated.nodes,
    edges: finalValidated.edges,
    summary: `Optimized layout and validated ${finalValidated.nodes.length} nodes & ${finalValidated.edges.length} connections.`
  }
}
