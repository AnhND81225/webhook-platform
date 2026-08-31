import { Link } from 'react-router-dom'

const eventExample = `POST /api/v1/events

{
  "sourceEventId": "evt_123abc",
  "eventType": "order.completed",
  "payload": {
    "orderId": "ord_9988",
    "amount": 1500,
    "currency": "USD"
  }
}`

const curlExample = `curl -X POST https://api.example.com/api/v1/events \\
  -H "Authorization: Bearer whk_live_example" \\
  -H "Content-Type: application/json" \\
  -d '{
    "sourceEventId": "evt_order_123",
    "eventType": "order.completed",
    "payload": {
      "orderId": "ord_9988",
      "status": "paid"
    }
  }'`

const features = [
  ['Reliable delivery', 'Durable delivery records provide at-least-once responsibilities for every matching endpoint.'],
  ['Automatic retries', 'Retry scheduling and attempt history make transient delivery failures visible and recoverable.'],
  ['Secure by default', 'HMAC-SHA256 signing and SSRF, DNS, and TLS protections are part of outbound delivery.'],
  ['Full observability', 'Inspect immutable events, delivery state, retries, and attempt outcomes in one dashboard.'],
]

const steps = [
  ['Create an application', 'Create an application and its producer API key.'],
  ['Register an endpoint', 'Configure a webhook endpoint and event subscriptions.'],
  ['Send events', 'Post immutable events with the application API key.'],
  ['Observe delivery', 'Inspect deliveries, retries, failures, and attempts.'],
]

function Brand() {
  return <span className="public-brand">Webhook Platform</span>
}

export function LandingPage() {
  return (
    <div className="landing-page">
      <header className="public-header">
        <nav className="public-nav" aria-label="Public navigation">
          <Link to="/" aria-label="Webhook Platform home"><Brand /></Link>
          <div className="public-nav-links">
            <a href="#features">Platform</a>
            <a href="#api">Documentation</a>
          </div>
          <div className="public-nav-actions">
            <Link className="public-login" to="/login">Log in</Link>
            <Link className="button public-primary" to="/login">Get started</Link>
          </div>
        </nav>
      </header>

      <main>
        <section className="landing-hero" aria-labelledby="landing-title">
          <div className="hero-copy">
            <p className="landing-kicker">Webhook delivery infrastructure</p>
            <h1 id="landing-title">Reliable webhook delivery for modern applications.</h1>
            <p className="hero-description">Ingest events, fan out to subscriptions, and inspect durable signed deliveries with retries.</p>
            <div className="hero-actions">
              <Link className="button public-primary" to="/login">Get started</Link>
              <a className="button public-secondary" href="#api">Read the API</a>
            </div>
          </div>
          <section className="hero-preview" aria-label="Webhook event request example">
            <div className="code-heading"><span>event request</span><strong>201 Created</strong></div>
            <pre><code>{eventExample}</code></pre>
            <div className="code-result"><span>subscription fan-out</span><strong>durable delivery</strong></div>
          </section>
        </section>

        <section id="features" className="landing-section feature-section" aria-labelledby="features-title">
          <div className="section-intro">
            <p className="landing-kicker">Platform</p>
            <h2 id="features-title">The delivery path is built in.</h2>
            <p>Keep producer integration small while the platform retains delivery responsibility and operational context.</p>
          </div>
          <div className="feature-grid">
            {features.map(([title, description], index) => (
              <article className={`feature-card feature-card-${index + 1}`} key={title}>
                <span className="feature-index" aria-hidden="true">0{index + 1}</span>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="api" className="landing-section api-section" aria-labelledby="api-title">
          <div className="api-code">
            <div className="code-heading"><span>producer integration</span><strong>Bearer API key</strong></div>
            <pre><code>{curlExample}</code></pre>
          </div>
          <div className="api-copy">
            <p className="landing-kicker">Developer API</p>
            <h2 id="api-title">One request starts a durable delivery path.</h2>
            <p>The producer sends an immutable event. Matching subscriptions create durable deliveries, then the worker signs, sends, retries, and records each attempt.</p>
            <a className="text-link public-doc-link" href="#how-it-works">See how delivery works</a>
          </div>
        </section>

        <section id="how-it-works" className="landing-section process-section" aria-labelledby="process-title">
          <div className="section-intro">
            <h2 id="process-title">From event to delivery.</h2>
            <p>Use the platform lifecycle to separate producer code from delivery operations.</p>
          </div>
          <ol className="process-list">
            {steps.map(([title, description], index) => (
              <li key={title}>
                <span aria-hidden="true">{index + 1}</span>
                <h3>{title}</h3>
                <p>{description}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="landing-cta" aria-labelledby="cta-title">
          <div>
            <h2 id="cta-title">Ready to build reliable webhook delivery?</h2>
            <p>Start with an application, an API key, and the endpoints that need your events.</p>
          </div>
          <Link className="button public-primary" to="/login">Get started</Link>
        </section>
      </main>

      <footer className="public-footer">
        <div><Brand /><p>Developer infrastructure for durable webhook delivery.</p></div>
        <div className="footer-links"><a href="#features">Platform</a><Link to="/app">Dashboard</Link><a href="#api">Documentation</a></div>
        <p>© {new Date().getFullYear()} Webhook Platform</p>
      </footer>
    </div>
  )
}
