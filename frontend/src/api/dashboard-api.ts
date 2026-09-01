import { credentialedFetch } from '../config/api'

export type Application = {
  id: string
  name: string
  slug: string
  status: 'ACTIVE' | 'DISABLED'
  environment: 'DEVELOPMENT' | 'PRODUCTION'
  createdAt: string
  updatedAt: string
}

export type CreateApplication = Pick<Application, 'name' | 'slug' | 'environment'>
export type UpdateApplication = Partial<Pick<Application, 'name' | 'status'>>

export type DashboardSummary = {
  events: { total: number; last24Hours: number }
  deliveries: { pending: number; processing: number; retryScheduled: number; delivered: number; failed: number }
  recentFailures: number
}

export type EventListItem = { id: string; sourceEventId: string; eventType: string; createdAt: string; deliveryCount: number; deliveredCount: number; failedCount: number; retryScheduledCount: number }
export type EventDetail = EventListItem & { payload: unknown }
export type DeliveryStatus = 'PENDING' | 'PROCESSING' | 'RETRY_SCHEDULED' | 'DELIVERED' | 'FAILED'
export type AttemptStatus = 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED' | 'ABANDONED'
export type AttemptSummary = { attemptNumber: number; status: AttemptStatus; httpStatusCode: number | null; errorCode: string | null }
export type DeliveryListItem = { id: string; eventId: string; eventType: string; endpointId: string; endpointName: string; status: DeliveryStatus; nextRetryAt: string | null; attemptCount: number; lastAttempt: AttemptSummary | null; createdAt: string; updatedAt: string }
export type DeliveryDetail = { id: string; status: DeliveryStatus; event: { id: string; sourceEventId: string; eventType: string; createdAt: string }; endpoint: { id: string; name: string; url: string }; targetUrl: string; nextRetryAt: string | null; createdAt: string; updatedAt: string }
export type DeliveryAttempt = { id: string; attemptNumber: number; status: AttemptStatus; startedAt: string; completedAt: string | null; durationMs: number | null; httpStatusCode: number | null; errorCode: string | null }
export type PagedResponse<T> = { items: T[]; nextCursor: string | null }
export type ApiKeyStatus = 'ACTIVE' | 'REVOKED'
export type ApiKeyMetadata = { id: string; name: string; keyPrefix: string; status: ApiKeyStatus; lastUsedAt: string | null; createdAt: string; revokedAt: string | null }
export type CreatedApiKey = ApiKeyMetadata & { apiKey: string }
export type WebhookEndpointStatus = 'ACTIVE' | 'DISABLED'
export type WebhookEndpoint = { id: string; name: string; url: string; status: WebhookEndpointStatus; createdAt: string; updatedAt: string }
export type CreatedWebhookEndpoint = WebhookEndpoint & { signingSecret: string }
export type WebhookSubscription = { id: string; eventType: string; createdAt: string }

export class ApiError extends Error {
  constructor(readonly status: number, message: string, readonly code: string | null = null) { super(message) }
}

type Query = Record<string, string | number | null | undefined>

async function parseError(response: Response): Promise<ApiError> {
  if (response.status === 401) window.dispatchEvent(new Event('webhook-platform:unauthenticated'))
  const body = await response.json().catch(() => null) as { code?: string; message?: string } | null
  return new ApiError(response.status, body?.message ?? `Request failed with status ${response.status}`, body?.code ?? null)
}

async function request<T>(path: string, query?: Query, signal?: AbortSignal): Promise<T> {
  const params = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const response = await credentialedFetch(`${path}${params.size ? `?${params}` : ''}`, { signal })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<T>
}

