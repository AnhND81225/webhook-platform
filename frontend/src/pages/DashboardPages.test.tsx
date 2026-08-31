import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import { afterEach, expect, it, vi } from 'vitest'
import { DeliveriesPage } from './DeliveriesPage'
import { EventDetailPage } from './EventDetailPage'
import { EventsPage } from './EventsPage'
import { DeliveryDetailPage } from './DeliveryDetailPage'
import { OverviewPage } from './OverviewPage'
import { AppIndexPage } from './AppIndexPage'
import { dashboardApi } from '../api/dashboard-api'
import { AuthProvider, useAuth } from '../auth/AuthProvider'

const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
const event = { id: '11111111-1111-1111-1111-111111111111', sourceEventId: 'source-1', eventType: 'ai.solution.completed', createdAt: '2026-08-29T05:20:14Z', deliveryCount: 2, deliveredCount: 1, failedCount: 0, retryScheduledCount: 1 }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }
function SwitchApplication() { const navigate = useNavigate(); return <button onClick={() => navigate('/app/app-b/events')}>Switch application</button> }
function UnauthorizedDashboardRequest() { const auth = useAuth(); useEffect(() => { if (auth.status === 'authenticated') void dashboardApi.summary('app-1').catch(() => undefined) }, [auth.status]); return <span>{auth.status}</span> }

afterEach(() => vi.unstubAllGlobals())

it('loads more event rows using the opaque cursor without rendering list payloads', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(json({ items: [event], nextCursor: 'opaque-next' })).mockResolvedValueOnce(json({ items: [{ ...event, id: '22222222-2222-2222-2222-222222222222', sourceEventId: 'source-2' }], nextCursor: null }))
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={['/app/app-1/events']}><Routes><Route path="/app/:applicationId/events" element={<EventsPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText('source-1')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Load more' }))
  expect(await screen.findByText('source-2')).toBeInTheDocument()
  expect(fetchMock.mock.calls[1][0]).toContain('cursor=opaque-next')
  expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
})

it('renders producer-controlled event JSON as text', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ ...event, payload: { text: '<script>alert(1)</script>' } })))
  render(<MemoryRouter initialEntries={['/app/app-1/events/event-1']}><Routes><Route path="/app/:applicationId/events/:eventId" element={<EventDetailPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument()
  expect(document.querySelector('script')).toBeNull()
})

it('shows a controlled event-not-found state for a M10 404', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'EVENT_NOT_FOUND', message: 'Event was not found.' }), { status: 404, headers: { 'Content-Type': 'application/json' } })))
  render(<MemoryRouter initialEntries={['/app/app-1/events/missing']}><Routes><Route path="/app/:applicationId/events/:eventId" element={<EventDetailPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText('Event not found')).toBeInTheDocument()
  expect(screen.queryByText('EVENT_NOT_FOUND')).not.toBeInTheDocument()
})

it('renders delivery statuses and preserves abandoned attempt semantics', async () => {
  const statuses = ['PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'DELIVERED', 'FAILED']
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json({ items: statuses.map((status, index) => ({ id: `${index}`, eventId: 'event', eventType: 'ai.solution.completed', endpointId: 'endpoint', endpointName: 'Endpoint', status, nextRetryAt: status === 'RETRY_SCHEDULED' ? '2026-08-29T05:21:14Z' : null, attemptCount: 0, lastAttempt: null, createdAt: '2026-08-29T05:20:14Z', updatedAt: '2026-08-29T05:20:14Z' })), nextCursor: null })).mockResolvedValueOnce(json({ id: 'delivery', status: 'FAILED', event: { id: 'event', sourceEventId: 'source', eventType: 'ai.solution.completed', createdAt: '2026-08-29T05:20:14Z' }, endpoint: { id: 'endpoint', name: 'Endpoint', url: 'https://consumer.example/hook' }, targetUrl: 'https://consumer.example/hook', nextRetryAt: null, createdAt: '2026-08-29T05:20:14Z', updatedAt: '2026-08-29T05:20:14Z' })).mockResolvedValueOnce(json([{ id: 'attempt', attemptNumber: 1, status: 'ABANDONED', startedAt: '2026-08-29T05:20:14Z', completedAt: null, durationMs: null, httpStatusCode: null, errorCode: null }])) )
  const { unmount } = render(<MemoryRouter initialEntries={['/app/app-1/deliveries']}><Routes><Route path="/app/:applicationId/deliveries" element={<DeliveriesPage />} /></Routes></MemoryRouter>)
  for (const status of statuses) expect((await screen.findAllByText(status.replace('_', ' '))).length).toBeGreaterThan(0)
  unmount()
  render(<MemoryRouter initialEntries={['/app/app-1/deliveries/delivery']}><Routes><Route path="/app/:applicationId/deliveries/:deliveryId" element={<DeliveryDetailPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText('Outcome unknown after recovery')).toBeInTheDocument()
  await waitFor(() => expect(screen.queryByText('Request failed')).not.toBeInTheDocument())
})

