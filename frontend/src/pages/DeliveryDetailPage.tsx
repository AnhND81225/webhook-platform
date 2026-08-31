import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { dashboardApi } from '../api/dashboard-api'
import { AttemptStatusBadge, DeliveryStatusBadge, ErrorCodeDisplay, HttpStatusDisplay } from '../components/status/StatusBadges'
import { ErrorState, LoadingState, NotFoundState, EmptyState } from '../components/states/States'
import { dateTime } from '../lib/format'
import { useResource } from '../lib/useResource'
import { Info } from './EventDetailPage'

export function DeliveryDetailPage() {
  const { applicationId = '', deliveryId = '' } = useParams()
  const deliveryLoad = useCallback((signal: AbortSignal) => dashboardApi.delivery(applicationId, deliveryId, signal), [applicationId, deliveryId])
  const attemptLoad = useCallback((signal: AbortSignal) => dashboardApi.attempts(applicationId, deliveryId, signal), [applicationId, deliveryId])
  const delivery = useResource(`delivery:${applicationId}:${deliveryId}`, deliveryLoad)
  const attempts = useResource(`attempts:${applicationId}:${deliveryId}`, attemptLoad)
  if (delivery.loading) return <LoadingState label="Loading delivery" />
  if (delivery.error && 'status' in delivery.error && delivery.error.status === 404) return <NotFoundState title="Delivery not found"><Link className="text-link" to={`/app/${applicationId}/deliveries`}>Back to deliveries</Link></NotFoundState>
  if (delivery.error || !delivery.data) return <ErrorState message={delivery.error?.message ?? 'Request failed'} onRetry={delivery.reload} />
  const item = delivery.data
  return <section className="page-stack"><div className="detail-heading"><div><Link className="text-link" to={`/app/${applicationId}/deliveries`}>Deliveries</Link><h1>Delivery detail</h1><p className="mono technical-value">{item.id}</p></div><DeliveryStatusBadge status={item.status} /></div><section className="panel detail-grid"><Info label="Created" value={dateTime(item.createdAt)} /><Info label="Updated" value={dateTime(item.updatedAt)} /><Info label="Next retry" value={dateTime(item.nextRetryAt)} /><Info label="Target URL" value={item.targetUrl} mono /></section><div className="two-column"><section className="panel detail-section"><h2>Event</h2><Info label="Event ID" value={item.event.id} mono /><Info label="Source event ID" value={item.event.sourceEventId} mono /><Info label="Event type" value={item.event.eventType} mono /><Info label="Created" value={dateTime(item.event.createdAt)} /></section><section className="panel detail-section"><h2>Endpoint</h2><Info label="Endpoint ID" value={item.endpoint.id} mono /><Info label="Name" value={item.endpoint.name} /><Info label="Sanitized URL" value={item.endpoint.url} mono /></section></div><section className="panel detail-section"><h2>Attempt history</h2>{attempts.loading ? <LoadingState label="Loading attempts" /> : attempts.error ? <ErrorState message={attempts.error.message} onRetry={attempts.reload} /> : !attempts.data?.length ? <EmptyState title="No attempts recorded" detail="The worker has not made an outbound attempt yet." /> : <ol className="attempt-timeline">{attempts.data.map((attempt) => <li key={attempt.id}><div className="attempt-node" aria-hidden="true" /><div className="attempt-card"><div><strong>Attempt #{attempt.attemptNumber}</strong><AttemptStatusBadge status={attempt.status} /></div>{attempt.status === 'ABANDONED' ? <p>Outcome unknown after recovery</p> : <div className="attempt-meta"><HttpStatusDisplay status={attempt.httpStatusCode} /><ErrorCodeDisplay code={attempt.errorCode} /><span>{attempt.durationMs === null ? '—' : `${attempt.durationMs} ms`}</span></div>}<small>Started {dateTime(attempt.startedAt)}{attempt.completedAt && ` · Completed ${dateTime(attempt.completedAt)}`}</small></div></li>)}</ol>}</section></section>
}
