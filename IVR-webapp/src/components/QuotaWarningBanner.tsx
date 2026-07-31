import { useState, useEffect } from 'react'
import { AlertTriangle, X } from 'lucide-react'

export interface QuotaWarning {
  provider: string
  model?: string
  attempt: number
}

const DEDUP_WINDOW_MS = 5 * 60 * 1000

function providerLabel(provider: string): string {
  const map: Record<string, string> = {
    gemini: 'Gemini',
    groq: 'Groq',
    ollama: 'Ollama',
  }
  return map[provider.toLowerCase()] || provider
}

export default function QuotaWarningBanner({
  warnings,
}: {
  warnings: QuotaWarning[]
}) {
  const [visible, setVisible] = useState(false)
  const [dismissedKeys, setDismissedKeys] = useState<Map<string, number>>(new Map())

  useEffect(() => {
    if (!warnings || warnings.length === 0) {
      setVisible(false)
      return
    }

    const now = Date.now()
    const hasNew = warnings.some(w => {
      const key = `${w.provider}-${w.attempt}`
      const lastDismissed = dismissedKeys.get(key)
      return !lastDismissed || now - lastDismissed > DEDUP_WINDOW_MS
    })

    if (hasNew) {
      setVisible(true)
    }
  }, [warnings])

  if (!visible || !warnings || warnings.length === 0) return null

  const allFailed = warnings.length >= 2 &&
    new Set(warnings.map(w => w.provider)).size >= 2

  const handleDismiss = () => {
    const now = Date.now()
    const newDismissed = new Map(dismissedKeys)
    warnings.forEach(w => {
      newDismissed.set(`${w.provider}-${w.attempt}`, now)
    })
    setDismissedKeys(newDismissed)
    setVisible(false)
  }

  return (
    <div className="flex items-start gap-3 px-4 py-3 rounded-xl border border-[#FDE68A] bg-[#FFFBEB] text-[#92400E] shadow-sm">
      <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
      <div className="flex-1 text-xs font-medium leading-relaxed">
        {allFailed ? (
          <>
            <strong>Both AI providers are currently rate-limited.</strong> Please try again shortly.
          </>
        ) : (
          <>
            <strong>{providerLabel(warnings[0].provider)}</strong> is rate-limited right now
            {warnings.length > 1 ? ` (attempt ${warnings[0].attempt})` : ''}
            {warnings[0].provider === 'gemini' && warnings.some(w => w.provider === 'groq')
              ? ' — using backup provider.'
              : '. Please try again shortly.'}
          </>
        )}
      </div>
      <button
        onClick={handleDismiss}
        className="flex-shrink-0 p-0.5 rounded hover:bg-[#FDE68A] transition-colors"
        title="Dismiss"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  )
}
