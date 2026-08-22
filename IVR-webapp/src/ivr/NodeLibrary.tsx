import { useState } from 'react'
import { Search, Star, Clock, ChevronDown, ChevronRight } from 'lucide-react'
import { NODE_DEFS, CATEGORIES, NODE_ICONS } from './nodeConfig'
import type { NodeType } from './types'

const FAVORITES: NodeType[] = ['dtmf_menu', 'greeting', 'ai']
const RECENT: NodeType[] = ['tts', 'api']

interface Props {
  collapsed: boolean
  onToggle: () => void
}

function DraggableNode({ type }: { type: NodeType }) {
  const def = NODE_DEFS[type]
  return (
    <div
      draggable
      onDragStart={e => { e.dataTransfer.setData('node-type', type); e.dataTransfer.effectAllowed = 'copy' }}
      className="flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-[#F3F4F6] cursor-grab active:cursor-grabbing transition-colors group"
    >
      <div className="w-7 h-7 rounded-md flex items-center justify-center text-sm flex-shrink-0"
        style={{ backgroundColor: def.iconBg }}>
        {NODE_ICONS[type]}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[#1F2937] text-xs font-medium truncate group-hover:text-[#2563EB] transition-colors">{def.label}</p>
        <p className="text-[#9CA3AF] text-[10px] truncate">{def.description}</p>
      </div>
      <div className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ backgroundColor: def.color }} />
    </div>
  )
}

function Section({ title, icon, children, defaultOpen = true }: {
  title: string; icon: React.ReactNode; children: React.ReactNode; defaultOpen?: boolean
}) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div>
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-2 px-3 py-2 text-[#6B7280] hover:text-[#374151] transition-colors"
      >
        <span className="text-[#9CA3AF]">{icon}</span>
        <span className="flex-1 text-left text-xs font-semibold uppercase tracking-wider text-[#9CA3AF]">{title}</span>
        {open ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
      </button>
      {open && <div className="pb-2">{children}</div>}
    </div>
  )
}

export default function NodeLibrary({ collapsed, onToggle }: Props) {
  const [search, setSearch] = useState('')
  const [openCats, setOpenCats] = useState<Set<string>>(new Set(CATEGORIES))

  const allNodes = Object.values(NODE_DEFS)
  const filtered = search
    ? allNodes.filter(d => d.label.toLowerCase().includes(search.toLowerCase()) || d.description.toLowerCase().includes(search.toLowerCase()))
    : null

  const toggleCat = (cat: string) => {
    setOpenCats(prev => {
      const s = new Set(prev)
      s.has(cat) ? s.delete(cat) : s.add(cat)
      return s
    })
  }

  if (collapsed) {
    return (
      <div className="w-12 bg-white border-r border-[#E5E7EB] flex flex-col items-center py-3 gap-2">
        <button onClick={onToggle} className="w-8 h-8 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
          <ChevronRight className="w-4 h-4" />
        </button>
        {allNodes.slice(0, 10).map(d => (
          <div key={d.type} draggable
            onDragStart={e => { e.dataTransfer.setData('node-type', d.type); e.dataTransfer.effectAllowed = 'copy' }}
            title={d.label}
            className="w-8 h-8 rounded-md flex items-center justify-center cursor-grab text-sm hover:scale-110 transition-transform"
            style={{ backgroundColor: d.iconBg }}>
            {NODE_ICONS[d.type as NodeType]}
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="w-60 bg-white border-r border-[#E5E7EB] flex flex-col flex-shrink-0 z-10">
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-[#E5E7EB]">
        <div className="flex-1">
          <p className="text-[#1F2937] font-semibold text-sm">Node Library</p>
          <p className="text-[#9CA3AF] text-[10px]">{allNodes.length} nodes available</p>
        </div>
        <button onClick={onToggle} className="w-7 h-7 rounded-lg flex items-center justify-center text-[#9CA3AF] hover:bg-[#F3F4F6] transition-colors">
          <ChevronDown className="w-3.5 h-3.5 rotate-90" />
        </button>
      </div>

      {/* Search */}
      <div className="px-3 py-2.5 border-b border-[#F3F4F6]">
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF]" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search nodes…"
            className="w-full h-8 pl-8 pr-3 rounded-lg border border-[#E5E7EB] bg-[#F9FAFB] text-xs text-[#1F2937] placeholder-[#9CA3AF] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {filtered ? (
          <div className="py-2">
            <p className="px-3 py-1 text-[9px] font-semibold uppercase tracking-wider text-[#9CA3AF]">
              {filtered.length} result{filtered.length !== 1 ? 's' : ''}
            </p>
            {filtered.map(d => <DraggableNode key={d.type} type={d.type} />)}
          </div>
        ) : (
          <>
            {/* Favorites */}
            <Section title="Favorites" icon={<Star className="w-3.5 h-3.5" />}>
              {FAVORITES.map(t => <DraggableNode key={t} type={t} />)}
            </Section>

            {/* Recently Used */}
            <Section title="Recent" icon={<Clock className="w-3.5 h-3.5" />} defaultOpen={false}>
              {RECENT.map(t => <DraggableNode key={t} type={t} />)}
            </Section>

            {/* Divider */}
            <div className="h-px bg-[#F3F4F6] mx-3 my-2" />

            {/* Categories */}
            {CATEGORIES.map(cat => {
              const catNodes = allNodes.filter(d => d.category === cat)
              const isOpen = openCats.has(cat)
              return (
                <div key={cat}>
                  <button
                    onClick={() => toggleCat(cat)}
                    className="w-full flex items-center gap-2 px-3 py-2 hover:bg-[#F9FAFB] transition-colors"
                  >
                    <span className="flex-1 text-left text-xs font-semibold text-[#374151]">{cat}</span>
                    <span className="text-[10px] text-[#9CA3AF] font-medium">{catNodes.length}</span>
                    {isOpen ? <ChevronDown className="w-3 h-3 text-[#9CA3AF]" /> : <ChevronRight className="w-3 h-3 text-[#9CA3AF]" />}
                  </button>
                  {isOpen && (
                    <div className="pb-1">
                      {catNodes.map(d => <DraggableNode key={d.type} type={d.type} />)}
                    </div>
                  )}
                </div>
              )
            })}
          </>
        )}
      </div>

      {/* Drag hint */}
      <div className="px-3 py-2.5 border-t border-[#F3F4F6] bg-[#F9FAFB]">
        <p className="text-[#9CA3AF] text-[10px] text-center">Drag nodes onto the canvas</p>
      </div>
    </div>
  )
}
