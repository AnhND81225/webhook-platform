import { useCallback, useEffect, useRef, useState } from 'react'
import { useOutletContext, useParams } from 'react-router-dom'
import { ApiError, dashboardApi, type Application, type UpdateApplication } from '../api/dashboard-api'
import { Button } from '../components/ui/Button'
import { ErrorState, LoadingState, NotFoundState } from '../components/states/States'
import { dateTime } from '../lib/format'
import { useResource } from '../lib/useResource'
import type { ApplicationShellContext } from '../layouts/AuthenticatedLayout'

const maxNameLength = 120

function displayEnvironment(environment: Application['environment']) {
  return environment === 'PRODUCTION' ? 'Production' : 'Development'
}

export function ApplicationSettingsPage() {
  const { applicationId = '' } = useParams()
  return <ApplicationSettingsContent key={applicationId} applicationId={applicationId} />
}

function ApplicationSettingsContent({ applicationId }: { applicationId: string }) {
  const shell = useOutletContext<ApplicationShellContext>()
  const load = useCallback((signal: AbortSignal) => dashboardApi.application(applicationId, signal), [applicationId])
  const resource = useResource(`application:${applicationId}`, load)
  const initializedFor = useRef<string | null>(null)
  const submitting = useRef(false)
  const [name, setName] = useState('')
  const [status, setStatus] = useState<Application['status']>('ACTIVE')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (resource.data && initializedFor.current !== resource.data.id) {
      initializedFor.current = resource.data.id
      setName(resource.data.name)
      setStatus(resource.data.status)
    }
  }, [resource.data])

  if (resource.loading) return <LoadingState label="Loading application settings" />
  if (resource.error instanceof ApiError && resource.error.status === 404) return <NotFoundState title="Application not found" />
  if (resource.error || !resource.data) return <ErrorState message={resource.error?.message ?? 'Request failed'} onRetry={resource.reload} />

  const application = resource.data
  const normalizedName = name.trim()
  const nameChanged = normalizedName !== application.name
  const statusChanged = status !== application.status
  const dirty = nameChanged || statusChanged

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || !dirty) return
    if (!normalizedName) {
      setError('Enter an application name.')
      return
    }
    if (normalizedName.length > maxNameLength) {
      setError(`Application name must be ${maxNameLength} characters or fewer.`)
      return
    }
    const update: UpdateApplication = {}
    if (nameChanged) update.name = normalizedName
    if (statusChanged) update.status = status
    submitting.current = true
    setSaving(true)
    setError(null)
    setSuccess(null)
    try {
      const updated = await dashboardApi.updateApplication(applicationId, update)
      resource.update(() => updated)
      shell.updateApplicationInShell(updated)
      setName(updated.name)
      setStatus(updated.status)
      setSuccess('Application settings saved.')
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 400) setError('Check the application details and try again.')
      else setError('We could not update the application. Try again.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  return (
    <section className="page-stack application-settings-page">
      <div><p className="eyebrow">Configuration</p><h1>Application settings</h1><p>Update the name and operational availability for this application.</p></div>
      <form className="panel settings-form" onSubmit={(event) => void save(event)} noValidate>
        <div className="settings-form-section">
          <h2>General</h2>
          <label className="form-field" htmlFor="application-name">Application name
            <input id="application-name" value={name} maxLength={maxNameLength} aria-invalid={Boolean(error)} aria-describedby={error ? 'application-settings-error' : undefined} onChange={(event) => { setName(event.target.value); setError(null); setSuccess(null) }} />
          </label>
          <label className="form-field" htmlFor="application-status">Status
            <select id="application-status" value={status} aria-describedby={error ? 'application-settings-error' : undefined} onChange={(event) => { setStatus(event.target.value as Application['status']); setSuccess(null) }}>
              <option value="ACTIVE">Active</option><option value="DISABLED">Disabled</option>
            </select>
          </label>
          <p className="form-helper">Disabled applications reject new producer events. Existing delivery history remains available.</p>
        </div>
        <div className="settings-form-section settings-readonly" aria-label="Immutable application details">
          <h2>Immutable details</h2>
          <dl>
            <div><dt>Slug</dt><dd><code className="mono-inline">{application.slug}</code></dd></div>
            <div><dt>Environment</dt><dd>{displayEnvironment(application.environment)}</dd></div>
            <div><dt>Application ID</dt><dd><code className="mono-inline">{application.id}</code></dd></div>
            <div><dt>Created</dt><dd>{dateTime(application.createdAt)}</dd></div>
            <div><dt>Last updated</dt><dd>{dateTime(application.updatedAt)}</dd></div>
          </dl>
        </div>
        {error && <p className="form-error form-error--summary" id="application-settings-error" role="alert">{error}</p>}
        {success && <p className="settings-success" role="status">{success}</p>}
        <div className="application-form-actions"><Button type="submit" disabled={!dirty || saving}>{saving ? 'Saving changes…' : 'Save changes'}</Button></div>
      </form>
    </section>
  )
}
