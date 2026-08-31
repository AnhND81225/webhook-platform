import type { AttemptStatus, DeliveryStatus } from '../../api/dashboard-api'
import { errorDescription } from '../../lib/format'

export function DeliveryStatusBadge({ status }: { status: DeliveryStatus }) { return <span className={`status-badge status-${status.toLowerCase()}`}><span aria-hidden="true" />{status.replace('_', ' ')}</span> }
export function AttemptStatusBadge({ status }: { status: AttemptStatus }) { return <span className={`status-badge attempt-${status.toLowerCase()}`}><span aria-hidden="true" />{status.replace('_', ' ')}</span> }
export function HttpStatusDisplay({ status }: { status: number | null }) { return <span className="mono">{status ? `HTTP ${status}` : '—'}</span> }
export function ErrorCodeDisplay({ code }: { code: string | null }) {
  if (!code) return <span>—</span>
  return <span className="error-code"><span className="mono">{code}</span>{errorDescription(code) && <small>{errorDescription(code)}</small>}</span>
}
