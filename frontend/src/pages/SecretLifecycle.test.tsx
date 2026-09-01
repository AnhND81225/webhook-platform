import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { AuthenticatedLayout } from '../layouts/AuthenticatedLayout'
import { ApiKeysPage } from './ApiKeysPage'
import { EndpointsPage } from './EndpointsPage'

const user = { id: 'user-1', email: 'developer@example.com', displayName: 'Developer', avatarUrl: null }
const apps = [{ id: 'app-a', name: 'App A', slug: 'app-a', status: 'ACTIVE', environment: 'DEVELOPMENT', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }, { id: 'app-b', name: 'App B', slug: 'app-b', status: 'ACTIVE', environment: 'PRODUCTION', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }]
const key = { id: 'key-1', name: 'Key', keyPrefix: 'whpk_', status: 'ACTIVE', lastUsedAt: null, createdAt: '2026-01-01T00:00:00Z', revokedAt: null }
const endpoint = { id: 'endpoint-1', name: 'Endpoint', url: 'https://example.com/hook', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
function Location() { return <output data-testid="location">{useLocation().pathname}</output> }
function renderShell(path: string, secret: string, mode: 'key' | 'endpoint') {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/me')) return Promise.resolve(json(user)); if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf' }))
    if (mode === 'key' && url.endsWith('/api-keys') && init?.method === 'POST') return Promise.resolve(json({ ...key, id: 'key-2', apiKey: secret }, 201))
    if (mode === 'endpoint' && url.endsWith('/endpoints') && init?.method === 'POST') return Promise.resolve(json({ ...endpoint, id: 'endpoint-2', signingSecret: secret }, 201))
    if (mode === 'key' && url.includes('/api-keys')) return Promise.resolve(json([key]))
    if (mode === 'endpoint' && url.includes('/endpoints')) return Promise.resolve(json([endpoint]))
    if (url.includes('/applications')) return Promise.resolve(json(apps)); return Promise.resolve(json([]))
  })
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={[path]}><AuthProvider><Routes><Route path="/app" element={<AuthenticatedLayout />}><Route path="applications" element={<Location />} /><Route path=":applicationId/api-keys" element={<ApiKeysPage />} /><Route path=":applicationId/endpoints" element={<EndpointsPage />} /><Route path=":applicationId" element={<Location />} /></Route></Routes></AuthProvider></MemoryRouter>)
}
async function createSecret(mode: 'key' | 'endpoint', secret: string) {
  await screen.findByText(mode === 'key' ? 'Key' : 'Endpoint')
  await userEvent.click(screen.getByRole('button', { name: mode === 'key' ? 'Create API key' : 'Add endpoint' }))
  if (mode === 'key') { await userEvent.type(screen.getByLabelText('Key name'), 'New key'); await userEvent.click(screen.getByRole('button', { name: 'Create key' })) }
  else { await userEvent.type(screen.getByLabelText('Name'), 'New endpoint'); await userEvent.type(screen.getByLabelText('Destination URL'), 'https://example.com/new'); await userEvent.click(screen.getAllByRole('button', { name: 'Add endpoint' })[1]) }
  expect(await screen.findByText(secret)).toBeInTheDocument()
}
afterEach(() => vi.unstubAllGlobals())

it('clears an API key secret across application switch and route-away/back', async () => { const secret = 'phase2-api-secret-switch-test'; renderShell('/app/app-a/api-keys', secret, 'key'); await createSecret('key', secret); await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-b'); expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b'); expect(screen.queryByText(secret)).not.toBeInTheDocument(); await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-a'); expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-a'); await userEvent.click(screen.getByRole('link', { name: 'API keys' })); expect(await screen.findByRole('heading', { name: 'API keys' })).toBeInTheDocument(); expect(screen.queryByText(secret)).not.toBeInTheDocument(); await userEvent.click(screen.getByRole('link', { name: 'Applications' })); expect(await screen.findByTestId('location')).toHaveTextContent('/app/applications'); await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-a'); expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-a'); await userEvent.click(screen.getByRole('link', { name: 'API keys' })); expect(screen.queryByText(secret)).not.toBeInTheDocument() })

it('clears a signing secret across application switch and route-away/back', async () => { const secret = 'phase2-signing-secret-switch-test'; renderShell('/app/app-a/endpoints', secret, 'endpoint'); await createSecret('endpoint', secret); await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-b'); expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-b'); expect(screen.queryByText(secret)).not.toBeInTheDocument(); await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-a'); expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-a'); await userEvent.click(screen.getByRole('link', { name: 'Endpoints' })); expect(await screen.findByRole('heading', { name: 'Endpoints' })).toBeInTheDocument(); expect(screen.queryByText(secret)).not.toBeInTheDocument(); await userEvent.click(screen.getByRole('link', { name: 'Applications' })); expect(await screen.findByTestId('location')).toHaveTextContent('/app/applications'); await userEvent.selectOptions(screen.getByLabelText('Application'), 'app-a'); expect(await screen.findByTestId('location')).toHaveTextContent('/app/app-a'); await userEvent.click(screen.getByRole('link', { name: 'Endpoints' })); expect(screen.queryByText(secret)).not.toBeInTheDocument() })
