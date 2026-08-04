import { type GenerationStage } from '../hooks/useAIAssistant'
import { Loader2, CheckCircle } from 'lucide-react'

export function GenerationStepper({ stage, onCancel }: { stage: GenerationStage; onCancel?: () => void }) {
  const stages: { key: GenerationStage; label: string }[] = [
    { key: 'understanding', label: 'Understanding request' },
    { key: 'analysis', label: 'Business analysis' },
    { key: 'planning', label: 'Planning flow' },
    { key: 'template', label: 'Selecting template/domain' },
    { key: 'generating', label: 'Generating VXML' },
    { key: 'validating', label: 'Validating' },
    { key: 'converting', label: 'Converting to nodes' },
    { key: 'rendering', label: 'Rendering canvas' },
  ]

  if (stage === 'idle') return null

  const activeIndex = stages.findIndex(s => s.key === stage)

  return (
    <div className="px-3.5 py-3 rounded-2xl border border-[#DBEAFE] bg-gradient-to-r from-[#EFF6FF] via-[#F5F3FF] to-[#EFF6FF] shadow-sm select-none">
      <div className="flex items-center justify-between mb-2.5">
        <p className="text-[10px] font-bold text-[#2563EB] uppercase tracking-wider flex items-center gap-1.5">
          <Loader2 className="w-3.5 h-3.5 animate-spin text-[#2563EB]" />
          Generating Flow Pipeline
        </p>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="px-2 py-0.5 rounded text-[10px] font-semibold bg-white border border-[#FCA5A5] text-[#DC2626] hover:bg-[#FEF2F2] hover:border-[#EF4444] transition-colors"
          >
            Cancel
          </button>
        )}
      </div>

      <div className="space-y-1.5">
        {stages.map((s, i) => {
          const isDone = i < activeIndex
          const isActive = i === activeIndex
          return (
            <div key={s.key} className="flex items-center gap-2">
              <div className={`w-4 h-4 rounded-full flex items-center justify-center flex-shrink-0 transition-all duration-200 ${
                isDone ? 'bg-[#22C55E]' : isActive ? 'bg-[#2563EB] animate-pulse ring-2 ring-[#93C5FD]' : 'bg-[#E5E7EB]'
              }`}>
                {isDone ? <CheckCircle className="w-2.5 h-2.5 text-white" /> :
                 isActive ? <Loader2 className="w-2.5 h-2.5 text-white animate-spin" /> :
                 <div className="w-1.5 h-1.5 rounded-full bg-white" />}
              </div>
              <span className={`text-xs transition-colors duration-200 ${
                isDone ? 'text-[#15803D] font-medium' : isActive ? 'text-[#2563EB] font-bold' : 'text-[#9CA3AF]'
              }`}>
                {s.label}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
