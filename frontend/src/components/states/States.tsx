import type { ReactNode } from 'react'
export function LoadingState({ label = 'Loading data' }: { label?: string }) { return <div className="loading-block" aria-live="polite">{label}…</div> }
export function EmptyState({ title, detail }: { title: string; detail: string }) { return <section className="panel empty-state"><h2>{title}</h2><p>{detail}</p></section> }
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) { return <section className="panel error-state" role="alert"><h2>Unable to load this data</h2><p>{message}</p>{onRetry && <button type="button" onClick={onRetry}>Try again</button>}</section> }
export function NotFoundState({ title, children }: { title: string; children?: ReactNode }) { return <section className="panel empty-state"><h1>{title}</h1><p>This resource does not exist or is not available to your account.</p>{children}</section> }
