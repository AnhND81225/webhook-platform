export function shortId(value: string): string { return `${value.slice(0, 8)}…` }

export function dateTime(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
}

export function compactDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

export function errorDescription(code: string | null): string | null {
  const descriptions: Record<string, string> = {
    HTTP_ERROR: 'HTTP response error', DNS_ERROR: 'DNS resolution failed', CONNECTION_ERROR: 'Connection failed',
    TLS_ERROR: 'TLS handshake/certificate failure', TIMEOUT: 'Request timed out', SSRF_REJECTED: 'Target rejected by SSRF policy',
    SIGNING_ERROR: 'Signing configuration error',
  }
  return code ? (descriptions[code] ?? null) : null
}

export function toDateTimeLocalValue(iso?: string): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

export function fromDateTimeLocalValue(value?: string): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}
