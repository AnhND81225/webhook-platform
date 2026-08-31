import { Button } from '../ui/Button'

export function ApplicationOnboarding({ onCreate }: { onCreate: (trigger?: HTMLElement) => void }) {
  return (
    <section className="application-onboarding">
      <span className="eyebrow">Webhook Platform</span>
      <h1>Create your first application</h1>
      <p>Applications group events, endpoints, API keys, and delivery data for a producer.</p>
      <Button onClick={(event) => onCreate(event.currentTarget)}>Create application</Button>
    </section>
  )
}
