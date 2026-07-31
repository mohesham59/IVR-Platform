import { useRef, useState, useCallback, useEffect, memo, type PointerEvent } from 'react'
import type { FlowNode, FlowEdge } from './types'
import { NODE_DEFS, NODE_ICONS } from './nodeConfig'
import {
  CheckCircle, AlertTriangle, XCircle, ChevronDown, ChevronUp,
  Maximize2, ZoomIn, ZoomOut,
} from 'lucide-react'

interface Viewport {
  x: number
  y: number
  scale: number
}

interface Props {
  nodes: FlowNode[]
  edges: FlowEdge[]
  selectedId: string | null
  selectedEdgeId?: string | null
  simulatingId: string | null
  viewport?: Viewport
  onViewportChange?: (vp: Viewport) => void
  onSelectNode: (id: string | null) => void
  onSelectEdge?: (id: string | null) => void
  onMoveNode: (id: string, x: number, y: number) => void
  onDropNode: (type: string, x: number, y: number) => void
  onCollapseNode: (id: string) => void
  onContextMenu: (e: React.MouseEvent, id: string) => void
  onAddEdge?: (edge: FlowEdge) => void
  onDeleteEdge?: (edgeId: string) => void
}

const NODE_W = 220
const NODE_H_FULL = 108
const NODE_H_COLLAPSED = 52
const PORT_R = 5

function getPortY(node: FlowNode, portIndex: number, total: number): number {
  const h = node.collapsed ? NODE_H_COLLAPSED : NODE_H_FULL
  if (total <= 1) return h / 2
  const spacing = (h - 24) / (total + 1)
  return 12 + spacing * (portIndex + 1)
}

function straightLine(x1: number, y1: number, x2: number, y2: number): string {
  return `M ${x1} ${y1} L ${x2} ${y2}`
}

function MiniMap({
  nodes,
  viewport,
  canvasW,
  canvasH,
  onNavigate,
}: {
  nodes: FlowNode[]
  viewport: Viewport
  canvasW: number
  canvasH: number
  onNavigate?: (x: number, y: number) => void
}) {
  const mmW = 160, mmH = 100
  const allX = nodes.length > 0 ? nodes.map(n => n.x) : [0]
  const allY = nodes.length > 0 ? nodes.map(n => n.y) : [0]
  const minX = Math.min(...allX) - 40
  const minY = Math.min(...allY) - 40
  const maxX = Math.max(...allX) + NODE_W + 40
  const maxY = Math.max(...allY) + NODE_H_FULL + 40
  const sceneW = Math.max(1, maxX - minX)
  const sceneH = Math.max(1, maxY - minY)
  const scaleX = mmW / sceneW
  const scaleY = mmH / sceneH
  const s = Math.min(scaleX, scaleY, 1)

  const vpX = (-viewport.x / viewport.scale - minX) * s
  const vpY = (-viewport.y / viewport.scale - minY) * s
  const vpW = (canvasW / viewport.scale) * s
  const vpH = (canvasH / viewport.scale) * s

  const handlePointerDown = (e: React.PointerEvent<SVGSVGElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    const update = (pe: any) => {
      const rect = e.currentTarget.getBoundingClientRect()
      const clickX = pe.clientX - rect.left
      const clickY = pe.clientY - rect.top
      const sceneX = clickX / s + minX
      const sceneY = clickY / s + minY
      onNavigate?.(sceneX, sceneY)
    }
    update(e)

    const handlePointerMove = (pe: any) => {
      update(pe)
    }

    const handlePointerUp = () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
  }

  return (
    <svg width={mmW} height={mmH} className="block cursor-crosshair" onPointerDown={handlePointerDown}>
      <rect width={mmW} height={mmH} fill="#F8FAFC" rx="6" />
      {nodes.map(n => {
        const def = NODE_DEFS[n.type] || { color: '#2563EB' }
        const nx = (n.x - minX) * s
        const ny = (n.y - minY) * s
        return (
          <rect key={n.id} x={nx} y={ny} width={NODE_W * s} height={NODE_H_FULL * s * 0.5}
            fill={def.color} rx={2} opacity={0.7} />
        )
      })}
      <rect x={Math.max(0, vpX)} y={Math.max(0, vpY)}
        width={Math.min(vpW, mmW)} height={Math.min(vpH, mmH)}
        fill="none" stroke="#2563EB" strokeWidth={1.5} rx={2} opacity={0.8} />
    </svg>
  )
}

