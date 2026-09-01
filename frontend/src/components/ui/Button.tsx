import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode
  variant?: 'primary' | 'secondary' | 'quiet'
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ children, className = '', variant = 'primary', type = 'button', ...props }, ref) {
  return (
    <button
      {...props} ref={ref}
      type={type}
      className={`ui-button ui-button--${variant} ${className}`.trim()}
    >
      {children}
    </button>
  )
})
