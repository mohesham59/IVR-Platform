import { useRef, useState, useCallback, useEffect, type PointerEvent, type WheelEvent } from 'react'
import type { FlowNode, FlowEdge } from './types'
import { NODE_DEFS, NODE_ICONS } from './nodeConfig'
import {
  CheckCircle, AlertTriangle, XCircle, ChevronDown, ChevronUp,
  Maximize2, ZoomIn, ZoomOut,
} from 'lucide-react'

interface Props {
  nodes: FlowNode[]
  edges: FlowEdge[]
  selectedId: string | null
  simulatingId: string | null
  onSelectNode: (id: string | null) => void
  onMoveNode: (id: string, x: number, y: number) => void
  onDropNode: (type: string, x: number, y: number) => void
  onCollapseNode: (id: string) => void
  onContextMenu: (e: React.MouseEvent, id: string) => void
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

function cubicBezier(x1: number, y1: number, x2: number, y2: number): string {
  const cx = (x2 - x1) * 0.55
  return `M ${x1} ${y1} C ${x1 + cx} ${y1} ${x2 - cx} ${y2} ${x2} ${y2}`
}

function MiniMap({ nodes, viewport, canvasW, canvasH }: {
  nodes: FlowNode[]
  viewport: { x: number; y: number; scale: number }
  canvasW: number
  canvasH: number
}) {
  const mmW = 160, mmH = 100
  const allX = nodes.map(n => n.x)
  const allY = nodes.map(n => n.y)
  const minX = Math.min(...allX) - 40
  const minY = Math.min(...allY) - 40
  const maxX = Math.max(...allX) + NODE_W + 40
  const maxY = Math.max(...allY) + NODE_H_FULL + 40
  const sceneW = maxX - minX
  const sceneH = maxY - minY
  const scaleX = mmW / sceneW
  const scaleY = mmH / sceneH
  const s = Math.min(scaleX, scaleY, 1)

  const vpX = (-viewport.x / viewport.scale - minX) * s
  const vpY = (-viewport.y / viewport.scale - minY) * s
  const vpW = (canvasW / viewport.scale) * s
  const vpH = (canvasH / viewport.scale) * s

  return (
    <svg width={mmW} height={mmH} className="block">
      <rect width={mmW} height={mmH} fill="#F8FAFC" rx="6" />
      {nodes.map(n => {
        const def = NODE_DEFS[n.type]
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

export default function FlowCanvas({
  nodes, edges, selectedId, simulatingId,
  onSelectNode, onMoveNode, onDropNode, onCollapseNode, onContextMenu,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [viewport, setViewport] = useState({ x: -40, y: -80, scale: 0.82 })
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

  const toCanvas = useCallback((clientX: number, clientY: number) => {
    if (!containerRef.current) return { x: 0, y: 0 }
    const rect = containerRef.current.getBoundingClientRect()
    return {
      x: (clientX - rect.left - viewport.x) / viewport.scale,
      y: (clientY - rect.top - viewport.y) / viewport.scale,
    }
  }, [viewport])

  const onWheel = useCallback((e: WheelEvent<HTMLDivElement>) => {
    e.preventDefault()
    if (!containerRef.current) return
    const rect = containerRef.current.getBoundingClientRect()
    const cx = e.clientX - rect.left
    const cy = e.clientY - rect.top
    const factor = e.deltaY < 0 ? 1.1 : 0.9
    setViewport(v => {
      const ns = Math.max(0.2, Math.min(2.5, v.scale * factor))
      return {
        x: cx - (cx - v.x) * (ns / v.scale),
        y: cy - (cy - v.y) * (ns / v.scale),
        scale: ns,
      }
    })
  }, [])

  const onCanvasPointerDown = useCallback((e: PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return
    if ((e.target as HTMLElement).closest('[data-node]')) return
    setIsPanning(true)
    setPanStart({ x: e.clientX, y: e.clientY, vx: viewport.x, vy: viewport.y })
    onSelectNode(null)
    setConnectingFrom(null)
  }, [viewport, onSelectNode])

  const onPointerMove = useCallback((e: PointerEvent<HTMLDivElement>) => {
    const cp = toCanvas(e.clientX, e.clientY)
    setMousePos(cp)
    if (isPanning) {
      setViewport(v => ({
        ...v,
        x: panStart.vx + (e.clientX - panStart.x),
        y: panStart.vy + (e.clientY - panStart.y),
      }))
    }
    if (draggingId) {
      onMoveNode(draggingId,
        Math.round((cp.x - dragOffset.x) / 16) * 16,
        Math.round((cp.y - dragOffset.y) / 16) * 16,
      )
    }
  }, [isPanning, panStart, draggingId, dragOffset, toCanvas, onMoveNode])

  const onPointerUp = useCallback(() => {
    setIsPanning(false)
    setDraggingId(null)
    setConnectingFrom(null)
  }, [])

  const startDragNode = useCallback((e: PointerEvent, id: string, nx: number, ny: number) => {
    e.stopPropagation()
    const cp = toCanvas(e.clientX, e.clientY)
    setDraggingId(id)
    setDragOffset({ x: cp.x - nx, y: cp.y - ny })
    onSelectNode(id)
  }, [toCanvas, onSelectNode])

  const onDragOver = useCallback((e: React.DragEvent) => { e.preventDefault() }, [])
  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    const type = e.dataTransfer.getData('node-type')
    if (!type) return
    const cp = toCanvas(e.clientX, e.clientY)
    onDropNode(type, Math.round(cp.x / 16) * 16, Math.round(cp.y / 16) * 16)
  }, [toCanvas, onDropNode])

  // compute edge paths
  const edgePaths = edges.map(edge => {
    const src = nodes.find(n => n.id === edge.sourceId)
    const tgt = nodes.find(n => n.id === edge.targetId)
    if (!src || !tgt) return null
    const srcDef = NODE_DEFS[src.type]
    const portIndex = src.ports.findIndex(p => p.id === edge.sourcePort)
    const portCount = src.ports.length
    const portColor = src.ports[portIndex]?.color ?? srcDef.color

    const x1 = src.x + NODE_W
    const y1 = src.y + getPortY(src, portIndex, portCount)
    const x2 = tgt.x
    const y2 = tgt.y + (tgt.collapsed ? NODE_H_COLLAPSED : NODE_H_FULL) / 2

    const isActive = simulatingId === edge.targetId || simulatingId === edge.sourceId
    return {
      ...edge, path: cubicBezier(x1, y1, x2, y2),
      color: portColor, x1, y1, x2, y2, isActive,
    }
  }).filter(Boolean)

  const connectingPath = connectingFrom
    ? cubicBezier(connectingFrom.x, connectingFrom.y, mousePos.x, mousePos.y)
    : null

  return (
    <div ref={containerRef} className="relative w-full h-full overflow-hidden bg-[#F8FAFC] select-none"
      onWheel={onWheel} onPointerDown={onCanvasPointerDown} onPointerMove={onPointerMove}
      onPointerUp={onPointerUp} onPointerLeave={onPointerUp}
      onDragOver={onDragOver} onDrop={onDrop}
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
            <g key={ep.id}>
              <path d={ep.path} fill="none" stroke={ep.color} strokeWidth={ep.isActive ? 3 : 2}
                strokeOpacity={ep.isActive ? 1 : 0.7}
                markerEnd={`url(#arr-${ep.color.replace('#','')})`}
                strokeDasharray={ep.isActive ? '8 4' : undefined}
                style={ep.isActive ? { animation: 'dash 1s linear infinite' } : undefined}
              />
              {/* Edge label */}
              {ep.label && (
                <text x={(ep.x1 + ep.x2) / 2} y={(ep.y1 + ep.y2) / 2 - 6}
                  textAnchor="middle" fill={ep.color} fontSize={10} fontFamily="Inter">
                  {ep.label}
                </text>
              )}
            </g>
          ))}

          {/* Connecting line in progress */}
          {connectingPath && (
            <path d={connectingPath} fill="none" stroke="#2563EB" strokeWidth={2}
              strokeDasharray="6 4" opacity={0.8} />
          )}
        </svg>

        {/* Nodes */}
        {nodes.map(node => {
          const def = NODE_DEFS[node.type]
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
              {/* Header strip */}
              <div className="absolute inset-x-0 top-0 h-1.5 rounded-t-xl" style={{ backgroundColor: def.color }} />

              {/* Content */}
              <div className="px-3 pt-3.5 pb-2 flex items-start gap-2.5">
                {/* Icon */}
                <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-base"
                  style={{ backgroundColor: def.iconBg }}>
                  {NODE_ICONS[node.type]}
                </div>

                <div className="flex-1 min-w-0 mt-0.5">
                  <div className="flex items-center gap-1.5">
                    <p className="text-[#1F2937] font-semibold text-xs truncate leading-tight">{node.title}</p>
                    {/* Status indicator */}
                    {node.status === 'valid' && <CheckCircle className="w-3 h-3 text-[#22C55E] flex-shrink-0" />}
                    {node.status === 'warning' && <AlertTriangle className="w-3 h-3 text-[#F59E0B] flex-shrink-0" />}
                    {node.status === 'error' && <XCircle className="w-3 h-3 text-[#EF4444] flex-shrink-0" />}
                  </div>
                  {!node.collapsed && (
                    <p className="text-[#9CA3AF] text-[10px] mt-0.5 truncate leading-tight">{node.subtitle}</p>
                  )}
                </div>

                {/* Collapse */}
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

              {/* Type label */}
              {!node.collapsed && (
                <div className="px-3 pb-2.5">
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-semibold uppercase tracking-wide"
                    style={{ backgroundColor: def.iconBg, color: def.color }}>
                    {def.label}
                  </span>
                </div>
              )}

              {/* Input port — left center */}
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

              {/* Output ports — right side */}
              {node.ports.map((port, i) => (
                <div
                  key={port.id}
                  className="absolute flex items-center group"
                  style={{
                    right: -(PORT_R + 2),
                    top: getPortY(node, i, node.ports.length) - PORT_R - 2,
                    zIndex: 5,
                  }}
                >
                  {/* Port label */}
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

              {/* Simulating pulse */}
              {isSimulating && (
                <div className="absolute inset-0 rounded-xl border-2 border-[#2563EB] animate-ping opacity-40 pointer-events-none" />
              )}
            </div>
          )
        })}
      </div>

      {/* Zoom controls */}
      <div className="absolute bottom-4 left-4 flex flex-col gap-1 z-20">
        <button onClick={() => setViewport(v => ({ ...v, scale: Math.min(2.5, v.scale * 1.2) }))}
          className="w-8 h-8 rounded-lg bg-white border border-[#E5E7EB] shadow-sm flex items-center justify-center text-[#6B7280] hover:text-[#1F2937] hover:border-[#2563EB] transition-colors">
          <ZoomIn className="w-3.5 h-3.5" />
        </button>
        <button onClick={() => setViewport(v => ({ ...v, scale: Math.max(0.2, v.scale * 0.8) }))}
          className="w-8 h-8 rounded-lg bg-white border border-[#E5E7EB] shadow-sm flex items-center justify-center text-[#6B7280] hover:text-[#1F2937] hover:border-[#2563EB] transition-colors">
          <ZoomOut className="w-3.5 h-3.5" />
        </button>
        <button onClick={() => setViewport({ x: -40, y: -80, scale: 0.82 })}
          className="w-8 h-8 rounded-lg bg-white border border-[#E5E7EB] shadow-sm flex items-center justify-center text-[#6B7280] hover:text-[#1F2937] hover:border-[#2563EB] transition-colors">
          <Maximize2 className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Scale indicator */}
      <div className="absolute bottom-4 left-14 bg-white border border-[#E5E7EB] rounded-lg shadow-sm px-2.5 py-1.5 z-20">
        <span className="text-[#6B7280] text-xs font-mono">{Math.round(viewport.scale * 100)}%</span>
      </div>

      {/* Mini map */}
      <div className="absolute bottom-4 right-4 z-20 rounded-xl overflow-hidden border border-[#E5E7EB] shadow-lg bg-white p-1">
        <MiniMap nodes={nodes} viewport={viewport} canvasW={containerSize.w} canvasH={containerSize.h} />
      </div>

      <style>{`
        @keyframes dash { to { stroke-dashoffset: -20; } }
      `}</style>
    </div>
  )
}
