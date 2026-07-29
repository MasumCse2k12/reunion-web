import { type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'
import { Check, Loader2, Minus, X } from 'lucide-react'

export function cx(...parts: (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(' ')
}

/* ---------------- Button ---------------- */

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'gold' | 'ghost' | 'outline' | 'danger'
  size?: 'md' | 'lg' | 'sm'
  loading?: boolean
  full?: boolean
  icon?: ReactNode
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  full,
  icon,
  className,
  children,
  disabled,
  ...rest
}: BtnProps) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition active:scale-[.98] disabled:opacity-50 disabled:pointer-events-none'
  const sizes = {
    sm: 'px-3.5 py-2 text-sm',
    md: 'px-5 py-3 text-base',
    lg: 'px-6 py-4 text-lg',
  }
  const variants = {
    primary: 'bg-brand-600 text-white hover:bg-brand-700 shadow-sm shadow-brand-900/20',
    gold: 'bg-gold-400 text-ink-900 hover:bg-gold-300 shadow-sm shadow-gold-700/20',
    ghost: 'text-brand-700 hover:bg-brand-50',
    outline: 'border-2 border-brand-200 text-brand-700 hover:bg-brand-50 bg-white',
    danger: 'text-red-700 hover:bg-red-50 border-2 border-red-200 bg-white',
  }
  return (
    <button
      className={cx(base, sizes[size], variants[variant], full && 'w-full', className)}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Loader2 className="size-5 animate-spin" /> : icon}
      {children}
    </button>
  )
}

/* ---------------- Form fields ---------------- */

export function Field({
  label,
  hint,
  error,
  children,
  required,
}: {
  label: string
  hint?: string
  error?: string
  children: ReactNode
  required?: boolean
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block font-semibold text-ink-700">
        {label}
        {required && <span className="ml-1 text-red-600">*</span>}
      </span>
      {children}
      {hint && !error && <span className="mt-1 block text-sm text-ink-400">{hint}</span>}
      {error && <span className="mt-1 block text-sm font-medium text-red-600">{error}</span>}
    </label>
  )
}

const fieldBase =
  'w-full rounded-xl border-2 border-paper-2 bg-white px-4 py-3 text-ink-900 placeholder:text-ink-400 transition focus:border-brand-400 focus:outline-none'

export function Input({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cx(fieldBase, className)} {...rest} />
}

/**
 * A checkbox drawn as a button rather than a styled `<input>`. The tap target is
 * the full 44px square Apple and Android both ask for — these get tapped by
 * volunteers on phones, working down a list of forty rows.
 * `indeterminate` renders the "some, not all" dash for a select-all header.
 */
export function Checkbox({
  checked,
  indeterminate,
  onChange,
  label,
  className,
}: {
  checked: boolean
  indeterminate?: boolean
  onChange: (next: boolean) => void
  /** Accessible name. Rendered visibly when given as a string in a label slot. */
  label: string
  className?: string
}) {
  const on = checked || indeterminate
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={indeterminate ? 'mixed' : checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cx('grid size-11 shrink-0 place-items-center rounded-xl hover:bg-paper-2', className)}
    >
      <span
        className={cx(
          'grid size-6 place-items-center rounded-md border-2 transition',
          on ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-400/50 bg-white',
        )}
      >
        {indeterminate ? <Minus className="size-4" /> : checked ? <Check className="size-4" /> : null}
      </span>
    </button>
  )
}

export function Select({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cx(fieldBase, 'appearance-none pr-10', className)} {...rest}>
      {children}
    </select>
  )
}

/* ---------------- Surfaces ---------------- */

