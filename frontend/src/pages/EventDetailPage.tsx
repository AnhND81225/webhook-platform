import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { dashboardApi } from '../api/dashboard-api'
import { dateTime } from '../lib/format'
import { useResource } from '../lib/useResource'
import { ErrorState, LoadingState, NotFoundState } from '../components/states/States'

export function EventDetailPage() {
  const { applicationId = '', eventId = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => dashboardApi.event(applicationId, eventId, signal), [applicationId, eventId])
  const state = useResource(`event:${applicationId}:${eventId}`, load)
  if (state.loading) return <LoadingState label="Loading event" />
  if (state.error && 'status' in state.error && state.error.status === 404) return <NotFoundState title="Event not found"><Link className="text-link" to={`/app/${applicationId}/events`}>Back to events</Link></NotFoundState>
  if (state.error || !state.data) return <ErrorState message={state.error?.message ?? 'Request failed'} onRetry={state.reload} />
  const event = state.data
  return <section className="page-stack"><div><Link className="text-link" to={`/app/${applicationId}/events`}>Events</Link><h1>Event detail</h1></div><section className="panel detail-grid"><Info label="Event ID" value={event.id} mono /><Info label="Source event ID" value={event.sourceEventId} mono /><Info label="Event type" value={event.eventType} mono /><Info label="Created" value={dateTime(event.createdAt)} /><Info label="Deliveries" value={String(event.deliveryCount)} /><Info label="Delivered / failed / retrying" value={`${event.deliveredCount} / ${event.failedCount} / ${event.retryScheduledCount}`} /></section><section className="panel json-panel"><h2>Payload</h2><pre>{JSON.stringify(event.payload, null, 2)}</pre></section></section>
}
export function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) { return <div><span>{label}</span><strong className={mono ? 'mono technical-value' : ''}>{value}</strong></div> }
