const API_CONTEXT: string = (import.meta.env.VITE_API_CONTEXT as string | undefined) || '/nexusivr-ai-engine'

/**
 * Returns the absolute backend URL for a given API path (e.g. '/api/v1/auth/me').
 *
 * Uses the origin of the current page (works in dev via the Vite proxy on
 * `/api` and in production behind a reverse proxy) and can be overridden
 * with `VITE_API_CONTEXT` when the backend is served from a different context path.
 */
export function backendUrl(path: string): string {
  const cleanPath = path.startsWith('/') ? path : `/${path}`
  return `${API_CONTEXT}${cleanPath}`
}

/**
 * Fetch wrapper that automatically retries 502 Bad Gateway / 503 Service Unavailable
 * and network errors with exponential backoff before throwing.
 */
export async function fetchWithRetry(
  input: RequestInfo | URL,
  init?: RequestInit,
  maxRetries = 3,
  initialBackoffMs = 1000
): Promise<Response> {
  let attempt = 0
  let backoffMs = initialBackoffMs

  while (true) {
    try {
      const response = await fetch(input, init)
      if ((response.status === 502 || response.status === 503) && attempt < maxRetries) {
        attempt++
        console.warn(`[fetchWithRetry] Received HTTP ${response.status} from ${input.toString()}. Retrying in ${backoffMs}ms (attempt ${attempt}/${maxRetries})...`)
        await new Promise((resolve) => setTimeout(resolve, backoffMs))
        backoffMs *= 2
        continue
      }
      return response
    } catch (err: any) {
      if (attempt < maxRetries) {
        attempt++
        console.warn(`[fetchWithRetry] Network error connecting to ${input.toString()}. Retrying in ${backoffMs}ms (attempt ${attempt}/${maxRetries})...`, err)
        await new Promise((resolve) => setTimeout(resolve, backoffMs))
        backoffMs *= 2
        continue
      }
      throw err
    }
  }
}
