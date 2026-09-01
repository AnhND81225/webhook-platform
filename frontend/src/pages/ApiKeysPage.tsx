import { useCallback, useRef, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, dashboardApi, type ApiKeyMetadata } from '../api/dashboard-api'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Button } from '../components/ui/Button'
import { SecretRevealDialog } from '../components/ui/SecretRevealDialog'
import { Dialog } from '../components/ui/Dialog'
import { EmptyState, ErrorState, LoadingState, NotFoundState } from '../components/states/States'
import { useResource } from '../lib/useResource'
import { dateTime } from '../lib/format'

export function ApiKeysPage() { const { applicationId = '' } = useParams(); return <ApiKeysContent key={applicationId} applicationId={applicationId} /> }
function ApiKeysContent({ applicationId }: { applicationId: string }) {
  const resource = useResource(`api-keys:${applicationId}`, useCallback((signal: AbortSignal) => dashboardApi.apiKeys(applicationId, signal), [applicationId]))
  const [createOpen, setCreateOpen] = useState(false); const [revealed, setRevealed] = useState<string | null>(null); const [revoking, setRevoking] = useState<ApiKeyMetadata | null>(null); const createRef = useRef<HTMLButtonElement>(null)
  if (resource.loading) return <LoadingState label="Loading API keys" />
  if (resource.error instanceof ApiError && resource.error.status === 404) return <NotFoundState title="Application not found" />
  if (resource.error || !resource.data) return <ErrorState message={resource.error?.message ?? 'Request failed'} onRetry={resource.reload} />
  return <section className="page-stack credential-page"><div className="page-heading"><div><p className="eyebrow">Credentials</p><h1>API keys</h1><p>Create producer credentials and revoke them when they are no longer needed.</p></div><Button ref={createRef} onClick={() => setCreateOpen(true)}>Create API key</Button></div>
    {resource.data.length === 0 ? <EmptyState title="No API keys yet" detail="Create a key for a producer that sends events to this application." /> : <div className="panel table-scroll"><table><thead><tr><th>Name</th><th>Prefix</th><th>Status</th><th>Last used</th><th>Created</th><th><span className="visually-hidden">Actions</span></th></tr></thead><tbody>{resource.data.map(key => <tr key={key.id}><td>{key.name}</td><td><code className="mono-inline">{key.keyPrefix}</code></td><td><span className={`credential-status credential-status--${key.status.toLowerCase()}`}>{key.status === 'ACTIVE' ? 'Active' : 'Revoked'}</span></td><td>{key.lastUsedAt ? dateTime(key.lastUsedAt) : 'Never'}</td><td>{dateTime(key.createdAt)}</td><td>{key.status === 'ACTIVE' && <Button variant="quiet" onClick={() => setRevoking(key)}>Revoke</Button>}</td></tr>)}</tbody></table></div>}
    {createOpen && <CreateApiKeyDialog applicationId={applicationId} onDismiss={() => setCreateOpen(false)} returnFocusRef={createRef} onCreated={(key, raw) => { resource.update(current => [key, ...(current ?? [])]); setCreateOpen(false); setRevealed(raw) }} />}
    {revealed && <SecretRevealDialog title="API key created" secret={revealed} description="Copy this API key now. For security, it is shown only once." onDismiss={() => setRevealed(null)} returnFocusRef={createRef} />}
    {revoking && <ConfirmDialog title="Revoke API key" description={`Revoke ${revoking.name}? Producers using it will no longer be authenticated.`} confirmLabel="Revoke key" onDismiss={() => setRevoking(null)} onConfirm={async () => { const updated = await dashboardApi.revokeApiKey(revoking.id); resource.update(current => (current ?? []).map(key => key.id === updated.id ? updated : key)) }} />}
  </section>
}
function CreateApiKeyDialog({ applicationId, onDismiss, onCreated, returnFocusRef }: { applicationId: string; onDismiss: () => void; onCreated: (key: ApiKeyMetadata, raw: string) => void; returnFocusRef: React.RefObject<HTMLElement | null> }) {
  const inputRef = useRef<HTMLInputElement>(null); const busyRef = useRef(false); const [name, setName] = useState(''); const [error, setError] = useState<string | null>(null); const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); const value = name.trim(); if (busyRef.current) return; if (!value || value.length > 120) { setError('Enter a name of 120 characters or fewer.'); return } busyRef.current = true; setBusy(true); try { const created = await dashboardApi.createApiKey(applicationId, { name: value }); const { apiKey, ...metadata } = created; onCreated(metadata, apiKey) } catch (e) { setError(e instanceof ApiError && e.status === 400 ? 'Check the key name and try again.' : 'We could not create the API key. Try again.') } finally { busyRef.current = false; setBusy(false) } }
  return <Dialog title="Create API key" onDismiss={busy ? () => undefined : onDismiss} returnFocusRef={returnFocusRef} initialFocusRef={inputRef}><form className="application-form" onSubmit={e => void submit(e)} noValidate><label className="form-field" htmlFor="api-key-name"><span>Key name</span><input ref={inputRef} id="api-key-name" value={name} placeholder="Production producer" onChange={e => { setName(e.target.value); setError(null) }} aria-invalid={Boolean(error)} /></label>{error && <p className="form-error" role="alert">{error}</p>}<div className="application-form-actions"><Button variant="secondary" disabled={busy} onClick={onDismiss}>Cancel</Button><Button type="submit" disabled={busy}>{busy ? 'Creating…' : 'Create key'}</Button></div></form></Dialog>
}
