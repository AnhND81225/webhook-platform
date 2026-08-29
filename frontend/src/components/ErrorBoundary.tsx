import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { hasError: boolean }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Dashboard render failed', error, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="centered-page" role="alert">
          <section className="panel">
            <p className="eyebrow">Application error</p>
            <h1>Something went wrong</h1>
            <p>Reload the page to try again.</p>
            <button type="button" onClick={() => window.location.reload()}>
              Reload
            </button>
          </section>
        </main>
      )
    }

    return this.props.children
  }
}

