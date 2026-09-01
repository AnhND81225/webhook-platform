import { credentialedFetch } from '../config/api'

export type AuthenticatedUser = {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
}

export class UnauthenticatedError extends Error {}

async function requireSuccessfulResponse(response: Response): Promise<Response> {
  if (response.status === 401) {
    throw new UnauthenticatedError('Authentication is required')
  }
  if (!response.ok) {
    throw new Error(`Authentication request failed with status ${response.status}`)
  }
  return response
}

export async function fetchCurrentUser(signal?: AbortSignal): Promise<AuthenticatedUser> {
  const response = await requireSuccessfulResponse(
    await credentialedFetch('/api/v1/auth/me', { signal }),
  )
  return response.json() as Promise<AuthenticatedUser>
}

export async function logoutSession(): Promise<void> {
  const csrfResponse = await requireSuccessfulResponse(
    await credentialedFetch('/api/v1/auth/csrf'),
  )
  const csrf = (await csrfResponse.json()) as { token: string }
  const logoutResponse = await credentialedFetch('/api/v1/auth/logout', {
    method: 'POST',
    headers: {
      'X-CSRF-TOKEN': csrf.token,
    },
  })
  await requireSuccessfulResponse(logoutResponse)
}
