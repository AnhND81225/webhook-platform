import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { AuthenticatedLayout } from './AuthenticatedLayout'

const user = { id: 'user-1', email: 'developer@example.com', displayName: 'Developer', avatarUrl: null }
const applications = [
  { id: 'app-a', name: 'Application A', slug: 'application-a', status: 'ACTIVE', environment: 'DEVELOPMENT', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  { id: 'app-b', name: 'Application B', slug: 'application-b', status: 'ACTIVE', environment: 'PRODUCTION', createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' },
]

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>
}

function renderShell(initialEntry = '/app/app-a') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <Routes>
          <Route path="/app" element={<AuthenticatedLayout />}>
            <Route index element={<LocationProbe />} />
            <Route path=":applicationId" element={<LocationProbe />} />
            <Route path=":applicationId/events" element={<LocationProbe />} />
            <Route path=":applicationId/events/:eventId" element={<LocationProbe />} />
            <Route path=":applicationId/deliveries" element={<LocationProbe />} />
            <Route path=":applicationId/deliveries/:deliveryId" element={<LocationProbe />} />
          </Route>
          <Route path="/login" element={<span>Login</span>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(() => vi.unstubAllGlobals())

it('renders authenticated shell navigation and switches nested routes to the selected application overview', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    return Promise.resolve(json(applications))
  }))
  renderShell('/app/app-a/events/event-a')

  expect(await screen.findByText('Webhook Platform')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Events' })).toHaveClass('active')
  expect(screen.getByRole('link', { name: 'Deliveries' })).not.toHaveClass('active')
  expect(screen.getByRole('link', { name: 'Events' })).toHaveAttribute('href', '/app/app-a/events')
  expect(screen.getByRole('link', { name: 'Deliveries' })).toHaveAttribute('href', '/app/app-a/deliveries')
  expect(screen.queryByRole('link', { name: 'Applications' })).not.toBeInTheDocument()

  await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-b')
  expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b')
})

it('keeps Deliveries active at a nested delivery detail route', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(json(url.includes('/auth/me') ? user : applications))))
  renderShell('/app/app-a/deliveries/delivery-a')

  expect((await screen.findByRole('link', { name: 'Deliveries' }))).toHaveClass('active')
  expect(screen.getByRole('link', { name: 'Events' })).not.toHaveClass('active')
})

it('opens the accessible account menu and preserves CSRF-backed logout', async () => {
  const fetchMock = vi.fn((url: string) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/applications')) return Promise.resolve(json(applications))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    return Promise.resolve(new Response(null, { status: 204 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  renderShell()

  const accountTrigger = await screen.findByRole('button', { name: 'Developer' })
  await userEvent.click(accountTrigger)
  expect(screen.getByRole('group', { name: 'Account actions' })).toBeInTheDocument()
  expect(screen.getByText('developer@example.com')).toBeInTheDocument()

  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('group', { name: 'Account actions' })).not.toBeInTheDocument()
  expect(accountTrigger).toHaveFocus()

  await userEvent.click(accountTrigger)

  await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
  await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument())
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/v1/auth/logout',
    expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }) }),
  )
})

it('toggles the accessible mobile navigation state without adding dead destinations', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(json(url.includes('/auth/me') ? user : applications))))
  renderShell()

  const trigger = await screen.findByRole('button', { name: 'Open navigation' })
  expect(trigger).toHaveAttribute('aria-controls', 'application-console-navigation')
  expect(trigger).toHaveAttribute('aria-expanded', 'false')
  await userEvent.click(trigger)
  expect(trigger).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByRole('button', { name: 'Close navigation' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'API Keys' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Endpoints' })).not.toBeInTheDocument()
  await userEvent.keyboard('{Escape}')
  await waitFor(() => expect(trigger).toHaveAttribute('aria-expanded', 'false'))
  expect(trigger).toHaveFocus()
})