it('hydrates event ISO date filters for local inputs and sends ISO values on apply', async () => {
  const fetchMock = vi.fn().mockResolvedValue(json({ items: [], nextCursor: null }))
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={['/app/app-1/events?createdFrom=2026-08-31T01%3A00%3A00.000Z&createdTo=2026-08-31T02%3A00%3A00.000Z']}><Routes><Route path="/app/:applicationId/events" element={<EventsPage />} /></Routes></MemoryRouter>)
  const from = await screen.findByLabelText('From') as HTMLInputElement
  expect(from.value).toBeTruthy()
  fireEvent.change(from, { target: { value: '2026-08-31T10:00' } })
  fireEvent.submit(screen.getByRole('button', { name: 'Apply filters' }).closest('form')!)
  await waitFor(() => expect(fetchMock.mock.calls.at(-1)?.[0]).toContain('createdFrom=2026-08-31T'))
})

it('renders delivery date controls, overview metrics, and redacts unexpected fields', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(json({ events: { total: 12, last24Hours: 3 }, deliveries: { pending: 1, processing: 2, retryScheduled: 3, delivered: 4, failed: 5 }, recentFailures: 6 })).mockResolvedValueOnce(json({ items: [{ id: 'delivery', eventId: 'event', eventType: 'ai.solution.completed', endpointId: 'endpoint', endpointName: 'Endpoint', status: 'FAILED', nextRetryAt: null, attemptCount: 1, lastAttempt: null, createdAt: '2026-08-29T05:20:14Z', updatedAt: '2026-08-29T05:20:14Z', claimToken: 'do-not-render', signingSecret: 'do-not-render', ciphertext: 'do-not-render', keyHash: 'do-not-render' }], nextCursor: null }))
  vi.stubGlobal('fetch', fetchMock)
  const { unmount } = render(<MemoryRouter initialEntries={['/app/app-1']}><Routes><Route path="/app/:applicationId" element={<OverviewPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText('12')).toBeInTheDocument(); expect(screen.getByText('Retry scheduled')).toBeInTheDocument()
  unmount()
  render(<MemoryRouter initialEntries={['/app/app-1/deliveries?createdFrom=2026-08-31T01%3A00%3A00.000Z']}><Routes><Route path="/app/:applicationId/deliveries" element={<DeliveriesPage />} /></Routes></MemoryRouter>)
  expect((await screen.findByLabelText('From') as HTMLInputElement).value).not.toBe(''); expect(screen.getByLabelText('To')).toBeInTheDocument()
  expect(screen.queryByText('do-not-render')).not.toBeInTheDocument()
})

it('selects the first owned application at /app and renders a no-applications state', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json([{ id: 'app-a', name: 'App A' }])).mockResolvedValueOnce(json([])))
  const { unmount } = render(<MemoryRouter initialEntries={['/app']}><Routes><Route path="/app" element={<AppIndexPage />} /><Route path="/app/:applicationId" element={<span>Selected app</span>} /></Routes></MemoryRouter>)
  expect(await screen.findByText('Selected app')).toBeInTheDocument()
  unmount()
  render(<MemoryRouter initialEntries={['/app']}><Routes><Route path="/app" element={<AppIndexPage />} /></Routes></MemoryRouter>)
  expect(await screen.findByText('No applications')).toBeInTheDocument()
})

it('keeps App B results when an aborted App A request resolves late', async () => {
  const appA = deferred<Response>(); const fetchMock = vi.fn((url: string) => url.includes('/app-a/') ? appA.promise : Promise.resolve(json({ items: [{ ...event, sourceEventId: 'from-app-b' }], nextCursor: null })))
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={['/app/app-a/events']}><SwitchApplication /><Routes><Route path="/app/:applicationId/events" element={<EventsPage />} /></Routes></MemoryRouter>)
  await userEvent.click(screen.getByRole('button', { name: 'Switch application' }))
  expect(await screen.findByText('from-app-b')).toBeInTheDocument()
  appA.resolve(json({ items: [{ ...event, sourceEventId: 'from-app-a' }], nextCursor: null }))
  await waitFor(() => expect(screen.queryByText('from-app-a')).not.toBeInTheDocument())
})

it('transitions the session state after a dashboard 401', async () => {
  const fetchMock = vi.fn((url: string) => url.includes('/auth/me') ? Promise.resolve(json({ id: 'user', email: 'user@example.com', displayName: 'User', avatarUrl: null })) : Promise.resolve(new Response(JSON.stringify({ message: 'Authentication is required' }), { status: 401, headers: { 'Content-Type': 'application/json' } })))
  vi.stubGlobal('fetch', fetchMock)
  render(<AuthProvider><UnauthorizedDashboardRequest /></AuthProvider>)
  expect(await screen.findByText('unauthenticated')).toBeInTheDocument()
})
