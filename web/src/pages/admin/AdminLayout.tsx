import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { BadgeCheck, ExternalLink, LayoutDashboard, LogOut, ShieldCheck, UserCog, Wallet } from 'lucide-react'
import { adminScopeYears } from '../../mock/data'
import { useAdmin } from '../../lib/adminStore'
import { useApp } from '../../lib/store'
import { LangToggle } from '../../components/Layout'
import { cx } from '../../components/ui'

const NAV = [
  { to: '/admin', key: 'admin.overview', icon: LayoutDashboard, end: true, superOnly: false },
  { to: '/admin/members', key: 'admin.members', icon: BadgeCheck, end: false, superOnly: false },
  { to: '/admin/payments', key: 'admin.payments', icon: Wallet, end: false, superOnly: false },
  { to: '/admin/accounts', key: 'admin.accounts', icon: UserCog, end: false, superOnly: true },
] as const

export default function AdminLayout() {
  const { t, lang, yr } = useApp()
  const { admin, isSuper, signOut } = useAdmin()
  const navigate = useNavigate()

  const scope = admin ? adminScopeYears(admin) : null
  const nav = NAV.filter((item) => !item.superOnly || isSuper)

  return (
    <div className={cx('min-h-dvh bg-paper', lang === 'bn' && 'font-bn')}>
      {/* Dark chrome, on purpose — an admin should never mistake this for the member site. */}
      <header className="sticky top-0 z-30 bg-ink-900 text-white">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2.5">
          {/* School crest, with a gold shield badged onto it — same institution,
              unmistakably the staff side of it. */}
          <span className="relative shrink-0">
            <img
              src="/school-logo.png"
              alt=""
              className="size-10 rounded-full bg-white object-contain p-px ring-1 ring-white/25"
            />
            <span className="absolute -bottom-0.5 -right-0.5 grid size-5 place-items-center rounded-full bg-gold-400 text-ink-900 ring-2 ring-ink-900">
              <ShieldCheck className="size-3.5" />
            </span>
          </span>
          <div className="min-w-0 flex-1 leading-tight">
            <div className="truncate font-bold">{t('admin.portal')}</div>
            <div className="truncate text-xs text-white/55">
              {admin && (lang === 'bn' ? admin.nameBn : admin.name)} ·{' '}
              {admin && t(`admin.role${admin.role}` as never)}
              {scope && ` · ${yr(scope.from)}–${yr(scope.to)}`}
              {!scope && ` · ${t('admin.scopeAll')}`}
            </div>
          </div>

          <LangToggle dark />

          <button
            onClick={() => navigate('/')}
            title={t('admin.backToSite')}
            className="hidden size-10 place-items-center rounded-xl text-white/70 hover:bg-white/10 sm:grid"
          >
            <ExternalLink className="size-5" />
          </button>
          <button
            onClick={async () => {
              await signOut()
              navigate('/admin/login')
            }}
            title={t('admin.signOut')}
            className="grid size-10 shrink-0 place-items-center rounded-xl text-white/70 hover:bg-white/10"
          >
            <LogOut className="size-5" />
          </button>
        </div>

        {/* Tabs — flat and always visible, so nothing is buried in a menu */}
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-2 no-scrollbar">
          {nav.map(({ to, key, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cx(
                  'flex shrink-0 items-center gap-2 border-b-[3px] px-3.5 py-2.5 text-sm font-semibold transition',
                  isActive ? 'border-gold-400 text-gold-300' : 'border-transparent text-white/60 hover:text-white',
                )
              }
            >
              <Icon className="size-5" />
              {t(key)}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
