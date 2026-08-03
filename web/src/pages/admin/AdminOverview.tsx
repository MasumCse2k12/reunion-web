import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, BadgeCheck, Clock, Layers, Wallet, XCircle } from 'lucide-react'
import { adminApi, type AdminStats, type Application } from '../../lib/api'
import { useAdmin } from '../../lib/adminStore'
import { batchScope } from '../../lib/scope'
import { useApp } from '../../lib/store'
import { Avatar, Badge, Card, SectionTitle, Spinner } from '../../components/ui'

export default function AdminOverview() {
  const { t, lang, n, yr, money } = useApp()
  const { admin } = useAdmin()
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [recent, setRecent] = useState<Application[]>([])

  useEffect(() => {
    adminApi.stats().then(setStats)
    // Ask for the six it shows, rather than every pending row in scope.
    adminApi.applications({ memberStatus: 'PENDING', limit: 6 }).then((page) => setRecent(page.items))
  }, [])

  if (!stats || !admin) return <Spinner label={t('common.loading')} />

  const scope = batchScope(admin.role, admin.batches)

  /**
   * The header names the batches this page is counting. A group admin with
   * nothing assigned is told so — it used to fall through to "All batches",
   * which is the opposite of the truth and the reason empty tiles looked like
   * a data problem rather than a missing assignment.
   */
  const scopeLabel =
    scope.kind === 'ALL'
      ? `${t('admin.scopeAll')} · ${n(stats.batchesCovered)}`
      : scope.kind === 'NONE'
        ? t('admin.scopeNone')
        : scope.kind === 'RANGE'
          ? `${t('admin.scopeBatches')} ${yr(scope.from)}–${yr(scope.to)} · ${n(scope.count)}`
          : `${t('admin.scopeBatches')} ${scope.years.map(yr).join(', ')} · ${n(scope.count)}`

  const tiles = [
    { key: 'admin.pendingMembers', value: n(stats.pendingMembers), icon: Clock, tone: 'gold', to: '/admin/members' },
    { key: 'admin.pendingPayments', value: n(stats.pendingPayments), icon: Wallet, tone: 'gold', to: '/admin/payments' },
    { key: 'admin.approvedMembers', value: n(stats.approvedMembers), icon: BadgeCheck, tone: 'green', to: '/admin/members' },
    { key: 'admin.rejectedMembers', value: n(stats.rejectedMembers), icon: XCircle, tone: 'red', to: '/admin/members' },
  ] as const

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-extrabold text-ink-900">{t('admin.overview')}</h1>
        <p className="mt-1 inline-flex items-center gap-1.5 text-ink-500">
          <Layers className="size-4" />
          {scopeLabel}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {tiles.map(({ key, value, icon: Icon, tone, to }) => (
          <Link key={key} to={to}>
            <Card className="h-full transition hover:border-brand-300 hover:shadow-md">
              <Icon
                className={
                  tone === 'gold'
                    ? 'size-6 text-gold-600'
                    : tone === 'green'
                      ? 'size-6 text-brand-600'
                      : 'size-6 text-red-600'
                }
              />
              <div className="mt-2 text-3xl font-extrabold tabular-nums text-ink-900">{value}</div>
              <div className="text-sm text-ink-500">{t(key)}</div>
            </Card>
          </Link>
        ))}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Card className="bg-brand-700 text-white">
          <div className="text-sm text-white/70">{t('admin.confirmedAmount')}</div>
          <div className="mt-1 text-3xl font-extrabold tabular-nums text-gold-200">{money(stats.confirmedAmount)}</div>
        </Card>
        <Card className="bg-ink-900 text-white">
          <div className="text-sm text-white/70">{t('admin.outstandingAmount')}</div>
          <div className="mt-1 text-3xl font-extrabold tabular-nums text-gold-300">{money(stats.outstandingAmount)}</div>
        </Card>
      </div>

      <section>
        <SectionTitle
          action={
            <Link to="/admin/members" className="inline-flex items-center gap-1 font-semibold text-brand-700 underline underline-offset-4">
              {t('cta.viewAll')} <ArrowRight className="size-4" />
            </Link>
          }
        >
          {t('admin.pendingMembers')}
        </SectionTitle>

        {recent.length === 0 ? (
          <Card className="border-dashed py-8 text-center text-ink-500">{t('admin.noResults')}</Card>
        ) : (
          <div className="space-y-2">
            {recent.map((a) => (
              <Card key={a.id} className="flex items-center gap-3 p-3">
                <Avatar name={a.name} />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-bold text-ink-900">{a.nameBn || a.name}</div>
                  <div className="truncate text-sm text-ink-400">
                    {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(a.batchYear)} · {n(a.phone)}
                  </div>
                </div>
                <Badge tone="gold">{money(a.amountDue)}</Badge>
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
