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

export class ApiError extends Error {
  constructor(readonly status: number, message: string) { super(message) }
}

type Query = Record<string, string | number | null | undefined>

async function request<T>(path: string, query?: Query, signal?: AbortSignal): Promise<T> {
  const params = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const response = await credentialedFetch(`${path}${params.size ? `?${params}` : ''}`, { signal })
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event('webhook-platform:unauthenticated'))
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(response.status, body?.message ?? `Request failed with status ${response.status}`)
  }
  return response.json() as Promise<T>
}

export const dashboardApi = {
  listApplications: (signal?: AbortSignal) => request<Application[]>('/api/v1/applications', undefined, signal),
  summary: (applicationId: string, signal?: AbortSignal) => request<DashboardSummary>(`/api/v1/applications/${applicationId}/dashboard/summary`, undefined, signal),
  events: (applicationId: string, query: Query, signal?: AbortSignal) => request<PagedResponse<EventListItem>>(`/api/v1/applications/${applicationId}/events`, query, signal),
  event: (applicationId: string, eventId: string, signal?: AbortSignal) => request<EventDetail>(`/api/v1/applications/${applicationId}/events/${eventId}`, undefined, signal),
  deliveries: (applicationId: string, query: Query, signal?: AbortSignal) => request<PagedResponse<DeliveryListItem>>(`/api/v1/applications/${applicationId}/deliveries`, query, signal),
  delivery: (applicationId: string, deliveryId: string, signal?: AbortSignal) => request<DeliveryDetail>(`/api/v1/applications/${applicationId}/deliveries/${deliveryId}`, undefined, signal),
  attempts: (applicationId: string, deliveryId: string, signal?: AbortSignal) => request<DeliveryAttempt[]>(`/api/v1/applications/${applicationId}/deliveries/${deliveryId}/attempts`, undefined, signal),
}
