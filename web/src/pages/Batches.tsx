import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Search, TrendingUp } from 'lucide-react'
import { BATCHES } from '../lib/api'
import { useApp } from '../lib/store'
import { Badge, Card, Input, cx } from '../components/ui'

export default function Batches() {
  const { t, n, yr, lang, user } = useApp()
  const [q, setQ] = useState('')

  const list = useMemo(() => {
    const s = q.replace(/\D/g, '')
    return s ? BATCHES.filter((b) => String(b.year).includes(s)) : BATCHES
  }, [q])

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-extrabold text-ink-900">{t('batches.title')}</h1>
        <p className="mt-1 text-ink-500">{t('batches.sub')}</p>
      </div>

      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-ink-400" />
        <Input
          placeholder={lang === 'bn' ? 'সাল দিয়ে খুঁজুন…' : 'Search by year…'}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          className="pl-11"
          inputMode="numeric"
        />
      </div>

      <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((b) => {
          const pct = Math.round((b.claimedCount / b.rosterCount) * 100)
          const mine = user?.batchYear === b.year
          return (
            <Link key={b.year} to={`/app/batches/${b.year}`}>
              <Card
                className={cx(
                  'transition hover:-translate-y-0.5 hover:shadow-md',
                  mine && 'border-brand-400 bg-brand-50/60 ring-1 ring-brand-300',
                )}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="text-2xl font-extrabold tabular-nums text-ink-900">{yr(b.year)}</div>
                    <div className="text-sm text-ink-400">
                      {n(b.rosterCount)} {t('batches.members')}
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    {mine && (
                      <Badge tone="green">
                        <TrendingUp className="size-3.5" />
                        {lang === 'bn' ? 'আপনার ব্যাচ' : 'Your batch'}
                      </Badge>
                    )}
                    <Badge tone={pct > 50 ? 'green' : pct > 25 ? 'neutral' : 'gold'}>{n(pct)}%</Badge>
                  </div>
                </div>

                <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-paper-2">
                  <div
                    className={cx('h-full rounded-full transition-all duration-700', pct > 50 ? 'bg-brand-500' : 'bg-gold-400')}
                    style={{ width: `${pct}%` }}
                  />
                </div>

                <div className="mt-2 flex justify-between text-sm">
                  <span className="font-semibold text-brand-700">
                    {n(b.claimedCount)} {t('batches.found')}
                  </span>
                  <span className="text-ink-400">
                    {n(b.rosterCount - b.claimedCount)} {t('batches.missing')}
                  </span>
                </div>
              </Card>
            </Link>
          )
        })}
      </div>
    </div>
  )
}
