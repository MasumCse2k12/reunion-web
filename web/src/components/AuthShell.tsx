import { Link } from 'react-router-dom'
import { ArrowLeft, Phone } from 'lucide-react'
import { useApp } from '../lib/store'
import { cx } from './ui'
import { LangToggle, SchoolMark } from './Layout'
import { CONTACT_PHONE } from '../lib/api'

export default function AuthShell({
  children,
  onBack,
}: {
  children: React.ReactNode
  onBack?: () => void
}) {
  const { t, lang } = useApp()
  return (
    <div className={cx('flex min-h-dvh flex-col bg-paper', lang === 'bn' && 'font-bn')}>
      <header className="border-b border-paper-2 bg-white">
        <div className="mx-auto flex max-w-2xl items-center gap-3 px-4 py-3">
          {onBack ? (
            <button onClick={onBack} className="grid size-10 shrink-0 place-items-center rounded-xl hover:bg-paper-2">
              <ArrowLeft className="size-6 text-ink-600" />
            </button>
          ) : (
            <Link to="/" className="grid size-10 shrink-0 place-items-center rounded-xl hover:bg-paper-2">
              <ArrowLeft className="size-6 text-ink-600" />
            </Link>
          )}
          <div className="min-w-0 flex-1">
            <SchoolMark />
          </div>
          <LangToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-lg flex-1 px-4 py-8">{children}</main>

      <footer className="pb-8 text-center">
        <a
          href={`tel:+880${CONTACT_PHONE.replace(/^0/, '')}`}
          className="inline-flex items-center gap-2 rounded-full border-2 border-brand-200 bg-white px-5 py-2.5 font-semibold text-brand-700"
        >
          <Phone className="size-5" />
          {t('auth.needHelp')}: {CONTACT_PHONE}
        </a>
      </footer>
    </div>
  )
}