async function mutation<T>(path: string, method: 'POST' | 'PATCH' | 'DELETE', body?: unknown): Promise<T> {
  const csrfResponse = await credentialedFetch('/api/v1/auth/csrf')
  if (!csrfResponse.ok) throw await parseError(csrfResponse)
  const csrf = await csrfResponse.json() as { token: string }
  const response = await credentialedFetch(path, {
    method,
    headers: body === undefined ? { 'X-CSRF-TOKEN': csrf.token } : { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const dashboardApi = {
  listApplications: (signal?: AbortSignal) => request<Application[]>('/api/v1/applications', undefined, signal),
  application: (applicationId: string, signal?: AbortSignal) => request<Application>(`/api/v1/applications/${applicationId}`, undefined, signal),
  createApplication: (application: CreateApplication) => mutation<Application>('/api/v1/applications', 'POST', application),
  updateApplication: (applicationId: string, application: UpdateApplication) => mutation<Application>(`/api/v1/applications/${applicationId}`, 'PATCH', application),
  summary: (applicationId: string, signal?: AbortSignal) => request<DashboardSummary>(`/api/v1/applications/${applicationId}/dashboard/summary`, undefined, signal),
  events: (applicationId: string, query: Query, signal?: AbortSignal) => request<PagedResponse<EventListItem>>(`/api/v1/applications/${applicationId}/events`, query, signal),
  event: (applicationId: string, eventId: string, signal?: AbortSignal) => request<EventDetail>(`/api/v1/applications/${applicationId}/events/${eventId}`, undefined, signal),
  deliveries: (applicationId: string, query: Query, signal?: AbortSignal) => request<PagedResponse<DeliveryListItem>>(`/api/v1/applications/${applicationId}/deliveries`, query, signal),
  delivery: (applicationId: string, deliveryId: string, signal?: AbortSignal) => request<DeliveryDetail>(`/api/v1/applications/${applicationId}/deliveries/${deliveryId}`, undefined, signal),
  attempts: (applicationId: string, deliveryId: string, signal?: AbortSignal) => request<DeliveryAttempt[]>(`/api/v1/applications/${applicationId}/deliveries/${deliveryId}/attempts`, undefined, signal),
  apiKeys: (applicationId: string, signal?: AbortSignal) => request<ApiKeyMetadata[]>(`/api/v1/applications/${applicationId}/api-keys`, undefined, signal),
  createApiKey: (applicationId: string, requestBody: { name: string }) => mutation<CreatedApiKey>(`/api/v1/applications/${applicationId}/api-keys`, 'POST', requestBody),
  revokeApiKey: (apiKeyId: string) => mutation<ApiKeyMetadata>(`/api/v1/api-keys/${apiKeyId}/revoke`, 'POST'),
  endpoints: (applicationId: string, signal?: AbortSignal) => request<WebhookEndpoint[]>(`/api/v1/applications/${applicationId}/endpoints`, undefined, signal),
  endpoint: (applicationId: string, endpointId: string, signal?: AbortSignal) => request<WebhookEndpoint>(`/api/v1/applications/${applicationId}/endpoints/${endpointId}`, undefined, signal),
  createEndpoint: (applicationId: string, requestBody: { name: string; url: string }) => mutation<CreatedWebhookEndpoint>(`/api/v1/applications/${applicationId}/endpoints`, 'POST', requestBody),
  updateEndpoint: (applicationId: string, endpointId: string, requestBody: Partial<Pick<WebhookEndpoint, 'name' | 'url' | 'status'>>) => mutation<WebhookEndpoint>(`/api/v1/applications/${applicationId}/endpoints/${endpointId}`, 'PATCH', requestBody),
  provisionSigningSecret: (applicationId: string, endpointId: string) => mutation<{ value: string }>(`/api/v1/applications/${applicationId}/endpoints/${endpointId}/signing-secret`, 'POST'),
  subscriptions: (applicationId: string, endpointId: string, signal?: AbortSignal) => request<WebhookSubscription[]>(`/api/v1/applications/${applicationId}/endpoints/${endpointId}/subscriptions`, undefined, signal),
  createSubscription: (applicationId: string, endpointId: string, requestBody: { eventType: string }) => mutation<WebhookSubscription>(`/api/v1/applications/${applicationId}/endpoints/${endpointId}/subscriptions`, 'POST', requestBody),
  deleteSubscription: (applicationId: string, endpointId: string, subscriptionId: string) => mutation<void>(`/api/v1/applications/${applicationId}/endpoints/${endpointId}/subscriptions/${subscriptionId}`, 'DELETE'),
}
