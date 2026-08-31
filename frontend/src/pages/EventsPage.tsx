import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, dashboardApi, type EventListItem } from '../api/dashboard-api'
import { EmptyState, ErrorState, LoadingState, NotFoundState } from '../components/states/States'
import { compactDateTime, fromDateTimeLocalValue, shortId, toDateTimeLocalValue } from '../lib/format'

const filterKeys = ['eventType', 'sourceEventId', 'createdFrom', 'createdTo'] as const
type FilterKey = typeof filterKeys[number]

export function EventsPage() {
  const { applicationId = '' } = useParams()
  const [search, setSearch] = useSearchParams()
  const filters = Object.fromEntries(filterKeys.map((key) => [key, search.get(key) ?? ''])) as Record<FilterKey, string>
  const identity = `${applicationId}:${search.toString()}`
  const [draft, setDraft] = useState(filters)
  const [items, setItems] = useState<EventListItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [moreLoading, setMoreLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [retryVersion, setRetryVersion] = useState(0)
  const requestId = useRef(0)
  const loadMoreController = useRef<AbortController | null>(null)

  useEffect(() => setDraft(filters), [identity])
  useEffect(() => {
    const controller = new AbortController(); const id = ++requestId.current
    loadMoreController.current?.abort(); setLoading(true); setError(null); setItems([]); setCursor(null)
    void dashboardApi.events(applicationId, { ...filters, size: 25 }, controller.signal).then((page) => {
      if (id === requestId.current) { setItems(page.items); setCursor(page.nextCursor) }
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted && id === requestId.current) setError(reason instanceof Error ? reason : new Error('Request failed'))
    }).finally(() => { if (id === requestId.current) setLoading(false) })
    return () => controller.abort()
  }, [applicationId, identity, retryVersion])

  const loadMore = useCallback(() => {
    if (!cursor || moreLoading) return
    const controller = new AbortController(); loadMoreController.current = controller; const id = ++requestId.current; setMoreLoading(true); setError(null)
    void dashboardApi.events(applicationId, { ...filters, cursor, size: 25 }, controller.signal).then((page) => {
      if (id === requestId.current) { setItems((previous) => [...previous, ...page.items.filter((item) => !previous.some((old) => old.id === item.id))]); setCursor(page.nextCursor) }
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted && id === requestId.current) setError(reason instanceof Error ? reason : new Error('Request failed'))
    }).finally(() => { if (id === requestId.current) setMoreLoading(false) })
  }, [applicationId, cursor, filters, moreLoading])

  function apply() {
    const next = new URLSearchParams(); if (draft.eventType) next.set('eventType', draft.eventType); if (draft.sourceEventId) next.set('sourceEventId', draft.sourceEventId)
    const from = fromDateTimeLocalValue(draft.createdFrom); const to = fromDateTimeLocalValue(draft.createdTo); if (from) next.set('createdFrom', from); if (to) next.set('createdTo', to)
    setSearch(next, { replace: true })
  }
  if (loading) return <LoadingState label="Loading events" />
  if (error instanceof ApiError && error.status === 404) return <NotFoundState title="Application not found" />
  if (error) return <ErrorState message={error.message} onRetry={() => setRetryVersion((version) => version + 1)} />
  return <section className="page-stack"><div><p className="eyebrow">Observability</p><h1>Events</h1><p>Immutable producer events for this application.</p></div><form className="filter-toolbar" onSubmit={(event) => { event.preventDefault(); apply() }}><label>Event type<input value={draft.eventType} onChange={(event) => setDraft({ ...draft, eventType: event.target.value })} /></label><label>Source event ID<input value={draft.sourceEventId} onChange={(event) => setDraft({ ...draft, sourceEventId: event.target.value })} /></label><label>From<input type="datetime-local" value={toDateTimeLocalValue(draft.createdFrom)} onChange={(event) => setDraft({ ...draft, createdFrom: event.target.value })} /></label><label>To<input type="datetime-local" value={toDateTimeLocalValue(draft.createdTo)} onChange={(event) => setDraft({ ...draft, createdTo: event.target.value })} /></label><button type="submit">Apply filters</button></form>{items.length === 0 ? <EmptyState title={search.size ? 'No events match these filters' : 'No events yet'} detail="Events received for this application appear here." /> : <div className="table-scroll"><table><thead><tr><th>Event</th><th>Source event ID</th><th>Deliveries</th><th>Delivered</th><th>Failed</th><th>Retrying</th><th>Created</th></tr></thead><tbody>{items.map((event) => <tr key={event.id}><td><Link to={`/app/${applicationId}/events/${event.id}`}><strong className="mono">{event.eventType}</strong><small className="mono">{shortId(event.id)}</small></Link></td><td className="mono">{event.sourceEventId}</td><td>{event.deliveryCount}</td><td>{event.deliveredCount}</td><td>{event.failedCount}</td><td>{event.retryScheduledCount}</td><td>{compactDateTime(event.createdAt)}</td></tr>)}</tbody></table></div>}{cursor && <button type="button" className="secondary-button" disabled={moreLoading} onClick={loadMore}>{moreLoading ? 'Loading…' : 'Load more'}</button>}</section>
}
