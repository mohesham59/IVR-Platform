import { useState } from 'react'
import { ChevronDown, Save, Clock, RotateCcw, CheckCircle, Archive, GitBranch, AlertTriangle, XCircle, Info } from 'lucide-react'
import type { FlowNode, FlowVersion } from './types'
import { NODE_DEFS, NODE_ICONS } from './nodeConfig'

interface ValidationItem {
  type: 'error' | 'warning' | 'info'
  message: string
  nodeId?: string
}

interface Props {
  selectedNode: FlowNode | null
  versions: FlowVersion[]
  validationItems: ValidationItem[]
  activeTab: 'props' | 'versions' | 'validation'
  onTabChange: (t: 'props' | 'versions' | 'validation') => void
}

function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="block text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">{label}</label>
      {children}
    </div>
  )
}

function TextInput({ value, placeholder }: { value?: string; placeholder?: string }) {
  return (
    <input defaultValue={value} placeholder={placeholder}
      className="w-full h-8 px-2.5 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/10 transition-all" />
  )
}

function SelectInput({ options, value }: { options: string[]; value?: string }) {
  return (
    <div className="relative">
      <select defaultValue={value}
        className="w-full h-8 pl-2.5 pr-7 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] appearance-none cursor-pointer">
        {options.map(o => <option key={o}>{o}</option>)}
      </select>
      <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9CA3AF] pointer-events-none" />
    </div>
  )
}

function Toggle({ label, defaultChecked }: { label: string; defaultChecked?: boolean }) {
  const [on, setOn] = useState(defaultChecked ?? false)
  return (
    <div className="flex items-center justify-between">
      <span className="text-[#374151] text-xs">{label}</span>
      <button onClick={() => setOn(!on)}
        className={`w-8 h-4.5 rounded-full transition-colors relative flex-shrink-0 ${on ? 'bg-[#2563EB]' : 'bg-[#E5E7EB]'}`}
        style={{ height: '18px', width: '32px' }}>
        <span className={`absolute top-0.5 w-3.5 h-3.5 rounded-full bg-white shadow transition-all ${on ? 'left-3.5' : 'left-0.5'}`} />
      </button>
    </div>
  )
}

