import { Navigate, useOutletContext } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { ErrorState, LoadingState } from '../components/states/States'
import type { ApplicationShellContext } from '../layouts/AuthenticatedLayout'

export function AppIndexPage() {
  const context = useOutletContext<ApplicationShellContext>()
  if (context.applicationsLoading) return <LoadingState label="Loading applications" />
  if (context.applicationsError) return <ErrorState message={context.applicationsError.message} onRetry={context.retryApplications} />
  if (!context.applications?.length) {
    return (
      <section className="application-onboarding">
        <span className="eyebrow">Webhook Platform</span>
        <h1>Create your first application</h1>
        <p>Applications group events, endpoints, API keys, and delivery data for a producer.</p>
        <Button onClick={(event) => context.openApplicationCreation(event.currentTarget)}>Create application</Button>
      </section>
    )
  }
  return <Navigate to={`/app/${context.applications[0].id}`} replace />
}
