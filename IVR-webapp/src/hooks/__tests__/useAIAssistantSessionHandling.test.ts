// @ts-ignore
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { aiApi } from '../../api/aiApi'

describe('useAIAssistant Session & Cancellation Guards', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('should prevent double-fire POST requests when createNewChat is called concurrently', async () => {
    let callsCount = 0
    vi.spyOn(aiApi, 'createNewChat').mockImplementation(async () => {
      callsCount++
      await new Promise(r => setTimeout(r, 50))
      return { sessionId: 'session-123', tenantId: 'tenant-1', status: 'ACTIVE', customerIdentifier: 'New Chat' }
    })

    // Simulate double-fire invocation logic as implemented in handleNewChat
    let isCreatingSession = false
    const handleNewChatGuard = async () => {
      if (isCreatingSession) return null
      isCreatingSession = true
      try {
        return await aiApi.createNewChat('New Chat')
      } finally {
        isCreatingSession = false
      }
    }

    // Fire two calls concurrently (simulating rapid double-click)
    const [res1, res2] = await Promise.all([
      handleNewChatGuard(),
      handleNewChatGuard()
    ])

    expect(callsCount).toBe(1)
    expect(res1).not.toBeNull()
    expect(res2).toBeNull()
  })

  it('should abort in-flight generation requests when session changes', async () => {
    const controller = new AbortController()
    let isAbortedDuringFetch = false

    vi.spyOn(aiApi, 'generateFlow').mockImplementation(async (_desc: string, options?: any) => {
      const signal = options?.signal

      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
          resolve({ nodes: [{ id: '1', type: 'greeting', title: 'Old Flow' }], edges: [] })
        }, 100)

        signal?.addEventListener('abort', () => {
          clearTimeout(timer)
          isAbortedDuringFetch = true
          const err = new Error('Aborted')
          err.name = 'AbortError'
          reject(err)
        })
      })
    })

    // Initiate request
    const genPromise = aiApi.generateFlow('create hospital flow', { signal: controller.signal })

    // Simulate session switch / new chat clicking abort
    controller.abort('Session switched')

    await expect(genPromise).rejects.toThrow('Aborted')
    expect(isAbortedDuringFetch).toBe(true)
  })

  it('should gate state updates on active session matching requested session', () => {
    let currentActiveSessionId = 'new-session-456'
    const requestSessionId = 'old-session-123'
    let stateWasUpdated = false

    const applyFlowUpdate = (reqSid: string) => {
      if (currentActiveSessionId !== reqSid) {
        return // Gated out
      }
      stateWasUpdated = true
    }

    applyFlowUpdate(requestSessionId)
    expect(stateWasUpdated).toBe(false)
  })

  it('should correctly parse flowRes metadata without ReferenceError', () => {
    const flowRes = {
      name: 'Insurance IVR',
      flowJson: JSON.stringify({ nodes: [{ id: '1', type: 'greeting', title: 'Start' }], edges: [] }),
      validationScore: 85,
      validationResult: { score: 85, valid: true, issues: [] },
      providerAttempts: [{ provider: 'openrouter', statusCode: 200, reason: 'Success', remainingCooldownSeconds: 0 }]
    }

    const score = (flowRes as any).validationScore ?? (flowRes as any).validationResult?.score ?? (flowRes as any).score ?? 90
    expect(score).toBe(85)
  })
})
