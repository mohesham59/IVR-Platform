import { useState } from 'react'
import { ChevronDown, Save, Clock, RotateCcw, CheckCircle, Archive, GitBranch, AlertTriangle, XCircle, Info, Eye, Layers } from 'lucide-react'
import type { FlowNode, FlowVersion, FlowEdge } from './types'
import { NODE_DEFS, NODE_ICONS } from './nodeConfig'

export interface ValidationItem {
  type: 'error' | 'warning' | 'info'
  code?: string
  message: string
  nodeId?: string
}

export function isPlaceholderDestination(d: string): boolean {
  if (!d) return true
  return false
}

interface Props {
  selectedNode: FlowNode | null
  flowName: string
  nodesCount: number
  edgesCount: number
  versions: FlowVersion[]
  validationItems: ValidationItem[]
  activeTab: 'props' | 'versions' | 'validation'
  onTabChange: (t: 'props' | 'versions' | 'validation') => void
  onNodeChange?: (updatedNode: FlowNode) => void
  onRestoreVersion?: (version: FlowVersion) => void
  onSelectNode?: (nodeId?: string) => void
  onSaveVersion?: () => void
  selectedVersionId?: string
  onSelectVersion?: (version: FlowVersion | null) => void
  nodes?: FlowNode[]
  edges?: FlowEdge[]
  availablePrompts?: string[]
}

function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="block text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">{label}</label>
      {children}
    </div>
  )
}

function TextInput({ value, onChange, placeholder }: { value?: string; onChange?: (v: string) => void; placeholder?: string }) {
  return (
    <input
      value={value ?? ''}
      onChange={e => onChange?.(e.target.value)}
      placeholder={placeholder}
      className="w-full h-8 px-2.5 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all"
    />
  )
}

function SelectInput({ options, value, onChange }: { options: string[]; value?: string; onChange?: (v: string) => void }) {
  return (
    <div className="relative">
      <select
        value={value ?? options[0]}
        onChange={e => onChange?.(e.target.value)}
        className="w-full h-8 pl-2.5 pr-7 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] appearance-none cursor-pointer"
      >
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
      <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
    </div>
  )
}

