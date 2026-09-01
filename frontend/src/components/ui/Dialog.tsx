import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react'

type DialogProps = {
  title: string
  children: ReactNode
  onDismiss: () => void
  returnFocusRef?: RefObject<HTMLElement | null>
  initialFocusRef?: RefObject<HTMLElement | null>
}

export function Dialog({ title, children, onDismiss, returnFocusRef, initialFocusRef }: DialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const titleId = useId()

  useEffect(() => {
    ;(initialFocusRef?.current ?? dialogRef.current)?.focus()
    return () => returnFocusRef?.current?.focus()
  }, [initialFocusRef, returnFocusRef])

  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onDismiss()
      return
    }
    if (event.key !== 'Tab') return
    const focusable = dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])')
    if (!focusable?.length) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="ui-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onDismiss() }}>
      <div className="ui-dialog" ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={titleId} onKeyDown={handleKeyDown}>
        <div className="ui-dialog-header"><h2 id={titleId}>{title}</h2></div>
        {children}
      </div>
    </div>
  )
}
