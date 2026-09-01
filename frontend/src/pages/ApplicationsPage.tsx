import { Link, useOutletContext } from 'react-router-dom'
import { ApplicationOnboarding } from '../components/application/ApplicationOnboarding'
import { Button } from '../components/ui/Button'
import { ErrorState, LoadingState } from '../components/states/States'
import { compactDateTime } from '../lib/format'
import type { ApplicationShellContext } from '../layouts/AuthenticatedLayout'

function applicationStatusLabel(status: 'ACTIVE' | 'DISABLED') {
  return status === 'ACTIVE' ? 'Active' : 'Disabled'
}

export function ApplicationsPage() {
  const context = useOutletContext<ApplicationShellContext>()
  if (context.applicationsLoading) return <LoadingState label="Loading applications" />
  if (context.applicationsError) return <ErrorState message={context.applicationsError.message} onRetry={context.retryApplications} />
  if (!context.applications?.length) return <ApplicationOnboarding onCreate={context.openApplicationCreation} />

  return (
    <section className="page-stack applications-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">Configuration</p>
          <h1>Applications</h1>
          <p>Choose an application to inspect its operational state or update its configuration.</p>
        </div>
        <Button onClick={(event) => context.openApplicationCreation(event.currentTarget)}>Create application</Button>
      </div>
      <div className="table-scroll">
        <table>
          <thead><tr><th scope="col">Application</th><th scope="col">Slug</th><th scope="col">Environment</th><th scope="col">Status</th><th scope="col">Created</th><th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
          <tbody>{context.applications.map((application) => (
            <tr key={application.id}>
              <td><Link to={`/app/${application.id}`}>{application.name}</Link><small>{application.id}</small></td>
              <td><code className="mono-inline">{application.slug}</code></td>
              <td>{application.environment === 'PRODUCTION' ? 'Production' : 'Development'}</td>
              <td><span className={`status-badge application-status application-status--${application.status.toLowerCase()}`}><span />{applicationStatusLabel(application.status)}</span></td>
              <td>{compactDateTime(application.createdAt)}</td>
              <td className="application-actions"><Link to={`/app/${application.id}/settings`}>Settings</Link></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  )
}