export function Card({
  children,
  className,
  as: As = 'div',
}: {
  children: ReactNode
  className?: string
  as?: 'div' | 'section' | 'article'
}) {
  // Tailwind utilities have equal specificity, so a `bg-*` passed in className
  // does not reliably beat a hardcoded `bg-white` — CSS source order decides.
  // Only apply the default background when the caller has not supplied one.
  const hasOwnBg = /(^|\s)bg-/.test(className ?? '')
  return (
    <As
      className={cx(
        'rounded-2xl border border-paper-2 p-5 shadow-sm shadow-ink-900/[0.03]',
        !hasOwnBg && 'bg-white',
        className,
      )}
    >
      {children}
    </As>
  )
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-end justify-between gap-3">
      <h2 className="text-xl font-bold text-ink-900">{children}</h2>
      {action}
    </div>
  )
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode
  tone?: 'neutral' | 'green' | 'gold' | 'red' | 'muted'
}) {
  const tones = {
    neutral: 'bg-paper-2 text-ink-600',
    green: 'bg-brand-50 text-brand-700 ring-1 ring-brand-200',
    gold: 'bg-gold-50 text-gold-700 ring-1 ring-gold-200',
    red: 'bg-red-50 text-red-700 ring-1 ring-red-200',
    muted: 'bg-paper-2 text-ink-400',
  }
  return (
    <span className={cx('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-sm font-semibold', tones[tone])}>
      {children}
    </span>
  )
}

/* ---------------- Avatar (initials — no network images) ---------------- */

const AV_COLORS = [
  'bg-brand-600',
  'bg-gold-600',
  'bg-emerald-700',
  'bg-teal-700',
  'bg-amber-700',
  'bg-lime-800',
  'bg-cyan-800',
  'bg-stone-600',
]

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' | 'xl' }) {
  const clean = name.replace(/^(Md\.?|Mst\.?|Late|মোঃ|মোছাঃ|মরহুম)\s*/i, '').trim()
  const initials = clean
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase()
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  const sizes = { sm: 'size-9 text-sm', md: 'size-12 text-base', lg: 'size-16 text-xl', xl: 'size-24 text-3xl' }
  return (
    <span
      className={cx(
        'inline-flex shrink-0 items-center justify-center rounded-full font-bold text-white',
        AV_COLORS[h % AV_COLORS.length],
        sizes[size],
      )}
      aria-hidden
    >
      {initials || '?'}
    </span>
  )
}

/* ---------------- Progress ring ---------------- */

export function Ring({ value, label }: { value: number; label?: string }) {
  const r = 26
  const c = 2 * Math.PI * r
  return (
    <div className="relative inline-flex items-center justify-center">
      <svg width="68" height="68" viewBox="0 0 68 68" className="-rotate-90">
        <circle cx="34" cy="34" r={r} fill="none" stroke="currentColor" strokeWidth="7" className="text-paper-2" />
        <circle
          cx="34"
          cy="34"
          r={r}
          fill="none"
          stroke="currentColor"
          strokeWidth="7"
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c - (c * value) / 100}
          className="text-brand-500 transition-all duration-700"
        />
      </svg>
      <span className="absolute text-sm font-bold text-ink-700">{label ?? `${value}%`}</span>
    </div>
  )
}

/* ---------------- Sheet / modal (bottom sheet on mobile) ---------------- */

export function Sheet({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-ink-900/45 backdrop-blur-[2px]" onClick={onClose} />
      <div className="animate-fade-up relative max-h-[92vh] w-full overflow-y-auto rounded-t-3xl bg-paper p-5 pb-8 shadow-2xl sm:max-w-lg sm:rounded-3xl sm:pb-5">
        <div className="mb-4 flex items-center justify-between gap-4">
          <h3 className="text-xl font-bold text-ink-900">{title}</h3>
          <button
            onClick={onClose}
            aria-label="Close"
            className="grid size-10 shrink-0 place-items-center rounded-full text-ink-500 hover:bg-paper-2"
          >
            <X className="size-6" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/* ---------------- Misc ---------------- */

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-400">
      <Loader2 className="size-6 animate-spin" />
      {label && <span>{label}</span>}
    </div>
  )
}

export function Stat({ value, label, tone }: { value: ReactNode; label: string; tone?: 'gold' }) {
  return (
    <div className="text-center">
      <div className={cx('text-2xl font-extrabold tabular-nums sm:text-3xl', tone === 'gold' ? 'text-gold-300' : 'text-white')}>
        {value}
      </div>
      <div className="mt-0.5 text-sm text-white/70">{label}</div>
    </div>
  )
}
