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
