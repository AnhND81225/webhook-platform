import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { AuthenticatedLayout } from '../layouts/AuthenticatedLayout'
import { AppIndexPage } from './AppIndexPage'
import { slugify } from '../components/application/CreateApplicationDialog'

const user = { id: 'user-1', email: 'developer@example.com', displayName: 'Developer', avatarUrl: null }
const existingApplications = [
  { id: 'app-a', name: 'Application A', slug: 'application-a', status: 'ACTIVE', environment: 'DEVELOPMENT', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  { id: 'app-b', name: 'Application B', slug: 'application-b', status: 'ACTIVE', environment: 'PRODUCTION', createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' },
]
const createdApplication = { id: 'app-new', name: 'AI Study Assistant', slug: 'ai-study-assistant', status: 'ACTIVE', environment: 'DEVELOPMENT', createdAt: '2026-01-03T00:00:00Z', updatedAt: '2026-01-03T00:00:00Z' }

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
function LocationProbe() { return <span data-testid="location">{useLocation().pathname}</span> }
const createButtons = () => screen.getAllByRole('button', { name: 'Create application' })

function renderApp(initialEntry = '/app', applications = existingApplications) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications') && init?.method === 'POST') return Promise.resolve(json(createdApplication, 201))
    if (url.endsWith('/api/v1/applications')) return Promise.resolve(json(applications))
    return Promise.resolve(json({}))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <Routes>
          <Route path="/app" element={<AuthenticatedLayout />}>
            <Route index element={<AppIndexPage />} />
            <Route path=":applicationId" element={<LocationProbe />} />
            <Route path=":applicationId/events/:eventId" element={<LocationProbe />} />
            <Route path=":applicationId/deliveries/:deliveryId" element={<LocationProbe />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

it('shows first-run onboarding instead of the legacy API-only instruction', async () => {
  renderApp('/app', [])
  expect(await screen.findByRole('heading', { name: 'Create your first application' })).toBeInTheDocument()
  expect(createButtons()).toHaveLength(1)
  expect(screen.queryByText(/existing API/i)).not.toBeInTheDocument()
})

it('keeps loading and application-list errors distinct from first-run onboarding', async () => {
  let resolveApplications!: (response: Response) => void
  const applicationsResponse = new Promise<Response>((resolve) => { resolveApplications = resolve })
  vi.stubGlobal('fetch', vi.fn((url: string) => url.includes('/auth/me') ? Promise.resolve(json(user)) : applicationsResponse))
  const { unmount } = render(<MemoryRouter initialEntries={['/app']}><AuthProvider><Routes><Route path="/app" element={<AuthenticatedLayout />}><Route index element={<AppIndexPage />} /></Route></Routes></AuthProvider></MemoryRouter>)
  expect(await screen.findByText('Loading applications…')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Create your first application' })).not.toBeInTheDocument()
  resolveApplications(json([]))
  expect(await screen.findByRole('heading', { name: 'Create your first application' })).toBeInTheDocument()

  unmount()
  vi.stubGlobal('fetch', vi.fn((url: string) => url.includes('/auth/me') ? Promise.resolve(json(user)) : Promise.resolve(new Response(JSON.stringify({ message: 'Unavailable' }), { status: 500, headers: { 'Content-Type': 'application/json' } }))))
  render(<MemoryRouter initialEntries={['/app']}><AuthProvider><Routes><Route path="/app" element={<AuthenticatedLayout />}><Route index element={<AppIndexPage />} /></Route></Routes></AuthProvider></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load this data')
  expect(screen.queryByRole('heading', { name: 'Create your first application' })).not.toBeInTheDocument()
})

it('normalizes generated slugs from punctuation, repeated separators, and surrounding whitespace', () => {
  expect(slugify('  AI---Study   Assistant!  ')).toBe('ai-study-assistant')
  expect(slugify('***')).toBe('')
})

it('creates an application through the CSRF-protected API and immediately switches to it', async () => {
  const fetchMock = renderApp('/app', [])
  await screen.findByRole('heading', { name: 'Create your first application' })
  await userEvent.click(createButtons()[0])
  const dialog = screen.getByRole('dialog', { name: 'Create application' })
  expect(within(dialog).getByRole('textbox', { name: /Application name/ })).toHaveFocus()

  const nameInput = within(dialog).getByRole('textbox', { name: /Application name/ })
  const slugInput = within(dialog).getByRole('textbox', { name: /Slug/ })
  await userEvent.type(nameInput, 'AI Study Assistant')
  expect(slugInput).toHaveValue('ai-study-assistant')
  await userEvent.clear(slugInput)
  await userEvent.type(slugInput, 'study-app')
  await userEvent.clear(nameInput)
  await userEvent.type(nameInput, 'Renamed app')
  expect(slugInput).toHaveValue('study-app')
  await userEvent.clear(nameInput)
  await userEvent.type(nameInput, 'AI Study Assistant')
  expect(within(dialog).getByRole('combobox', { name: /Environment/ })).toHaveValue('DEVELOPMENT')
  await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: /Environment/ }), 'PRODUCTION')
  await userEvent.clear(slugInput)
  await userEvent.type(slugInput, 'ai-study-assistant')

  await userEvent.click(within(dialog).getByRole('button', { name: 'Create application' }))
  expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-new')
  expect(screen.getByLabelText('Application')).toHaveValue('app-new')
  expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/v1/auth/csrf', expect.anything())
  expect(fetchMock).toHaveBeenCalledWith(
    'http://localhost:8080/api/v1/applications',
    expect.objectContaining({ method: 'POST', credentials: 'include', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }), body: JSON.stringify({ name: 'AI Study Assistant', slug: 'ai-study-assistant', environment: 'PRODUCTION' }) }),
  )
})

