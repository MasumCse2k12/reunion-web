import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Briefcase, MapPin, Search } from 'lucide-react'
import { api, type Batch, type Person } from '../lib/api'
import { useApp } from '../lib/store'
import { Avatar, Badge, Card, Input, Spinner, cx } from '../components/ui'

type Tab = 'found' | 'missing' | 'memorial'

export default function BatchDetail() {
  const { year } = useParams()
  const { t, lang, n, yr } = useApp()
  const [data, setData] = useState<{ batch: Batch; members: Person[] } | null>(null)
  const [tab, setTab] = useState<Tab>('found')
  const [q, setQ] = useState('')

  useEffect(() => {
    setData(null)
    api.batch(Number(year)).then(setData)
  }, [year])

  const groups = useMemo(() => {
    if (!data) return { found: [], missing: [], memorial: [] }
    const s = q.trim().toLowerCase()
    const match = (p: Person) => !s || p.name.toLowerCase().includes(s) || p.nameBn.includes(q.trim())
    return {
      found: data.members.filter((p) => p.status === 'CLAIMED' && match(p)),
      missing: data.members.filter((p) => p.status === 'SEEDED' && !p.deceased && match(p)),
      memorial: data.members.filter((p) => p.deceased && match(p)),
    }
  }, [data, q])

  if (!data) return <Spinner label={t('common.loading')} />

  const pct = Math.round((data.batch.claimedCount / data.batch.rosterCount) * 100)
  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: 'found', label: t('batches.found'), count: groups.found.length },
    { key: 'missing', label: t('batches.missing'), count: groups.missing.length },
    { key: 'memorial', label: t('batches.deceased'), count: groups.memorial.length },
  ]

  return (
    <div className="space-y-5">
      <Link to="/app/batches" className="inline-flex items-center gap-1.5 font-semibold text-brand-700">
        <ArrowLeft className="size-5" />
        {t('batches.title')}
      </Link>

      <Card className="bg-gradient-to-br from-brand-700 to-brand-900 text-white">
        <div className="flex items-end justify-between gap-3">
          <div>
            <p className="font-semibold text-gold-300">{lang === 'bn' ? 'এসএসসি ব্যাচ' : 'SSC Batch'}</p>
            <h1 className="text-4xl font-extrabold tabular-nums">{yr(data.batch.year)}</h1>
          </div>
          <div className="text-right">
            <div className="text-3xl font-extrabold tabular-nums text-gold-200">
              {n(data.batch.claimedCount)}
              <span className="text-lg text-white/50">/{n(data.batch.rosterCount)}</span>
            </div>
            <p className="text-sm text-white/60">{t('batches.found')}</p>
          </div>
        </div>
        <div className="mt-4 h-3 overflow-hidden rounded-full bg-white/15">
          <div className="h-full rounded-full bg-gold-400 transition-all duration-700" style={{ width: `${pct}%` }} />
        </div>
      </Card>

      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-ink-400" />
        <Input
          placeholder={t('signup.searchName')}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          className="pl-11"
        />
      </div>

      <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4">
        {tabs.map((x) => (
          <button
            key={x.key}
            onClick={() => setTab(x.key)}
            className={cx(
              'shrink-0 rounded-full px-4 py-2 font-semibold transition',
              tab === x.key ? 'bg-brand-600 text-white' : 'bg-white text-ink-500 ring-1 ring-paper-2',
            )}
          >
            {x.label} <span className="tabular-nums opacity-70">({n(x.count)})</span>
          </button>
        ))}
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        {groups[tab].map((p) => (
          <Card key={p.id} className={cx('flex items-center gap-3 p-3', p.deceased && 'bg-paper-2/60')}>
            <Avatar name={p.name} photoUrl={p.photoUrl} />
            <div className="min-w-0 flex-1">
              <div className="truncate font-bold text-ink-900">
                {p.deceased && <span className="text-ink-400">{t('batches.deceased')} </span>}
                {lang === 'bn' ? p.nameBn : p.name}
              </div>
              {p.status === 'CLAIMED' ? (
                <div className="flex flex-wrap gap-x-3 text-sm text-ink-400">
                  {p.occupation && (
                    <span className="inline-flex items-center gap-1">
                      <Briefcase className="size-3.5" />
                      {p.occupation}
                    </span>
                  )}
                  {p.city && (
                    <span className="inline-flex items-center gap-1">
                      <MapPin className="size-3.5" />
                      {p.city}
                    </span>
                  )}
                </div>
              ) : (
                <div className="text-sm text-ink-400">
                  {p.deceased
                    ? lang === 'bn'
                      ? 'আল্লাহ তাঁকে জান্নাতবাসী করুন'
                      : 'Remembered by the batch'
                    : lang === 'bn'
                      ? 'যোগাযোগ করা যায়নি'
                      : 'Not yet reached'}
                </div>
              )}
            </div>
            {p.status === 'CLAIMED' && <Badge tone="green">✓</Badge>}
          </Card>
        ))}

        {groups[tab].length === 0 && (
          <p className="col-span-full py-10 text-center text-ink-400">
            {lang === 'bn' ? 'কেউ নেই' : 'Nothing here'}
          </p>
        )}
      </div>
    </div>
  )
}
