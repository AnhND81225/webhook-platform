import { useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { dashboardApi } from '../api/dashboard-api'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States'
import { useResource } from '../lib/useResource'

export function AppIndexPage() {
  const load = useCallback((signal: AbortSignal) => dashboardApi.listApplications(signal), [])
  const state = useResource('applications:index', load)
  if (state.loading) return <LoadingState label="Loading applications" />
  if (state.error) return <ErrorState message={state.error.message} onRetry={state.reload} />
  if (!state.data?.length) return <EmptyState title="No applications" detail="Create an application through the existing API before viewing dashboard data." />
  return <Navigate to={`/app/${state.data[0].id}`} replace />
}