it('provides creation from the switcher and always switches nested resources to the selected application overview', async () => {
  renderApp('/app/app-a/events/event-a')
  await userEvent.selectOptions(await screen.findByLabelText('Application'), 'app-b')
  expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b')
  expect(screen.getByRole('option', { name: 'Application B · Production' })).toBeInTheDocument()

  const switcherTrigger = screen.getByRole('button', { name: 'Create application' })
  await userEvent.click(switcherTrigger)
  expect(screen.getByRole('dialog', { name: 'Create application' })).toBeInTheDocument()
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('dialog', { name: 'Create application' })).not.toBeInTheDocument()
  expect(switcherTrigger).toHaveFocus()
})

it('switches from a delivery detail to the selected application overview', async () => {
  renderApp('/app/app-a/deliveries/delivery-a')
  await userEvent.selectOptions(await screen.findByLabelText('Application'), 'app-b')
  expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b')
})

it('switches from an application overview to the selected application overview', async () => {
  renderApp('/app/app-a')
  await userEvent.selectOptions(await screen.findByLabelText('Application'), 'app-b')
  expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b')
})

it('validates locally and presents safe conflict errors without a mutation before client validation', async () => {
  const fetchMock = renderApp('/app', [])
  await screen.findByRole('heading', { name: 'Create your first application' })
  await userEvent.click(createButtons()[0])
  const dialog = screen.getByRole('dialog', { name: 'Create application' })
  await userEvent.click(within(dialog).getByRole('button', { name: 'Create application' }))
  expect(within(dialog).getByText('Enter an application name.')).toBeInTheDocument()
  expect(within(dialog).getByText('Enter a slug.')).toBeInTheDocument()
  expect(fetchMock).not.toHaveBeenCalledWith('http://localhost:8080/api/v1/auth/csrf', expect.anything())

  await userEvent.type(within(dialog).getByRole('textbox', { name: /Application name/ }), 'Conflict')
  vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications') && init?.method === 'POST') return Promise.resolve(json({ code: 'APPLICATION_SLUG_CONFLICT', message: 'An Application with this slug already exists.' }, 409))
    return Promise.resolve(json([]))
  }))
  await userEvent.click(within(dialog).getByRole('button', { name: 'Create application' }))
  expect(await within(dialog).findByText('This slug is already used by one of your applications.')).toBeInTheDocument()
})

it('rejects invalid slugs locally and gives a generic message for validation and network failures', async () => {
  renderApp('/app', [])
  await screen.findByRole('heading', { name: 'Create your first application' })
  await userEvent.click(createButtons()[0])
  const dialog = screen.getByRole('dialog', { name: 'Create application' })
  const nameInput = within(dialog).getByRole('textbox', { name: /Application name/ })
  const slugInput = within(dialog).getByRole('textbox', { name: /Slug/ })
  await userEvent.type(nameInput, 'Valid name')
  await userEvent.clear(slugInput)
  await userEvent.type(slugInput, `${'a'.repeat(63)}-`)
  await userEvent.click(within(dialog).getByRole('button', { name: 'Create application' }))
  expect(within(dialog).getByText('Use lowercase letters, numbers, and single hyphens only.')).toBeInTheDocument()

  vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications') && init?.method === 'POST') return Promise.resolve(json({ code: 'VALIDATION_ERROR', message: 'Request validation failed.' }, 400))
    return Promise.resolve(json([]))
  }))
  await userEvent.clear(slugInput)
  await userEvent.type(slugInput, 'valid-name')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Create application' }))
  expect(await within(dialog).findByText('Check the application details and try again.')).toBeInTheDocument()

  vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications') && init?.method === 'POST') return Promise.reject(new TypeError('Network unavailable'))
    return Promise.resolve(json([]))
  }))
  await userEvent.click(within(dialog).getByRole('button', { name: 'Create application' }))
  expect(await within(dialog).findByText('We could not create the application. Try again.')).toBeInTheDocument()
})

it('keeps a created application request disabled while it is in flight', async () => {
  let resolveCreation!: (response: Response) => void
  const creation = new Promise<Response>((resolve) => { resolveCreation = resolve })
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user))
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf-token' }))
    if (url.endsWith('/api/v1/applications') && init?.method === 'POST') return creation
    return Promise.resolve(json([]))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={['/app']}><AuthProvider><Routes><Route path="/app" element={<AuthenticatedLayout />}><Route index element={<AppIndexPage />} /><Route path=":applicationId" element={<LocationProbe />} /></Route></Routes></AuthProvider></MemoryRouter>)
  await screen.findByRole('heading', { name: 'Create your first application' })
  await userEvent.click(createButtons()[0])
  const dialog = screen.getByRole('dialog', { name: 'Create application' })
  await userEvent.type(within(dialog).getByRole('textbox', { name: /Application name/ }), 'AI Study Assistant')
  const form = within(dialog).getByRole('button', { name: 'Create application' }).closest('form')!
  fireEvent.submit(form)
  fireEvent.submit(form)
  expect(within(dialog).getByRole('button', { name: 'Creating application…' })).toBeDisabled()
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/api/v1/applications') && (init as RequestInit | undefined)?.method === 'POST')).toHaveLength(1))
  resolveCreation(json(createdApplication, 201))
  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/app/app-new'))
})
