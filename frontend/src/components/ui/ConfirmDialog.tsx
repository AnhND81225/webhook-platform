import { useRef, useState, type RefObject } from 'react'
import { Button } from './Button'
import { Dialog } from './Dialog'

type Props = { title: string; description: string; confirmLabel: string; onConfirm: () => Promise<void>; onDismiss: () => void; returnFocusRef?: RefObject<HTMLElement | null> }
export function ConfirmDialog({ title, description, confirmLabel, onConfirm, onDismiss, returnFocusRef }: Props) {
  const confirmRef = useRef<HTMLButtonElement>(null)
  const inFlight = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function confirm() { if (inFlight.current) return; inFlight.current = true; setBusy(true); setError(null); try { await onConfirm(); onDismiss() } catch { setError('We could not complete that action. Try again.') } finally { inFlight.current = false; setBusy(false) } }
  return <Dialog title={title} onDismiss={busy ? () => undefined : onDismiss} returnFocusRef={returnFocusRef} initialFocusRef={confirmRef}>
    <div className="confirm-dialog"><p>{description}</p>{error && <p className="form-error" role="alert">{error}</p>}<div className="application-form-actions"><Button variant="secondary" disabled={busy} onClick={onDismiss}>Cancel</Button><Button ref={confirmRef} disabled={busy} onClick={() => void confirm()}>{busy ? 'Working…' : confirmLabel}</Button></div></div>
  </Dialog>
}
