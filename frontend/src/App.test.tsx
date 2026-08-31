import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App'
import { AuthProvider } from './auth/AuthProvider'

afterEach(() => {
  vi.unstubAllGlobals()
})

it('redirects an unauthenticated app route to login without rendering protected content', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

  render(
    <MemoryRouter initialEntries={['/app']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )

  expect(screen.getByText('Loading your session')).toBeInTheDocument()
  expect(await screen.findByText('Reliable delivery starts here.')).toBeInTheDocument()
  expect(screen.queryByText('Developer dashboard')).not.toBeInTheDocument()
})

it('shows a safe OAuth failure message', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

  render(
    <MemoryRouter initialEntries={['/login?error=oauth']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )

  expect(await screen.findByRole('alert')).toHaveTextContent('Google sign-in could not be completed')
})

it('renders the public landing page with product-accurate calls to action', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )

  expect(screen.getByRole('heading', { name: 'Reliable webhook delivery for modern applications.' })).toBeInTheDocument()
  expect(screen.getAllByRole('link', { name: 'Get started' })[0]).toHaveAttribute('href', '/login')
  expect(screen.getByText('Automatic retries')).toBeInTheDocument()
  expect(screen.queryByText(/manual retry|pricing|free events/i)).not.toBeInTheDocument()
})
