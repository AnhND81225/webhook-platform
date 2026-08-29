import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from './AuthProvider'

function AuthProbe() {
  const auth = useAuth()
  return (
    <div>
      <span>{auth.status}</span>
      {auth.status === 'authenticated' && <span>{auth.user.displayName}</span>}
      <button type="button" onClick={() => void auth.logout()}>Logout</button>
    </div>
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AuthProvider', () => {
  it('loads the authenticated user with credentialed requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'user-1',
      email: 'developer@example.com',
      displayName: 'Developer',
      avatarUrl: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    render(<AuthProvider><AuthProbe /></AuthProvider>)

    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(await screen.findByText('Developer')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/auth/me',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('uses a CSRF token for logout and clears authentication state', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: 'user-1',
        email: 'developer@example.com',
        displayName: 'Developer',
        avatarUrl: null,
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<AuthProvider><AuthProbe /></AuthProvider>)
    await screen.findByText('Developer')
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))

    await waitFor(() => expect(screen.getByText('unauthenticated')).toBeInTheDocument())
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      'http://localhost:8080/api/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }),
      }),
    )
  })
})
