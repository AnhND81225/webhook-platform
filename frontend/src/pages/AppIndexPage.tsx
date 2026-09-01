import { Navigate, useOutletContext } from 'react-router-dom'
import { ApplicationOnboarding } from '../components/application/ApplicationOnboarding'
import { ErrorState, LoadingState } from '../components/states/States'
import type { ApplicationShellContext } from '../layouts/AuthenticatedLayout'

export function AppIndexPage() {
  const context = useOutletContext<ApplicationShellContext>()
  if (context.applicationsLoading) return <LoadingState label="Loading applications" />
  if (context.applicationsError) return <ErrorState message={context.applicationsError.message} onRetry={context.retryApplications} />
  if (!context.applications?.length) {
    return <ApplicationOnboarding onCreate={context.openApplicationCreation} />
  }
  return <Navigate to={`/app/${context.applications[0].id}`} replace />
}