function FlowCanvas({
  nodes,
  edges,
  selectedId,
  selectedEdgeId,
  simulatingId,
  viewport: externalViewport,
  onViewportChange,
  onSelectNode,
  onSelectEdge,
  onMoveNode,
  onDropNode,
  onCollapseNode,
  onContextMenu,
  onAddEdge,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [internalViewport, setInternalViewport] = useState<Viewport>({ x: -40, y: -80, scale: 0.82 })
  const viewport = externalViewport ?? internalViewport

  const updateViewport = useCallback((newVp: Viewport | ((prev: Viewport) => Viewport)) => {
    if (typeof newVp === 'function') {
      const computed = newVp(viewport)
      setInternalViewport(computed)
      onViewportChange?.(computed)
    } else {
      setInternalViewport(newVp)
      onViewportChange?.(newVp)
    }
  }, [viewport, onViewportChange])

  const [isPanning, setIsPanning] = useState(false)
  const [panStart, setPanStart] = useState({ x: 0, y: 0, vx: 0, vy: 0 })
  const [draggingId, setDraggingId] = useState<string | null>(null)
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 })
  const [connectingFrom, setConnectingFrom] = useState<{ nodeId: string; portId: string; x: number; y: number } | null>(null)
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 })
  const [containerSize, setContainerSize] = useState({ w: 1200, h: 700 })

  useEffect(() => {
    const obs = new ResizeObserver(entries => {
      for (const e of entries) {
        setContainerSize({ w: e.contentRect.width, h: e.contentRect.height })
      }
    })
    if (containerRef.current) obs.observe(containerRef.current)
    return () => obs.disconnect()
  }, [])

  const fitViewport = useCallback(() => {
    if (nodes.length === 0) return
    const allX = nodes.map(n => n.x)
    const allY = nodes.map(n => n.y)
    const minX = Math.min(...allX) - 50
    const minY = Math.min(...allY) - 50
    const maxX = Math.max(...allX) + NODE_W + 50
    const maxY = Math.max(...allY) + NODE_H_FULL + 50

    const flowW = maxX - minX
    const flowH = maxY - minY

    const padding = 40
    const scaleX = (containerSize.w - padding * 2) / flowW
    const scaleY = (containerSize.h - padding * 2) / flowH
    const scale = Math.max(0.3, Math.min(1.5, Math.min(scaleX, scaleY)))

    const x = (containerSize.w - flowW * scale) / 2 - minX * scale
    const y = (containerSize.h - flowH * scale) / 2 - minY * scale

    const newVp = { x, y, scale }
    setInternalViewport(newVp)
    onViewportChange?.(newVp)
  }, [nodes, containerSize, onViewportChange])

  // Fit viewport automatically when nodes or container size updates
  useEffect(() => {
    // Small timeout to allow container size resize observer to stabilize
    const timer = setTimeout(() => {
      fitViewport()
    }, 50)
    return () => clearTimeout(timer)
  }, [nodes.length, containerSize.w, containerSize.h, fitViewport])

  // Fix passive event warning by using native non-passive wheel event listener
  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const handleWheel = (e: WheelEvent) => {
      e.preventDefault()
      const rect = el.getBoundingClientRect()
      const cx = e.clientX - rect.left
      const cy = e.clientY - rect.top
      const factor = e.deltaY < 0 ? 1.1 : 0.9

      updateViewport(v => {
        const ns = Math.max(0.2, Math.min(2.5, v.scale * factor))
        return {
          x: cx - (cx - v.x) * (ns / v.scale),
          y: cy - (cy - v.y) * (ns / v.scale),
          scale: ns,
        }
      })
    }

    el.addEventListener('wheel', handleWheel, { passive: false })
    return () => {
      el.removeEventListener('wheel', handleWheel)
    }
  }, [updateViewport])

  const toCanvas = useCallback((clientX: number, clientY: number) => {
    if (!containerRef.current) return { x: 0, y: 0 }
    const rect = containerRef.current.getBoundingClientRect()
    return {
      x: (clientX - rect.left - viewport.x) / viewport.scale,
      y: (clientY - rect.top - viewport.y) / viewport.scale,
    }
  }, [viewport])

  const onCanvasPointerDown = useCallback((e: PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return
    if ((e.target as HTMLElement).closest('[data-node]')) return
    setIsPanning(true)
    setPanStart({ x: e.clientX, y: e.clientY, vx: viewport.x, vy: viewport.y })
    onSelectNode(null)
    onSelectEdge?.(null)
    setConnectingFrom(null)
  }, [viewport, onSelectNode, onSelectEdge])

  const onPointerMove = useCallback((e: PointerEvent<HTMLDivElement>) => {
    const cp = toCanvas(e.clientX, e.clientY)
    setMousePos(cp)
    if (isPanning) {
      updateViewport(v => ({
        ...v,
        x: panStart.vx + (e.clientX - panStart.x),
        y: panStart.vy + (e.clientY - panStart.y),
      }))
    }
    if (draggingId) {
      onMoveNode(
        draggingId,
        Math.round((cp.x - dragOffset.x) / 16) * 16,
        Math.round((cp.y - dragOffset.y) / 16) * 16,
      )
    }
  }, [isPanning, panStart, draggingId, dragOffset, toCanvas, onMoveNode, updateViewport])

  const onPointerUp = useCallback((e: PointerEvent<HTMLDivElement>) => {
    if (connectingFrom) {
      const cp = toCanvas(e.clientX, e.clientY)
      // Check if dropped on a target node
      const targetNode = nodes.find(n =>
        n.id !== connectingFrom.nodeId &&
        cp.x >= n.x && cp.x <= n.x + NODE_W &&
        cp.y >= n.y && cp.y <= n.y + NODE_H_FULL
      )
      if (targetNode && onAddEdge) {
        onAddEdge({
          id: `e_${connectingFrom.nodeId}_${targetNode.id}_${Date.now()}`,
          sourceId: connectingFrom.nodeId,
          sourcePort: connectingFrom.portId,
          targetId: targetNode.id,
          targetPort: 'in',
        })
      }
    }

    setIsPanning(false)
    setDraggingId(null)
    setConnectingFrom(null)
  }, [connectingFrom, nodes, toCanvas, onAddEdge])

  const startDragNode = useCallback((e: PointerEvent, id: string, nx: number, ny: number) => {
    e.stopPropagation()
    const cp = toCanvas(e.clientX, e.clientY)
    setDraggingId(id)
    setDragOffset({ x: cp.x - nx, y: cp.y - ny })
    onSelectNode(id)
    onSelectEdge?.(null)
  }, [toCanvas, onSelectNode, onSelectEdge])

  const onDragOver = useCallback((e: React.DragEvent) => { e.preventDefault() }, [])
  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    const type = e.dataTransfer.getData('node-type')
    if (!type) return
    const cp = toCanvas(e.clientX, e.clientY)
    onDropNode(type, Math.round(cp.x / 16) * 16, Math.round(cp.y / 16) * 16)
  }, [toCanvas, onDropNode])

  const handleMiniMapNavigate = (sceneX: number, sceneY: number) => {
    updateViewport(v => ({
      ...v,
      x: containerSize.w / 2 - sceneX * v.scale,
      y: containerSize.h / 2 - sceneY * v.scale,
    }))
  }

  // compute edge paths
  const edgePaths = edges.map(edge => {
    const src = nodes.find(n => n.id === edge.sourceId)
    const tgt = nodes.find(n => n.id === edge.targetId)
    if (!src || !tgt) return null
    const srcDef = NODE_DEFS[src.type] || { color: '#2563EB' }
    const portIndex = src.ports.findIndex(p => p.id === edge.sourcePort)
    const portCount = src.ports.length
    const portColor = src.ports[portIndex]?.color ?? srcDef.color

    const x1 = src.x + NODE_W
    const y1 = src.y + getPortY(src, Math.max(0, portIndex), Math.max(1, portCount))
    const x2 = tgt.x
    const y2 = tgt.y + (tgt.collapsed ? NODE_H_COLLAPSED : NODE_H_FULL) / 2

    const isActive = simulatingId === edge.targetId || simulatingId === edge.sourceId
    const isSelected = selectedEdgeId === edge.id

    return {
      ...edge, path: straightLine(x1, y1, x2, y2),
      color: portColor, x1, y1, x2, y2, isActive, isSelected,
    }
  }).filter(Boolean)

  const connectingPath = connectingFrom
    ? straightLine(connectingFrom.x, connectingFrom.y, mousePos.x, mousePos.y)
    : null

  return (
    <div
      ref={containerRef}
      className="relative w-full h-full overflow-hidden bg-[#F8FAFC] select-none"
      onPointerDown={onCanvasPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerLeave={onPointerUp}
      onDragOver={onDragOver}
      onDrop={onDrop}
      style={{ cursor: isPanning ? 'grabbing' : draggingId ? 'grabbing' : 'default' }}
    >
      {/* Grid background */}
      <svg className="absolute inset-0 w-full h-full pointer-events-none" style={{ opacity: 0.5 }}>
        <defs>
          <pattern id="smallGrid" width={16 * viewport.scale} height={16 * viewport.scale}
            x={viewport.x % (16 * viewport.scale)} y={viewport.y % (16 * viewport.scale)}
            patternUnits="userSpaceOnUse">
            <path d={`M ${16 * viewport.scale} 0 L 0 0 0 ${16 * viewport.scale}`}
              fill="none" stroke="#E5E7EB" strokeWidth={0.5} />
          </pattern>
          <pattern id="grid" width={80 * viewport.scale} height={80 * viewport.scale}
            x={viewport.x % (80 * viewport.scale)} y={viewport.y % (80 * viewport.scale)}
            patternUnits="userSpaceOnUse">
            <rect width={80 * viewport.scale} height={80 * viewport.scale} fill="url(#smallGrid)" />
            <path d={`M ${80 * viewport.scale} 0 L 0 0 0 ${80 * viewport.scale}`}
              fill="none" stroke="#D1D5DB" strokeWidth={0.8} />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#grid)" />
      </svg>

      {/* Canvas transform group */}
      <div className="absolute inset-0" style={{ transform: `translate(${viewport.x}px,${viewport.y}px) scale(${viewport.scale})`, transformOrigin: '0 0', willChange: 'transform' }}>
        {/* SVG edges */}
        <svg className="absolute overflow-visible pointer-events-none" style={{ left: 0, top: 0, width: '100%', height: '100%' }}>
          <defs>
            {['#22C55E','#EF4444','#8B5CF6','#F59E0B','#3B82F6','#6366F1','#EC4899','#06B6D4','#6B7280'].map(c => (
              <marker key={c} id={`arr-${c.replace('#','')}`} markerWidth="8" markerHeight="6"
                refX="7" refY="3" orient="auto">
                <path d="M0,0 L8,3 L0,6 Z" fill={c} />
              </marker>
            ))}
          </defs>

          {edgePaths.map(ep => ep && (
            <g key={ep.id} className="pointer-events-auto cursor-pointer" onClick={() => onSelectEdge?.(ep.id)}>
              <path
                d={ep.path}
                fill="none"
                stroke={ep.isSelected ? '#2563EB' : ep.color}
                strokeWidth={ep.isSelected ? 4 : ep.isActive ? 3 : 2}
                strokeOpacity={ep.isActive || ep.isSelected ? 1 : 0.7}
                markerEnd={`url(#arr-${(ep.isSelected ? '#2563EB' : ep.color).replace('#','')})`}
                strokeDasharray={ep.isActive ? '8 4' : undefined}
                style={ep.isActive ? { animation: 'dash 1s linear infinite' } : undefined}
              />
              {ep.label && (
                <text x={(ep.x1 + ep.x2) / 2} y={(ep.y1 + ep.y2) / 2 - 6}
                  textAnchor="middle" fill={ep.color} fontSize={10} fontFamily="Inter" className="font-semibold">
                  {ep.label}
                </text>
              )}
            </g>
          ))}

          {connectingPath && (
            <path d={connectingPath} fill="none" stroke="#2563EB" strokeWidth={2}
              strokeDasharray="6 4" opacity={0.8} />
          )}
        </svg>

        {/* Nodes */}
        {nodes.map(node => {
          const def = NODE_DEFS[node.type] || { label: node.type, iconBg: '#EFF6FF', color: '#2563EB' }
          const isSelected = selectedId === node.id
          const isSimulating = simulatingId === node.id
          const h = node.collapsed ? NODE_H_COLLAPSED : NODE_H_FULL

          return (
            <div
              key={node.id}
              data-node="true"
              onPointerDown={(e) => startDragNode(e, node.id, node.x, node.y)}
              onContextMenu={(e) => { e.preventDefault(); onContextMenu(e, node.id) }}
              style={{
                position: 'absolute',
                left: node.x, top: node.y,
                width: NODE_W, height: h,
                cursor: 'grab',
                zIndex: isSelected ? 20 : 10,
                transition: draggingId === node.id ? 'none' : 'box-shadow 0.15s',
              }}
              className={`rounded-xl border-2 bg-white overflow-visible transition-all
                ${isSelected
                  ? `border-[${def.color}] shadow-[0_0_0_4px_${def.color}22,0_8px_32px_${def.color}33]`
                  : 'border-[#E5E7EB] shadow-[0_2px_8px_rgba(0,0,0,0.08)] hover:border-[#D1D5DB] hover:shadow-[0_4px_16px_rgba(0,0,0,0.12)]'}
                ${isSimulating ? 'border-[#2563EB] shadow-[0_0_0_4px_#2563EB33,0_8px_32px_#2563EB22]' : ''}
                ${node.disabled ? 'opacity-50' : ''}
              `}
            >
              <div className="absolute inset-x-0 top-0 h-1.5 rounded-t-xl" style={{ backgroundColor: def.color }} />

              <div className="px-3 pt-3.5 pb-2 flex items-start gap-2.5">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-base"
                  style={{ backgroundColor: def.iconBg }}>
                  {NODE_ICONS[node.type] || '⚡'}
                </div>

                <div className="flex-1 min-w-0 mt-0.5">
                  <div className="flex items-center gap-1.5">
                    <p className="text-[#1F2937] font-semibold text-xs truncate leading-tight">{node.title}</p>
                    {node.status === 'valid' && <CheckCircle className="w-3 h-3 text-[#22C55E] flex-shrink-0" />}
                    {node.status === 'warning' && <AlertTriangle className="w-3 h-3 text-[#F59E0B] flex-shrink-0" />}
                    {node.status === 'error' && <XCircle className="w-3 h-3 text-[#EF4444] flex-shrink-0" />}
                  </div>
                  {!node.collapsed && (
                    <p className="text-[#9CA3AF] text-[10px] mt-0.5 truncate leading-tight">{node.subtitle}</p>
                  )}
                </div>

                <button
                  onPointerDown={e => e.stopPropagation()}
                  onClick={e => { e.stopPropagation(); onCollapseNode(node.id) }}
                  className="w-5 h-5 flex items-center justify-center text-[#9CA3AF] hover:text-[#374151] flex-shrink-0 mt-0.5"
                >
                  {node.collapsed
                    ? <ChevronDown className="w-3.5 h-3.5" />
                    : <ChevronUp className="w-3.5 h-3.5" />}
                </button>
              </div>

              {!node.collapsed && (
                <div className="px-3 pb-2.5">
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-semibold uppercase tracking-wide"
                    style={{ backgroundColor: def.iconBg, color: def.color }}>
                    {def.label}
                  </span>
                </div>
              )}

              {/* Input port */}
              {node.type !== 'start' && (
                <div
                  className="absolute flex items-center justify-center rounded-full border-2 border-white bg-[#9CA3AF] hover:bg-[#6B7280] transition-colors"
                  style={{
                    width: PORT_R * 2 + 4,
                    height: PORT_R * 2 + 4,
                    left: -(PORT_R + 2),
                    top: h / 2 - PORT_R - 2,
                    zIndex: 5,
                    boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
                  }}
                />
              )}

              {/* Output ports */}
              {(node.ports || []).map((port, i) => (
                <div
                  key={port.id}
                  className="absolute flex items-center group"
                  style={{
                    right: -(PORT_R + 2),
                    top: getPortY(node, i, node.ports.length) - PORT_R - 2,
                    zIndex: 5,
                  }}
                >
                  {!node.collapsed && node.ports.length > 1 && (
                    <span className="absolute right-full mr-1.5 text-[9px] font-medium whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity"
                      style={{ color: port.color }}>
                      {port.label}
                    </span>
                  )}
                  <div
                    onPointerDown={e => {
                      e.stopPropagation()
                      const cx = node.x + NODE_W
                      const cy = node.y + getPortY(node, i, node.ports.length)
                      setConnectingFrom({ nodeId: node.id, portId: port.id, x: cx, y: cy })
                    }}
                    className="rounded-full border-2 border-white cursor-crosshair hover:scale-125 transition-transform"
                    style={{
                      width: PORT_R * 2 + 4, height: PORT_R * 2 + 4,
                      backgroundColor: port.color,
                      boxShadow: `0 0 0 2px ${port.color}44`,
                    }}
                  />
                </div>
              ))}

              {isSimulating && (
                <div className="absolute inset-0 rounded-xl border-2 border-[#2563EB] animate-ping opacity-40 pointer-events-none" />
              )}
            </div>
          )
        })}
      </div>

      {/* Zoom controls */}
      <div className="absolute bottom-4 left-4 flex flex-col gap-1 z-20">
        <button onClick={() => updateViewport(v => ({ ...v, scale: Math.min(2.5, v.scale * 1.2) }))}
          className="w-8 h-8 rounded-lg bg-white border border-[#E5E7EB] shadow-sm flex items-center justify-center text-[#6B7280] hover:text-[#1F2937] hover:border-[#2563EB] transition-colors" title="Zoom In">
          <ZoomIn className="w-3.5 h-3.5" />
        </button>
        <button onClick={() => updateViewport(v => ({ ...v, scale: Math.max(0.2, v.scale * 0.8) }))}
          className="w-8 h-8 rounded-lg bg-white border border-[#E5E7EB] shadow-sm flex items-center justify-center text-[#6B7280] hover:text-[#1F2937] hover:border-[#2563EB] transition-colors" title="Zoom Out">
          <ZoomOut className="w-3.5 h-3.5" />
        </button>
        <button onClick={fitViewport}
          className="w-8 h-8 rounded-lg bg-white border border-[#E5E7EB] shadow-sm flex items-center justify-center text-[#6B7280] hover:text-[#1F2937] hover:border-[#2563EB] transition-colors" title="Fit View">
          <Maximize2 className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Scale indicator */}
      <div className="absolute bottom-4 left-14 bg-white border border-[#E5E7EB] rounded-lg shadow-sm px-2.5 py-1.5 z-20">
        <span className="text-[#6B7280] text-xs font-mono">{Math.round(viewport.scale * 100)}%</span>
      </div>

      {/* Mini map */}
      <div className="absolute bottom-4 right-4 z-20 rounded-xl overflow-hidden border border-[#E5E7EB] shadow-lg bg-white p-1">
        <MiniMap nodes={nodes} viewport={viewport} canvasW={containerSize.w} canvasH={containerSize.h} onNavigate={handleMiniMapNavigate} />
      </div>

      <style>{`
        @keyframes dash { to { stroke-dashoffset: -20; } }
      `}</style>
    </div>
  )
}

export default memo(FlowCanvas)
