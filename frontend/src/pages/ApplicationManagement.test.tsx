import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { AuthenticatedLayout } from '../layouts/AuthenticatedLayout'
import { ApplicationsPage } from './ApplicationsPage'
import { ApplicationSettingsPage } from './ApplicationSettingsPage'

const user = { id: 'user-1', email: 'developer@example.com', displayName: 'Developer', avatarUrl: null }
const application = { id: 'app-a', name: 'Application A', slug: 'application-a', status: 'ACTIVE', environment: 'DEVELOPMENT', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
const anotherApplication = { id: 'app-b', name: 'Application B', slug: 'application-b', status: 'DISABLED', environment: 'PRODUCTION', createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }

function LocationProbe() { return <span data-testid="location">{useLocation().pathname}</span> }

function renderApplications(initialEntry = '/app/applications', fetchMock?: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', fetchMock ?? vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications/app-a') && init?.method === 'PATCH') return Promise.resolve(json({ ...application, name: 'Renamed application', updatedAt: '2026-01-03T00:00:00Z' }))
    if (url.endsWith('/api/v1/applications/app-a')) return Promise.resolve(json(application))
    if (url.endsWith('/api/v1/applications')) return Promise.resolve(json([application, anotherApplication]))
    return Promise.resolve(json({}))
  }))
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <Routes>
          <Route path="/app" element={<AuthenticatedLayout />}>
            <Route path="applications" element={<ApplicationsPage />} />
            <Route path=":applicationId/settings" element={<><ApplicationSettingsPage /><LocationProbe /></>} />
            <Route path=":applicationId" element={<LocationProbe />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(() => vi.unstubAllGlobals())

it('resolves the global /app/applications route instead of treating applications as an application ID', async () => {
  renderApplications()
  expect(await screen.findByRole('heading', { name: 'Applications' })).toBeInTheDocument()
  const navigation = screen.getByRole('navigation', { name: 'Primary navigation' })
  expect(within(navigation).getByRole('link', { name: 'Applications' })).toHaveClass('active')
  expect(within(navigation).queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument()
  expect(within(navigation).queryByRole('link', { name: 'Events' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Application A' })).toHaveAttribute('href', '/app/app-a')
  expect(screen.getAllByRole('link', { name: 'Settings' })[0]).toHaveAttribute('href', '/app/app-a/settings')
  expect(screen.getByText('application-a')).toBeInTheDocument()
  expect(screen.getByText('Development')).toBeInTheDocument()
  expect(screen.getByText('Disabled')).toBeInTheDocument()
})

it('updates only supported settings fields through CSRF PATCH and synchronizes the shell', async () => {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications/app-a') && init?.method === 'PATCH') return Promise.resolve(json({ ...application, ...JSON.parse(String(init.body)), updatedAt: '2026-01-03T00:00:00Z' }))
    if (url.endsWith('/api/v1/applications/app-a')) return Promise.resolve(json(application))
    if (url.endsWith('/api/v1/applications')) return Promise.resolve(json([application, anotherApplication]))
    return Promise.resolve(json({}))
  })
  renderApplications('/app/app-a/settings', fetchMock)
  expect(await screen.findByRole('heading', { name: 'Application settings' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Settings' })).toHaveClass('active')
  expect(screen.getByText('application-a')).toBeInTheDocument()
  expect(screen.getAllByText('Development').length).toBeGreaterThan(0)

  const save = screen.getByRole('button', { name: 'Save changes' })
  await waitFor(() => expect(screen.getByLabelText('Application name')).toHaveValue('Application A'))
  expect(save).toBeDisabled()
  const name = screen.getByLabelText('Application name')
  await userEvent.clear(name)
  await userEvent.type(name, 'Renamed application')
  expect(save).toBeEnabled()
  await userEvent.clear(name)
  await userEvent.type(name, 'Application A')
  expect(save).toBeDisabled()
  await userEvent.clear(name)
  await userEvent.type(name, 'Renamed application')
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

  expect(await screen.findByRole('status')).toHaveTextContent('Application settings saved.')
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/v1/applications/app-a',
    expect.objectContaining({ method: 'PATCH', credentials: 'include', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }), body: JSON.stringify({ name: 'Renamed application' }) }),
  )
  expect(screen.getByLabelText('Application')).toHaveTextContent('Renamed application · Development')
  expect(screen.getByTestId('location')).toHaveTextContent('/app/app-a/settings')
  expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
  await userEvent.selectOptions(screen.getByLabelText('Status'), 'DISABLED')
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/api/v1/applications/app-a') && (init as RequestInit | undefined)?.method === 'PATCH')).toHaveLength(2))
  const patchCalls = fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/api/v1/applications/app-a') && (init as RequestInit | undefined)?.method === 'PATCH')
  expect((patchCalls[1][1] as RequestInit).body).toBe(JSON.stringify({ status: 'DISABLED' }))
  expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
  await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-b')
  expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b')
})

it('keeps loading/errors distinct from the empty state and retries application listing', async () => {
  let attempts = 0
  const fetchMock = vi.fn((url: string) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.endsWith('/api/v1/applications')) {
      attempts += 1
      return Promise.resolve(attempts === 1 ? json({ message: 'Unavailable' }, 500) : json([]))
    }
    return Promise.resolve(json({}))
  })
  renderApplications('/app/applications', fetchMock)
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load this data')
  expect(screen.queryByRole('heading', { name: 'Create your first application' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Create your first application' })).toBeInTheDocument())
  const navigation = screen.getByRole('navigation', { name: 'Primary navigation' })
  expect(within(navigation).getByRole('link', { name: 'Applications' })).toBeInTheDocument()
  expect(within(navigation).queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument()
})

it('prevents synchronous duplicate PATCH submissions', async () => {
  const patchResponse = deferred<Response>()
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications/app-a') && init?.method === 'PATCH') return patchResponse.promise
    if (url.endsWith('/api/v1/applications/app-a')) return Promise.resolve(json(application))
    if (url.endsWith('/api/v1/applications')) return Promise.resolve(json([application, anotherApplication]))
    return Promise.resolve(json({}))
  })
  renderApplications('/app/app-a/settings', fetchMock)
  const name = await screen.findByLabelText('Application name')
  await userEvent.clear(name)
  await userEvent.type(name, 'Renamed application')
  const form = screen.getByRole('button', { name: 'Save changes' }).closest('form')!
  fireEvent.submit(form)
  fireEvent.submit(form)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/api/v1/applications/app-a') && (init as RequestInit | undefined)?.method === 'PATCH')).toHaveLength(1))
  patchResponse.resolve(json({ ...application, name: 'Renamed application', updatedAt: '2026-01-03T00:00:00Z' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Application settings saved.')
})

it('does not allow an older applications GET to overwrite a successful PATCH', async () => {
  const staleList = deferred<Response>()
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications/app-a') && init?.method === 'PATCH') return Promise.resolve(json({ ...application, name: 'Renamed application', updatedAt: '2026-01-03T00:00:00Z' }))
    if (url.endsWith('/api/v1/applications/app-a')) return Promise.resolve(json(application))
    if (url.endsWith('/api/v1/applications')) return staleList.promise
    return Promise.resolve(json({}))
  })
  renderApplications('/app/app-a/settings', fetchMock)
  const name = await screen.findByLabelText('Application name')
  await userEvent.clear(name)
  await userEvent.type(name, 'Renamed application')
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Application settings saved.')
  staleList.resolve(json([application]))
  await waitFor(() => expect(screen.getByLabelText('Application')).toHaveTextContent('Renamed application · Development'))
  await userEvent.click(screen.getByRole('link', { name: 'Applications' }))
  expect(await screen.findByRole('link', { name: 'Renamed application' })).toBeInTheDocument()
})
