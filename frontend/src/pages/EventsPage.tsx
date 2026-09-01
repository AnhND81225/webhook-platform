import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, dashboardApi, type EventListItem } from '../api/dashboard-api'
import { EmptyState, ErrorState, LoadingState, NotFoundState } from '../components/states/States'
import { compactDateTime, fromDateTimeLocalValue, shortId, toDateTimeLocalValue } from '../lib/format'
import { Dialog } from '../components/ui/Dialog'
import { Button } from '../components/ui/Button'

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
  const [testOpen, setTestOpen] = useState(false)

  // Test-event responses are application-scoped; never carry one across a switch.
  useLayoutEffect(() => {
    loadMoreController.current?.abort()
    requestId.current += 1
    setLoading(true)
    setMoreLoading(false)
    setError(null)
    setItems([])
    setCursor(null)
    setTestOpen(false)
  }, [identity])

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
  return <section className="page-stack"><div className="page-heading"><div><p className="eyebrow">Observability</p><h1>Events</h1><p>Immutable producer events for this application.</p></div><Button onClick={()=>setTestOpen(true)}>Send test event</Button></div><form className="filter-toolbar" onSubmit={(event) => { event.preventDefault(); apply() }}><label>Event type<input value={draft.eventType} onChange={(event) => setDraft({ ...draft, eventType: event.target.value })} /></label><label>Source event ID<input value={draft.sourceEventId} onChange={(event) => setDraft({ ...draft, sourceEventId: event.target.value })} /></label><label>From<input type="datetime-local" value={toDateTimeLocalValue(draft.createdFrom)} onChange={(event) => setDraft({ ...draft, createdFrom: event.target.value })} /></label><label>To<input type="datetime-local" value={toDateTimeLocalValue(draft.createdTo)} onChange={(event) => setDraft({ ...draft, createdTo: event.target.value })} /></label><Button type="submit">Apply filters</Button></form>{items.length === 0 ? <EmptyState title={search.size ? 'No events match these filters' : 'No events yet'} detail="Send a simulated test event or wait for your producer to send an event." /> : <div className="table-scroll"><table><thead><tr><th>Event</th><th>Source event ID</th><th>Deliveries</th><th>Delivered</th><th>Failed</th><th>Retrying</th><th>Created</th></tr></thead><tbody>{items.map((event) => <tr key={event.id}><td><Link to={`/app/${applicationId}/events/${event.id}`}><strong className="mono">{event.eventType}</strong><small className="mono">{shortId(event.id)}</small></Link></td><td className="mono">{event.sourceEventId}</td><td>{event.deliveryCount}</td><td>{event.deliveredCount}</td><td>{event.failedCount}</td><td>{event.retryScheduledCount}</td><td>{compactDateTime(event.createdAt)}</td></tr>)}</tbody></table></div>}{cursor && <Button variant="secondary" disabled={moreLoading} onClick={loadMore}>{moreLoading ? 'Loading…' : 'Load more'}</Button>}{testOpen&&<TestEventDialog applicationId={applicationId} onDismiss={()=>setTestOpen(false)}/>}</section>
}

function TestEventDialog({applicationId,onDismiss}:{applicationId:string;onDismiss:()=>void}) { const navigate=useNavigate(); const guard=useRef(false); const [eventType,setEventType]=useState('ai.solution.completed'); const [sourceEventId,setSourceEventId]=useState(`test-${Date.now()}`); const [payload,setPayload]=useState('{\n  "status": "completed"\n}'); const [error,setError]=useState<string|null>(null); const [eventId,setEventId]=useState<string|null>(null); const [busy,setBusy]=useState(false); async function submit(event:React.FormEvent){event.preventDefault();if(guard.current)return;let parsed:unknown;try{parsed=JSON.parse(payload);if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')throw new Error()}catch{setError('Payload must be a valid JSON object.');return}if(!/^[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+$/.test(eventType.trim())||!sourceEventId.trim()){setError('Enter a valid event type and source event ID.');return}guard.current=true;setBusy(true);setError(null);try{const created=await dashboardApi.sendTestEvent(applicationId,{eventType:eventType.trim(),sourceEventId:sourceEventId.trim(),payload:parsed});setEventId(created.id)}catch{setError('We could not send the test event. Try again.')}finally{guard.current=false;setBusy(false)}} return <Dialog title="Send test event" onDismiss={busy?()=>undefined:onDismiss}><form className="application-form" onSubmit={e=>void submit(e)} noValidate>{eventId?<><p className="settings-success" role="status">Test event created.</p><div className="application-form-actions"><Button variant="secondary" onClick={onDismiss}>Done</Button><Button onClick={()=>navigate(`/app/${applicationId}/events/${eventId}`)}>View event</Button></div></>:<><p>This simulates a producer event using your dashboard session.</p><label className="form-field">Event type<input value={eventType} disabled={busy} aria-invalid={Boolean(error)} onChange={e=>{setEventType(e.target.value);setError(null)}}/></label><label className="form-field">Source event ID<input value={sourceEventId} disabled={busy} aria-invalid={Boolean(error)} onChange={e=>{setSourceEventId(e.target.value);setError(null)}}/></label><label className="form-field">JSON payload<textarea value={payload} disabled={busy} aria-invalid={Boolean(error)} onChange={e=>{setPayload(e.target.value);setError(null)}} rows={8}/></label>{error&&<p className="form-error" role="alert">{error}</p>}<div className="application-form-actions"><Button variant="secondary" disabled={busy} onClick={onDismiss}>Cancel</Button><Button type="submit" disabled={busy}>{busy?'Sending…':'Send test event'}</Button></div></>}</form></Dialog> }
