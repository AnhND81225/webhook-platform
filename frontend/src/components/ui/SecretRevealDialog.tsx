import { useRef, useState, type RefObject } from 'react'
import { Button } from './Button'
import { Dialog } from './Dialog'

type Props = { title: string; secret: string; description: string; onDismiss: () => void; returnFocusRef?: RefObject<HTMLElement | null> }

export function SecretRevealDialog({ title, secret, description, onDismiss, returnFocusRef }: Props) {
  const doneRef = useRef<HTMLButtonElement>(null)
  const [copied, setCopied] = useState(false)
  async function copy() {
    try { await navigator.clipboard.writeText(secret); setCopied(true) } catch { setCopied(false) }
  }
  return <Dialog title={title} onDismiss={onDismiss} returnFocusRef={returnFocusRef} initialFocusRef={doneRef}>
    <div className="secret-reveal">
      <p>{description}</p>
      <code className="secret-value">{secret}</code>
      <p className="form-helper">Store this value now. It will not be shown again.</p>
      <div className="application-form-actions">
        <Button variant="secondary" onClick={() => void copy()}>{copied ? 'Copied' : 'Copy'}</Button>
        <Button ref={doneRef} onClick={onDismiss}>Done</Button>
      </div>
    </div>
  </Dialog>
}
