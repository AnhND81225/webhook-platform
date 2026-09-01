import { useRef, useState, type FormEvent, type RefObject } from 'react'
import { ApiError, dashboardApi, type Application } from '../../api/dashboard-api'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const environments = ['DEVELOPMENT', 'PRODUCTION'] as const

function slugify(value: string) {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').replace(/-+/g, '-')
}

function normalizeSlugInput(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9-]+/g, '').replace(/-+/g, '-').replace(/^-+/, '')
}

type Props = {
  onCreated: (application: Application) => void
  onDismiss: () => void
  returnFocusRef?: RefObject<HTMLElement | null>
}

export function CreateApplicationDialog({ onCreated, onDismiss, returnFocusRef }: Props) {
  const nameRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [environment, setEnvironment] = useState<Application['environment']>('DEVELOPMENT')
  const [slugEdited, setSlugEdited] = useState(false)
  const [errors, setErrors] = useState<{ name?: string; slug?: string; form?: string }>({})
  const [submitting, setSubmitting] = useState(false)
  const submissionInFlight = useRef(false)

  function onNameChange(value: string) {
    setName(value)
    if (!slugEdited) setSlug(slugify(value))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submissionInFlight.current) return
    const nextErrors: typeof errors = {}
    const normalizedName = name.trim()
    const normalizedSlug = slug.trim()
    if (!normalizedName) nextErrors.name = 'Enter an application name.'
    else if (normalizedName.length > 120) nextErrors.name = 'Application names must be 120 characters or fewer.'
    if (!normalizedSlug) nextErrors.slug = 'Enter a slug.'
    else if (normalizedSlug.length > 63 || !slugPattern.test(normalizedSlug)) nextErrors.slug = 'Use lowercase letters, numbers, and single hyphens only.'
    if (!environments.includes(environment)) nextErrors.form = 'Select a valid environment.'
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors)
      return
    }

    submissionInFlight.current = true
    setSubmitting(true)
    setErrors({})
    try {
      const application = await dashboardApi.createApplication({ name: normalizedName, slug: normalizedSlug, environment })
      onCreated(application)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'APPLICATION_SLUG_CONFLICT') {
        setErrors({ slug: 'This slug is already used by one of your applications.' })
      } else if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
        setErrors({ form: 'Check the application details and try again.' })
      } else {
        setErrors({ form: 'We could not create the application. Try again.' })
      }
    } finally {
      submissionInFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <Dialog title="Create application" onDismiss={submitting ? () => undefined : onDismiss} returnFocusRef={returnFocusRef} initialFocusRef={nameRef}>
      <form className="application-form" onSubmit={(event) => void submit(event)} noValidate>
        <p className="application-form-intro">Applications group events, endpoints, API keys, and delivery data for a producer.</p>
        <label className="form-field" htmlFor="application-name">
          <span>Application name</span>
          <input ref={nameRef} id="application-name" name="name" value={name} placeholder="AI Study Assistant" onChange={(event) => onNameChange(event.target.value)} aria-invalid={Boolean(errors.name)} aria-describedby={errors.name ? 'application-name-error' : undefined} />
          {errors.name && <span className="form-error" id="application-name-error" role="alert">{errors.name}</span>}
        </label>
        <label className="form-field" htmlFor="application-slug">
          <span>Slug</span>
          <input id="application-slug" name="slug" value={slug} placeholder="ai-study-assistant" onChange={(event) => { setSlugEdited(true); setSlug(normalizeSlugInput(event.target.value)) }} aria-invalid={Boolean(errors.slug)} aria-describedby={errors.slug ? 'application-slug-helper application-slug-error' : 'application-slug-helper'} />
          <span className="form-helper" id="application-slug-helper">Lowercase letters, numbers, and single hyphens. Up to 63 characters.</span>
          {errors.slug && <span className="form-error" id="application-slug-error" role="alert">{errors.slug}</span>}
        </label>
        <label className="form-field" htmlFor="application-environment">
          <span>Environment</span>
          <select id="application-environment" name="environment" value={environment} onChange={(event) => setEnvironment(event.target.value as Application['environment'])}>
            <option value="DEVELOPMENT">Development</option>
            <option value="PRODUCTION">Production</option>
          </select>
        </label>
        {errors.form && <p className="form-error form-error--summary" role="alert">{errors.form}</p>}
        <div className="application-form-actions">
          <Button variant="secondary" type="button" disabled={submitting} onClick={onDismiss}>Cancel</Button>
          <Button type="submit" disabled={submitting}>{submitting ? 'Creating application…' : 'Create application'}</Button>
        </div>
      </form>
    </Dialog>
  )
}

export { slugify }
