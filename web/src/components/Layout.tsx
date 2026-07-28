import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Home, LayoutDashboard, LogOut, Users, UserRound, Users2, RotateCcw } from 'lucide-react'
import { useApp } from '../lib/store'
import { api } from '../lib/api'
import { cx, Avatar } from './ui'
import { SCHOOL } from '../mock/data'

const NAV = [
  { to: '/app', key: 'nav.dashboard', icon: LayoutDashboard, end: true },
  { to: '/app/guests', key: 'nav.guests', icon: Users2, end: false },
  { to: '/app/batches', key: 'nav.batches', icon: Users, end: false },
  { to: '/app/profile', key: 'nav.profile', icon: UserRound, end: false },
] as const

export function LangToggle({ dark }: { dark?: boolean }) {
  const { lang, setLang } = useApp()
  return (
    <div
      className={cx(
        'inline-flex shrink-0 overflow-hidden rounded-full p-0.5 text-sm font-semibold',
        dark ? 'bg-white/15' : 'bg-paper-2',
      )}
    >
      {(['bn', 'en'] as const).map((l) => (
        <button
          key={l}
          onClick={() => setLang(l)}
          className={cx(
            'min-h-0 rounded-full px-3 py-1 transition',
            lang === l ? (dark ? 'bg-white text-brand-700' : 'bg-white text-brand-700 shadow-sm') : dark ? 'text-white/80' : 'text-ink-500',
          )}
        >
          {l === 'bn' ? 'বাংলা' : 'EN'}
        </button>
      ))}
    </div>
  )
}

export function DemoBanner() {
  const { t } = useApp()
  return (
    <div className="flex items-center justify-center gap-3 bg-gold-300 px-4 py-1.5 text-center text-sm font-semibold text-ink-900">
      <span>⚠︎ {t('demo.banner')}</span>
      <button
        onClick={() => {
          api.resetDemo()
          location.href = '/'
        }}
        className="inline-flex min-h-0 items-center gap-1 rounded-full bg-ink-900/10 px-2.5 py-0.5 text-xs hover:bg-ink-900/20"
      >
        <RotateCcw className="size-3.5" />
        {t('demo.reset')}
      </button>
    </div>
  )
}

export function SchoolMark({ dark }: { dark?: boolean }) {
  const { lang, yr } = useApp()
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      {/* The crest is a dark seal on white, so it always sits on a white disc —
          that reads as intentional on the light header and the dark hero alike. */}
      <img
        src="/school-logo.png"
        alt=""
        className={cx(
          'size-10 shrink-0 rounded-full bg-white object-contain p-px ring-1',
          dark ? 'ring-white/25' : 'ring-paper-2',
        )}
      />
      <span className="min-w-0 leading-tight">
        <span className={cx('block truncate font-bold', dark ? 'text-white' : 'text-ink-900')}>
          {lang === 'bn' ? SCHOOL.nameBn : SCHOOL.nameEn}
        </span>
        <span className={cx('block truncate text-xs', dark ? 'text-white/60' : 'text-ink-400')}>
          {lang === 'bn' ? SCHOOL.locationBn : SCHOOL.locationEn} · {yr(SCHOOL.established)}
        </span>
      </span>
    </div>
  )
}

export default function Layout() {
  const { t, user, logout, lang } = useApp()
  const navigate = useNavigate()

  return (
    <div className="min-h-dvh">
      <DemoBanner />

      {/* Top bar */}
      <header className="sticky top-0 z-30 border-b border-paper-2 bg-paper/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-2.5">
          <button onClick={() => navigate('/app')} className="min-h-0 min-w-0 flex-1 text-left">
            <SchoolMark />
          </button>

          {/* Desktop nav */}
          <nav className="hidden items-center gap-1 lg:flex">
            {NAV.map(({ to, key, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  cx(
                    'flex items-center gap-2 rounded-xl px-3.5 py-2 font-semibold transition',
                    isActive ? 'bg-brand-600 text-white' : 'text-ink-600 hover:bg-paper-2',
                  )
                }
              >
                <Icon className="size-5" />
                {t(key)}
              </NavLink>
            ))}
          </nav>

          <LangToggle />

          {user && (
            <div className="flex items-center gap-2">
              <span className="hidden sm:block">
                <Avatar name={user.name} size="sm" />
              </span>
              <button
                onClick={async () => {
                  await logout()
                  navigate('/')
                }}
                title={t('nav.logout')}
                className="grid size-10 place-items-center rounded-xl text-ink-500 hover:bg-paper-2"
              >
                <LogOut className="size-5" />
              </button>
            </div>
          )}
        </div>
      </header>

      <main className={cx('mx-auto max-w-6xl px-4 pb-28 pt-5 lg:pb-12', lang === 'bn' && 'font-bn')}>
        <Outlet />
      </main>

      {/* Mobile bottom nav — the "app" feel */}
      <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-paper-2 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden">
        <div className="mx-auto grid max-w-lg grid-cols-5">
          <NavLink
            to="/"
            className={({ isActive }) =>
              cx('flex flex-col items-center gap-0.5 py-2 text-xs font-semibold', isActive ? 'text-brand-600' : 'text-ink-400')
            }
          >
            <Home className="size-6" />
            {t('nav.home')}
          </NavLink>
          {NAV.map(({ to, key, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cx(
                  'flex flex-col items-center gap-0.5 py-2 text-xs font-semibold',
                  isActive ? 'text-brand-600' : 'text-ink-400',
                )
              }
            >
              <Icon className="size-6" />
              <span className="max-w-full truncate px-1">{t(key)}</span>
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
