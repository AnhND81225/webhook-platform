const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL

export const apiBaseUrl = (configuredBaseUrl ?? 'http://localhost:8080').replace(/\/$/, '')

export function apiUrl(path: string): string {
  return `${apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