function SearchableSelectInput({ options, value, onChange }: { options: string[]; value?: string; onChange?: (v: string) => void }) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  
  const filtered = options.filter(o => o.toLowerCase().includes(search.toLowerCase()));
  
  return (
    <div className="relative">
      <div 
        onClick={() => setIsOpen(!isOpen)}
        className="w-full h-8 pl-2.5 pr-7 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] flex items-center cursor-pointer overflow-hidden"
      >
        <span className="truncate">{value || 'Select a file...'}</span>
        <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
      </div>
      
      {isOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)} />
          <div className="absolute z-50 w-full mt-1 bg-white border border-[#E5E7EB] rounded-lg shadow-lg overflow-hidden">
            <div className="p-1.5 border-b border-[#E5E7EB]">
              <input 
                autoFocus
                className="w-full px-2 py-1.5 text-xs border border-[#E5E7EB] rounded bg-gray-50 focus:outline-none focus:border-[#2563EB]"
                placeholder="Search audio files..."
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
            <div className="max-h-48 overflow-y-auto">
              {filtered.length > 0 ? filtered.map(o => (
                <div 
                  key={o} 
                  className={`px-2.5 py-1.5 text-xs cursor-pointer hover:bg-[#EFF6FF] hover:text-[#2563EB] truncate ${value === o ? 'bg-[#EFF6FF] text-[#2563EB] font-medium' : 'text-[#1F2937]'}`}
                  onClick={() => {
                    onChange?.(o);
                    setIsOpen(false);
                    setSearch('');
                  }}
                >
                  {o}
                </div>
              )) : (
                <div className="px-2.5 py-2 text-xs text-[#9CA3AF] text-center">No results found</div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function Toggle({ label, checked, onChange }: { label: string; checked?: boolean; onChange?: (val: boolean) => void }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-[#374151] text-xs">{label}</span>
      <button
        type="button"
        onClick={() => onChange?.(!checked)}
        className={`w-8 h-4.5 rounded-full transition-colors relative flex-shrink-0 ${checked ? 'bg-[#2563EB]' : 'bg-[#E5E7EB]'}`}
        style={{ height: '18px', width: '32px' }}
      >
        <span className={`absolute top-0.5 w-3.5 h-3.5 rounded-full bg-white shadow transition-all ${checked ? 'left-3.5' : 'left-0.5'}`} />
      </button>
    </div>
  )
}

function NodePropsContent({ node, onNodeChange, availablePrompts = [] }: { node: FlowNode; onNodeChange?: (updated: FlowNode) => void; availablePrompts?: string[] }) {
  const def = NODE_DEFS[node.type] || { label: node.type, description: '', iconBg: '#EFF6FF', color: '#2563EB' }

  const handleUpdate = (fields: Partial<FlowNode>) => {
    if (onNodeChange) {
      onNodeChange({ ...node, ...fields })
    }
  }

  return (
    <div className="space-y-4">
      {/* Node identity */}
      <div className="flex items-center gap-3 p-3 rounded-xl border border-[#E5E7EB] bg-[#F9FAFB]">
        <div className="w-9 h-9 rounded-xl flex items-center justify-center text-base flex-shrink-0" style={{ backgroundColor: def.iconBg }}>
          {NODE_ICONS[node.type] || '⚡'}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[#1F2937] font-semibold text-sm truncate">{node.title}</p>
          <span className="inline-flex px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wide mt-0.5"
            style={{ backgroundColor: def.iconBg, color: def.color }}>{def.label}</span>
        </div>
      </div>

      {/* Basic settings */}
      <section className="space-y-3">
        <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Node Settings</h4>

        <FieldRow label="Node Name">
          <TextInput value={node.title} onChange={v => handleUpdate({ title: v })} />
        </FieldRow>
        <FieldRow label="Description">
          <TextInput value={node.subtitle} onChange={v => handleUpdate({ subtitle: v })} />
        </FieldRow>

        {/* Type-specific fields */}
        {(node.type === 'greeting' || node.type === 'playback') && (
          <FieldRow label="Audio File">
            <SearchableSelectInput
              options={availablePrompts?.length ? availablePrompts : ['welcome_hospital.wav', 'afterhours_msg.wav', 'hold_music.wav', 'beep.wav']}
              value={node.subtitle || (availablePrompts?.length ? availablePrompts[0] : 'welcome_hospital.wav')}
              onChange={v => handleUpdate({ subtitle: v })}
            />
          </FieldRow>
        )}

        {node.type === 'dtmf_menu' && (
          <>
            <FieldRow label="Prompt File">
              <SearchableSelectInput 
                options={availablePrompts?.length ? availablePrompts : ['menu_main.wav', 'menu_billing.wav', 'menu_support.wav']} 
                value={node.prompt}
                onChange={v => handleUpdate({ prompt: v })}
              />
            </FieldRow>
            <FieldRow label="Invalid Input Audio">
              <SearchableSelectInput 
                options={availablePrompts?.length ? availablePrompts : ['invalid_entry.wav']} 
                value={node.invalidPrompt}
                onChange={v => handleUpdate({ invalidPrompt: v })}
              />
            </FieldRow>
            <FieldRow label="Timeout Audio">
              <SearchableSelectInput 
                options={availablePrompts?.length ? availablePrompts : ['timeout_msg.wav']} 
                value={node.timeoutPrompt}
                onChange={v => handleUpdate({ timeoutPrompt: v })}
              />
            </FieldRow>
            <FieldRow label="Max Retries">
              <SelectInput 
                options={['1', '2', '3', '4', '5']} 
                value={node.maxRetries?.toString() || '3'} 
                onChange={v => handleUpdate({ maxRetries: parseInt(v) })}
              />
            </FieldRow>
            <FieldRow label="Timeout (sec)">
              <TextInput 
                value={node.timeoutSecs?.toString() || '5'} 
                onChange={v => handleUpdate({ timeoutSecs: parseInt(v) || 5 })}
              />
            </FieldRow>
            
            <div className="mt-4 pt-3 border-t border-[#E5E7EB]">
              <div className="flex items-center justify-between mb-2">
                <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Menu Options</h4>
                <button 
                  type="button"
                  onClick={() => {
                    const newPorts = [...node.ports];
                    const nextNum = node.ports.filter(p => p.id.startsWith('key')).length + 1;
                    const digit = (nextNum % 10).toString();
                    newPorts.unshift({ id: `key${digit}`, label: `Key ${digit}`, color: def.color, type: 'output' });
                    handleUpdate({ ports: newPorts });
                  }}
                  className="text-xs text-[#2563EB] hover:underline font-medium"
                >
                  + Add Option
                </button>
              </div>
              <div className="space-y-2">
                {node.ports.filter(p => p.id !== 'timeout').map(port => {
                  const digit = port.id.replace('key', '');
                  return (
                    <div key={port.id} className="flex gap-2 items-center">
                      <div className="w-12 flex-shrink-0">
                        <SelectInput 
                          options={['0','1','2','3','4','5','6','7','8','9','*','#']}
                          value={digit}
                          onChange={v => {
                            const newPorts = node.ports.map(p => 
                              p.id === port.id ? { ...p, id: `key${v}`, label: `Key ${v}` } : p
                            );
                            handleUpdate({ ports: newPorts });
                          }}
                        />
                      </div>
                      <div className="flex-1">
                        <TextInput 
                          value={port.label.replace(`Key ${digit}`, '').trim() || port.label} 
                          onChange={v => {
                            const newPorts = node.ports.map(p => 
                              p.id === port.id ? { ...p, label: `Key ${digit} - ${v}` } : p
                            );
                            handleUpdate({ ports: newPorts });
                          }}
                        />
                      </div>
                      <button 
                        type="button" 
                        onClick={() => {
                          handleUpdate({ ports: node.ports.filter(p => p.id !== port.id) });
                        }}
                        className="text-[#9CA3AF] hover:text-[#EF4444] transition-colors"
                      >
                        <XCircle className="w-4 h-4" />
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          </>
        )}

        {node.type === 'api' && (
          <>
            <FieldRow label="Method">
              <SelectInput options={['GET', 'POST', 'PUT', 'DELETE']} value="GET" />
            </FieldRow>
            <FieldRow label="Endpoint URL">
              <TextInput value={node.subtitle?.startsWith('http') ? node.subtitle : 'https://api.nexusivr.com/verify'} onChange={v => handleUpdate({ subtitle: v })} />
            </FieldRow>
          </>
        )}
        {node.type === 'ai' && (
          <>
            <FieldRow label="AI Model">
              <SelectInput options={['llama-3.3-70b-versatile', 'granite3.2:2b']} />
            </FieldRow>
            <FieldRow label="System Prompt">
              <textarea
                className="w-full px-2.5 py-2 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] resize-none"
                rows={3}
                value={node.subtitle || 'You are an AI IVR assistant. Assist callers clearly.'}
                onChange={e => handleUpdate({ subtitle: e.target.value })}
              />
            </FieldRow>
          </>
        )}
      </section>

      {/* Advanced */}
      <section className="space-y-2.5">
        <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Advanced</h4>
        <Toggle label="Node Disabled" checked={node.disabled} onChange={val => handleUpdate({ disabled: val })} />
      </section>

      {/* Action */}
      <button
        onClick={() => handleUpdate({ status: 'valid' })}
        className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/20"
      >
        <Save className="w-3.5 h-3.5" /> Apply Node Changes
      </button>
    </div>
  )
}

function VersionsContent({
  versions,
  onRestoreVersion,
  onSaveVersion,
  selectedVersionId,
  onSelectVersion,
  currentNodes = [],
}: {
  versions: FlowVersion[]
  onRestoreVersion?: (v: FlowVersion) => void
  onSaveVersion?: () => void
  selectedVersionId?: string | null
  onSelectVersion?: (version: FlowVersion | null) => void
  currentNodes?: FlowNode[]
}) {
  const [comparingVer, setComparingVer] = useState<FlowVersion | null>(null)

  const tagConfig = {
    draft: { cls: 'bg-[#FEF9C3] text-[#A16207]', icon: <Clock className="w-3 h-3" /> },
    published: { cls: 'bg-[#DCFCE7] text-[#15803D]', icon: <CheckCircle className="w-3 h-3" /> },
    archived: { cls: 'bg-[#F3F4F6] text-[#6B7280]', icon: <Archive className="w-3 h-3" /> },
  }

  const getDiffs = (ver: FlowVersion) => {
    const activeNodesMap = new Map(currentNodes.map(n => [n.id, n]))
    const verNodesMap = new Map((ver.nodes || []).map(n => [n.id, n]))

    const added: string[] = []
    const deleted: string[] = []
    const modified: string[] = []

    currentNodes.forEach(n => {
      const oldNode = verNodesMap.get(n.id)
      if (!oldNode) {
        added.push(n.title || n.id)
      } else if (oldNode.title !== n.title || oldNode.subtitle !== n.subtitle || oldNode.disabled !== n.disabled) {
        modified.push(n.title || n.id)
      }
    })

    ;(ver.nodes || []).forEach(n => {
      if (!activeNodesMap.has(n.id)) {
        deleted.push(n.title || n.id)
      }
    })

    return { added, deleted, modified }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Flow Versions</h4>
        <button onClick={onSaveVersion} className="flex items-center gap-1 text-[#2563EB] text-[10px] font-medium hover:underline">
          <GitBranch className="w-3 h-3" /> Create Version
        </button>
      </div>

      {comparingVer && (() => {
        const diffs = getDiffs(comparingVer)
        const totalDiffs = diffs.added.length + diffs.deleted.length + diffs.modified.length
        return (
          <div className="p-3 rounded-xl border border-[#2563EB] bg-[#EFF6FF] space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-[#2563EB]">Comparing {comparingVer.label}</span>
              <button onClick={() => setComparingVer(null)} className="text-[#9CA3AF] hover:text-[#1F2937] text-xs">Close</button>
            </div>
            
            <div className="space-y-1.5 text-[11px] text-[#374151]">
              {totalDiffs === 0 ? (
                <p className="text-xs text-[#22C55E] font-medium">Flow structure is identical to current draft.</p>
              ) : (
                <>
                  {diffs.added.length > 0 && (
                    <div>
                      <span className="font-semibold text-[#15803D]">+ Added ({diffs.added.length}):</span>
                      <p className="text-[#6B7280] pl-2">{diffs.added.join(', ')}</p>
                    </div>
                  )}
                  {diffs.deleted.length > 0 && (
                    <div>
                      <span className="font-semibold text-[#B91C1C]">- Deleted ({diffs.deleted.length}):</span>
                      <p className="text-[#6B7280] pl-2">{diffs.deleted.join(', ')}</p>
                    </div>
                  )}
                  {diffs.modified.length > 0 && (
                    <div>
                      <span className="font-semibold text-[#B45309]">~ Modified ({diffs.modified.length}):</span>
                      <p className="text-[#6B7280] pl-2">{diffs.modified.join(', ')}</p>
                    </div>
                  )}
                </>
              )}
            </div>

            <button
              onClick={() => { onRestoreVersion?.(comparingVer); setComparingVer(null); }}
              className="w-full py-1 rounded bg-[#2563EB] text-white text-[11px] font-semibold hover:bg-[#1E40AF]"
            >
              Restore This Version
            </button>
          </div>
        )
      })()}

      <div className="space-y-2">
        {versions.map((v) => {
          const tc = tagConfig[v.tag] || tagConfig.draft
          const isSelected = selectedVersionId === v.id
          return (
            <div
              key={v.id}
              onClick={() => onSelectVersion?.(isSelected ? null : v)}
              className={`p-3 rounded-xl border transition-all cursor-pointer ${
                isSelected ? 'border-[#2563EB] bg-[#EFF6FF]' : 'border-[#E5E7EB] bg-white hover:border-[#D1D5DB]'
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="flex-1 min-w-0">
                  <p className={`text-xs font-semibold ${isSelected ? 'text-[#2563EB]' : 'text-[#1F2937]'}`}>{v.label}</p>
                  <p className="text-[#9CA3AF] text-[10px] mt-0.5">{v.savedAt} · {v.author}</p>
                </div>
                <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-semibold flex-shrink-0 ${tc.cls}`}>
                  {tc.icon}{v.tag}
                </span>
              </div>
              <div className="mt-2 flex items-center gap-3" onClick={e => e.stopPropagation()}>
                <button onClick={() => onRestoreVersion?.(v)} className="flex items-center gap-1 text-[#6B7280] text-[10px] hover:text-[#2563EB] transition-colors">
                  <RotateCcw className="w-3 h-3" /> Restore
                </button>
                <button onClick={() => setComparingVer(v)} className="flex items-center gap-1 text-[#6B7280] text-[10px] hover:text-[#2563EB] transition-colors">
                  <Eye className="w-3 h-3" /> Compare
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function ValidationContent({ items, onSelectNode }: { items: ValidationItem[]; onSelectNode?: (id?: string) => void }) {
  const errors = items.filter(i => i.type === 'error')
  const warnings = items.filter(i => i.type === 'warning')
  const info = items.filter(i => i.type === 'info')
  const isValid = errors.length === 0

  const iconMap = {
    error: <XCircle className="w-3.5 h-3.5 text-[#EF4444]" />,
    warning: <AlertTriangle className="w-3.5 h-3.5 text-[#F59E0B]" />,
    info: <Info className="w-3.5 h-3.5 text-[#2563EB]" />,
  }

  return (
    <div className="space-y-4">
      {/* Overall status */}
      <div className={`flex items-center gap-3 p-3 rounded-xl border ${isValid ? 'bg-[#F0FDF4] border-[#BBF7D0]' : 'bg-[#FEF2F2] border-[#FECACA]'}`}>
        {isValid
          ? <CheckCircle className="w-5 h-5 text-[#22C55E]" />
          : <XCircle className="w-5 h-5 text-[#EF4444]" />}
        <div>
          <p className={`font-semibold text-xs ${isValid ? 'text-[#15803D]' : 'text-[#B91C1C]'}`}>
            {isValid ? 'Flow is valid' : `${errors.length} error${errors.length > 1 ? 's' : ''} found`}
          </p>
          <p className="text-[#9CA3AF] text-[10px]">
            {errors.length} errors · {warnings.length} warnings · {info.length} suggestions
          </p>
        </div>
      </div>

      {[
        { label: 'Errors', items: errors, type: 'error' as const },
        { label: 'Warnings', items: warnings, type: 'warning' as const },
        { label: 'Info', items: info, type: 'info' as const },
      ].filter(g => g.items.length > 0).map(group => (
        <section key={group.label} className="space-y-2">
          <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">{group.label}</h4>
          <div className="space-y-1.5">
            {group.items.map((item, i) => (
              <div
                key={i}
                onClick={() => item.nodeId && onSelectNode?.(item.nodeId)}
                className="flex items-start gap-2 p-2.5 rounded-lg border border-[#F3F4F6] hover:border-[#2563EB] bg-white transition-colors cursor-pointer"
              >
                <span className="flex-shrink-0 mt-0.5">{iconMap[item.type]}</span>
                <div className="flex-1">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {item.code && (
                      <span className="px-1.5 py-0.5 rounded bg-[#F3F4F6] text-[#4B5563] text-[9px] font-mono font-bold uppercase border border-[#E5E7EB]">
                        {item.code}
                      </span>
                    )}
                    <span className="text-[#374151] text-xs font-medium">{item.message}</span>
                  </div>
                  {item.nodeId && <span className="text-[10px] text-[#2563EB] block mt-0.5">Focus Node: {item.nodeId}</span>}
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

export default function PropertiesPanel({
  selectedNode,
  flowName,
  nodesCount,
  edgesCount,
  versions,
  validationItems,
  activeTab,
  onTabChange,
  onNodeChange,
  onRestoreVersion,
  onSelectNode,
  onSaveVersion,
  selectedVersionId,
  onSelectVersion,
  nodes = [],
  availablePrompts = [],
}: Props) {
  const errCount = validationItems.filter(i => i.type === 'error').length
  const warnCount = validationItems.filter(i => i.type === 'warning').length

  return (
    <div className="w-72 bg-white border-l border-[#E5E7EB] flex flex-col flex-shrink-0 z-10">
      {/* Tab bar */}
      <div className="flex border-b border-[#E5E7EB] px-2 pt-2 flex-shrink-0">
        {[
          { id: 'props' as const, label: 'Properties' },
          { id: 'versions' as const, label: 'History' },
          { id: 'validation' as const, label: 'Validate', badge: errCount > 0 ? errCount : warnCount > 0 ? warnCount : 0, badgeColor: errCount > 0 ? '#EF4444' : '#F59E0B' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => onTabChange(tab.id)}
            className={`flex-1 relative flex items-center justify-center gap-1.5 px-2 py-2.5 text-xs font-medium border-b-2 -mb-px transition-colors ${activeTab === tab.id ? 'border-[#2563EB] text-[#2563EB]' : 'border-transparent text-[#6B7280] hover:text-[#374151]'}`}
          >
            {tab.label}
            {tab.badge && tab.badge > 0 && (
              <span className="w-4 h-4 rounded-full text-white text-[9px] font-bold flex items-center justify-center"
                style={{ backgroundColor: tab.badgeColor }}>
                {tab.badge}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === 'props' && (
          selectedNode ? (
              <NodePropsContent node={selectedNode} onNodeChange={onNodeChange} availablePrompts={availablePrompts} />
            ) : (
              <div className="space-y-4">
                <div className="p-3 rounded-xl border border-[#E5E7EB] bg-[#F9FAFB]">
                  <div className="flex items-center gap-2 mb-1">
                    <Layers className="w-4 h-4 text-[#2563EB]" />
                    <p className="text-[#1F2937] font-semibold text-sm">{flowName || 'IVR Flow'}</p>
                  </div>
                  <p className="text-[#9CA3AF] text-xs">Active Flow Overview</p>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2.5 rounded-lg border border-[#F3F4F6] bg-white">
                    <div className="text-sm font-bold text-[#2563EB]">{nodesCount}</div>
                    <div className="text-[#9CA3AF] text-[10px]">Total Nodes</div>
                  </div>
                  <div className="p-2.5 rounded-lg border border-[#F3F4F6] bg-white">
                    <div className="text-sm font-bold text-[#8B5CF6]">{edgesCount}</div>
                    <div className="text-[#9CA3AF] text-[10px]">Connections</div>
                  </div>
                </div>

                <div className="flex flex-col items-center justify-center py-8 text-center border-t border-[#F3F4F6]">
                  <p className="text-[#374151] font-medium text-xs">No node selected</p>
                  <p className="text-[#9CA3AF] text-[11px] mt-1">Click a node on the canvas to inspect and edit its properties.</p>
                </div>
              </div>
            )
        )}
        {activeTab === 'versions' && (
          <VersionsContent
            versions={versions}
            onRestoreVersion={onRestoreVersion}
            onSaveVersion={onSaveVersion}
            selectedVersionId={selectedVersionId}
            onSelectVersion={onSelectVersion}
            currentNodes={nodes}
          />
        )}
        {activeTab === 'validation' && (
          <ValidationContent items={validationItems} onSelectNode={onSelectNode} />
        )}
      </div>
    </div>
  )
}