function NodePropsContent({ node }: { node: FlowNode }) {
  const def = NODE_DEFS[node.type]

  return (
    <div className="space-y-4">
      {/* Node identity */}
      <div className="flex items-center gap-3 p-3 rounded-xl border border-[#E5E7EB] bg-[#F9FAFB]">
        <div className="w-9 h-9 rounded-xl flex items-center justify-center text-base flex-shrink-0" style={{ backgroundColor: def.iconBg }}>
          {NODE_ICONS[node.type]}
        </div>
        <div>
          <p className="text-[#1F2937] font-semibold text-sm">{node.title}</p>
          <span className="inline-flex px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wide mt-0.5"
            style={{ backgroundColor: def.iconBg, color: def.color }}>{def.label}</span>
        </div>
      </div>

      {/* Basic settings */}
      <section className="space-y-3">
        <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Node Settings</h4>

        <FieldRow label="Node Name">
          <TextInput value={node.title} />
        </FieldRow>
        <FieldRow label="Description">
          <TextInput value={node.subtitle} />
        </FieldRow>

        {/* Type-specific fields */}
        {(node.type === 'greeting' || node.type === 'playback') && (
          <FieldRow label="Audio File">
            <SelectInput options={['welcome_hospital.wav', 'afterhours_msg.wav', 'hold_music.wav', 'beep.wav']} value="welcome_hospital.wav" />
          </FieldRow>
        )}
        {node.type === 'tts' && (
          <>
            <FieldRow label="Text / Template">
              <textarea className="w-full px-2.5 py-2 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] resize-none" rows={3}
                defaultValue="Thank you for calling {{hospital.name}}. Please hold." />
            </FieldRow>
            <FieldRow label="Voice">
              <SelectInput options={['Polly.Joanna (en-US)', 'Polly.Matthew (en-US)', 'Polly.Amy (en-GB)']} />
            </FieldRow>
          </>
        )}
        {node.type === 'dtmf_menu' && (
          <>
            <FieldRow label="Prompt File">
              <SelectInput options={['menu_main.wav', 'menu_billing.wav', 'menu_support.wav']} />
            </FieldRow>
            <FieldRow label="Max Retries">
              <SelectInput options={['1', '2', '3', '5']} value="3" />
            </FieldRow>
            <FieldRow label="Timeout (sec)">
              <TextInput value="5" />
            </FieldRow>
            <div className="space-y-2">
              <label className="block text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Key Mappings</label>
              {[['1', 'Appointments'], ['2', 'Emergency'], ['3', 'Billing'], ['0', 'Agent']].map(([k, v]) => (
                <div key={k} className="flex items-center gap-2">
                  <span className="w-7 h-7 rounded-lg bg-[#EDE9FE] text-[#6D28D9] text-xs font-bold flex items-center justify-center flex-shrink-0">{k}</span>
                  <input defaultValue={v} className="flex-1 h-7 px-2 rounded-lg border border-[#E5E7EB] text-xs text-[#1F2937] outline-none focus:border-[#2563EB] transition-all" />
                </div>
              ))}
            </div>
          </>
        )}
        {node.type === 'queue' && (
          <>
            <FieldRow label="Queue">
              <SelectInput options={['appt-queue', 'billing-queue', 'support-queue', 'vip-queue']} value="appt-queue" />
            </FieldRow>
            <FieldRow label="Max Wait Time (sec)">
              <TextInput value="300" />
            </FieldRow>
            <FieldRow label="Music on Hold">
              <SelectInput options={['default-moh', 'jazz-moh', 'classical-moh']} />
            </FieldRow>
            <Toggle label="Announce Position" defaultChecked />
            <Toggle label="Announce Wait Time" defaultChecked />
          </>
        )}
        {node.type === 'api' && (
          <>
            <FieldRow label="Method">
              <SelectInput options={['GET', 'POST', 'PUT', 'DELETE']} value="GET" />
            </FieldRow>
            <FieldRow label="URL">
              <TextInput value="https://api.meridian.io/check-patient" />
            </FieldRow>
            <FieldRow label="Auth Type">
              <SelectInput options={['Bearer Token', 'API Key', 'Basic Auth', 'None']} />
            </FieldRow>
            <Toggle label="Include Call Variables" defaultChecked />
            <FieldRow label="Timeout (sec)">
              <TextInput value="10" />
            </FieldRow>
          </>
        )}
        {node.type === 'ai' && (
          <>
            <FieldRow label="AI Model">
              <SelectInput options={['GPT-4o', 'GPT-4 Turbo', 'Claude 3.5 Sonnet', 'Gemini Pro']} />
            </FieldRow>
            <FieldRow label="System Prompt">
              <textarea className="w-full px-2.5 py-2 rounded-lg border border-[#E5E7EB] bg-white text-xs text-[#1F2937] outline-none focus:border-[#2563EB] resize-none" rows={3}
                defaultValue="You are a hospital support assistant. Be empathetic and helpful." />
            </FieldRow>
            <FieldRow label="Max Turns">
              <TextInput value="5" />
            </FieldRow>
            <Toggle label="Sentiment Analysis" defaultChecked />
            <Toggle label="Auto-Escalate on Frustration" defaultChecked />
          </>
        )}
        {node.type === 'hours' && (
          <div className="space-y-2">
            <label className="block text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Schedule</label>
            {[
              { day: 'Mon–Fri', open: '08:00', close: '18:00', active: true },
              { day: 'Saturday', open: '09:00', close: '14:00', active: true },
              { day: 'Sunday', open: '—', close: '—', active: false },
            ].map(row => (
              <div key={row.day} className="flex items-center gap-2">
                <span className="text-[#374151] text-xs w-16 flex-shrink-0">{row.day}</span>
                {row.active
                  ? <><TextInput value={row.open} /><span className="text-[#9CA3AF] text-xs">–</span><TextInput value={row.close} /></>
                  : <span className="text-[#9CA3AF] text-xs italic">Closed</span>}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Advanced */}
      <section className="space-y-2.5">
        <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Advanced</h4>
        <Toggle label="Log Execution" defaultChecked />
        <Toggle label="Retry on Failure" />
        <Toggle label="Node Disabled" defaultChecked={node.disabled} />
        {node.type !== 'start' && node.type !== 'end' && (
          <FieldRow label="On Error Goto">
            <SelectInput options={['End Call', 'Voicemail', 'Transfer to Agent']} />
          </FieldRow>
        )}
      </section>

      {/* Save */}
      <button className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1E40AF] transition-colors shadow-md shadow-[#2563EB]/20">
        <Save className="w-3.5 h-3.5" /> Apply Changes
      </button>
    </div>
  )
}

function VersionsContent({ versions }: { versions: FlowVersion[] }) {
  const tagConfig = {
    draft: { cls: 'bg-[#FEF9C3] text-[#A16207]', icon: <Clock className="w-3 h-3" /> },
    published: { cls: 'bg-[#DCFCE7] text-[#15803D]', icon: <CheckCircle className="w-3 h-3" /> },
    archived: { cls: 'bg-[#F3F4F6] text-[#6B7280]', icon: <Archive className="w-3 h-3" /> },
  }
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-[#9CA3AF] text-[10px] font-semibold uppercase tracking-wider">Flow Versions</h4>
        <button className="flex items-center gap-1 text-[#2563EB] text-[10px] font-medium hover:underline">
          <GitBranch className="w-3 h-3" /> New Version
        </button>
      </div>
      <div className="space-y-2">
        {versions.map((v, i) => {
          const tc = tagConfig[v.tag]
          return (
            <div key={v.id} className={`p-3 rounded-xl border transition-all ${i === 0 ? 'border-[#2563EB] bg-[#EFF6FF]' : 'border-[#E5E7EB] bg-white hover:border-[#D1D5DB]'}`}>
              <div className="flex items-start justify-between gap-2">
                <div className="flex-1 min-w-0">
                  <p className={`text-xs font-semibold ${i === 0 ? 'text-[#2563EB]' : 'text-[#1F2937]'}`}>{v.label}</p>
                  <p className="text-[#9CA3AF] text-[10px] mt-0.5">{v.savedAt} · {v.author}</p>
                </div>
                <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-semibold flex-shrink-0 ${tc.cls}`}>
                  {tc.icon}{v.tag}
                </span>
              </div>
              {i !== 0 && (
                <button className="mt-2 flex items-center gap-1 text-[#6B7280] text-[10px] hover:text-[#2563EB] transition-colors">
                  <RotateCcw className="w-3 h-3" /> Restore this version
                </button>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function ValidationContent({ items }: { items: ValidationItem[] }) {
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
              <div key={i} className="flex items-start gap-2 p-2.5 rounded-lg border border-[#F3F4F6] hover:border-[#E5E7EB] bg-white transition-colors">
                <span className="flex-shrink-0 mt-0.5">{iconMap[item.type]}</span>
                <p className="text-[#374151] text-xs">{item.message}</p>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

export default function PropertiesPanel({ selectedNode, versions, validationItems, activeTab, onTabChange }: Props) {
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
          selectedNode
            ? <NodePropsContent node={selectedNode} />
            : (
              <div className="flex flex-col items-center justify-center h-full py-16 text-center">
                <div className="w-12 h-12 rounded-2xl bg-[#F3F4F6] flex items-center justify-center mb-4">
                  <span className="text-2xl">☰</span>
                </div>
                <p className="text-[#374151] font-medium text-sm">No node selected</p>
                <p className="text-[#9CA3AF] text-xs mt-1">Click a node to view and edit its properties</p>
              </div>
            )
        )}
        {activeTab === 'versions' && <VersionsContent versions={versions} />}
        {activeTab === 'validation' && <ValidationContent items={validationItems} />}
      </div>
    </div>
  )
}
