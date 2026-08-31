import { useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { dashboardApi } from '../api/dashboard-api'
import { ErrorState, LoadingState, NotFoundState } from '../components/states/States'
import { useResource } from '../lib/useResource'

export function OverviewPage() {
  const { applicationId = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => dashboardApi.summary(applicationId, signal), [applicationId])
  const state = useResource(`summary:${applicationId}`, load)
  if (state.loading) return <LoadingState label="Loading overview" />
  if (state.error?.name === 'ApiError' && 'status' in state.error && state.error.status === 404) return <NotFoundState title="Application not found" />
  if (state.error || !state.data) return <ErrorState message={state.error?.message ?? 'Request failed'} onRetry={state.reload} />
  const { events, deliveries, recentFailures } = state.data
  const metrics = [['Total events', events.total], ['Events last 24h', events.last24Hours], ['Pending', deliveries.pending], ['Processing', deliveries.processing], ['Delivered', deliveries.delivered], ['Retry scheduled', deliveries.retryScheduled], ['Failed', deliveries.failed], ['Recent failures', recentFailures]]
  return <section className="page-stack"><div><p className="eyebrow">Observability</p><h1>Overview</h1><p>Application delivery health and recent operational state.</p></div><div className="metric-grid">{metrics.map(([label, value]) => <article className="panel metric-card" key={String(label)}><span>{label}</span><strong>{value}</strong></article>)}</div></section>
}
