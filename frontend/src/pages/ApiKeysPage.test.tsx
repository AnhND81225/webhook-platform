import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { ApiKeysPage } from './ApiKeysPage'

const active = { id: 'key-1', name: 'Primary producer', keyPrefix: 'whpk_live_', status: 'ACTIVE', lastUsedAt: null, createdAt: '2026-01-01T00:00:00Z', revokedAt: null }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function renderPage(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={['/app/app-a/api-keys']}><Routes><Route path="/app/:applicationId/api-keys" element={<ApiKeysPage />} /><Route path="/away" element={<p>Away</p>} /></Routes></MemoryRouter>)
}
afterEach(() => vi.unstubAllGlobals())

it('creates one key for same-tick submits and clears its one-time secret on close', async () => {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf' }))
    if (url.endsWith('/api/v1/applications/app-a/api-keys') && init?.method === 'POST') return Promise.resolve(json({ ...active, id: 'key-2', name: 'New producer', apiKey: 'test-secret-phase2-api-key' }, 201))
    return Promise.resolve(json([active]))
  })
  renderPage(fetchMock)
  await screen.findByText('Primary producer')
  await userEvent.click(screen.getByRole('button', { name: 'Create API key' }))
  await userEvent.type(screen.getByLabelText('Key name'), 'New producer')
  const form = screen.getByRole('button', { name: 'Create key' }).closest('form')!
  fireEvent.submit(form); fireEvent.submit(form)
  expect(await screen.findByText('test-secret-phase2-api-key')).toBeInTheDocument()
  expect(fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/api/v1/applications/app-a/api-keys') && (init as RequestInit).method === 'POST')).toHaveLength(1)
  expect(screen.getByText('New producer')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Done' }))
  expect(screen.queryByText('test-secret-phase2-api-key')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Create API key' }))
  expect(screen.queryByText('test-secret-phase2-api-key')).not.toBeInTheDocument()
})

it('requires confirmation and sends one CSRF-backed revoke mutation', async () => {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.includes('/auth/csrf')) return Promise.resolve(json({ token: 'csrf' }))
    if (url.endsWith('/api/v1/api-keys/key-1/revoke')) return Promise.resolve(json({ ...active, status: 'REVOKED', revokedAt: '2026-01-02T00:00:00Z' }))
    return Promise.resolve(json([active]))
  })
  renderPage(fetchMock); await screen.findByText('Primary producer')
  await userEvent.click(screen.getByRole('button', { name: 'Revoke' }))
  await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/revoke'))).toBe(false)
  await userEvent.click(screen.getByRole('button', { name: 'Revoke' }))
  const confirm = screen.getByRole('button', { name: 'Revoke key' }); fireEvent.click(confirm); fireEvent.click(confirm)
  await screen.findByText('Revoked')
  expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/v1/api-keys/key-1/revoke'))).toHaveLength(1)
  expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument()
})

it('renders API key loading, error retry, empty, and safe metadata states', async () => {
  let calls = 0
  const fetchMock = vi.fn(() => { calls += 1; return Promise.resolve(calls === 1 ? json({ message: 'Unavailable' }, 500) : json([])) })
  renderPage(fetchMock)
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByText('No API keys yet')).toBeInTheDocument()
  expect(screen.queryByText(/••••/)).not.toBeInTheDocument()
})
