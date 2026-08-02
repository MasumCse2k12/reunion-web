import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock,
  MapPin,
  Megaphone,
  Ticket,
  UserPlus,
  Users2,
} from 'lucide-react'
import { api, type DashboardData, type Person } from '../lib/api'
import { useApp } from '../lib/store'
import { Avatar, Badge, Button, Card, Field, Input, Ring, SectionTitle, Sheet, Spinner, cx } from '../components/ui'

export default function Dashboard() {
  const { t, lang, n, yr, money } = useApp()
  const navigate = useNavigate()
  const [data, setData] = useState<DashboardData | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [referTarget, setReferTarget] = useState<Person | null>(null)
  const [referPhone, setReferPhone] = useState('')
  const [referDone, setReferDone] = useState<string[]>([])
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.dashboard().then(setData).catch(() => setLoadError(true))
  }, [])

  if (loadError) return (
    <div className="flex flex-col items-center gap-4 py-20 text-center">
      <p className="text-ink-500">{lang === 'bn' ? 'তথ্য লোড করা যায়নি। ইন্টারনেট সংযোগ যাচাই করুন।' : 'Could not load your dashboard. Check your connection.'}</p>
      <button className="font-semibold text-brand-700 underline underline-offset-4" onClick={() => { setLoadError(false); api.dashboard().then(setData).catch(() => setLoadError(true)) }}>
        {lang === 'bn' ? 'আবার চেষ্টা করুন' : 'Try again'}
      </button>
    </div>
  )

  if (!data) return <Spinner label={t('common.loading')} />

  const { me, batch, event, registration, application, notices, missingFromBatch, profileCompleteness } = data
  const batchPct = Math.round((batch.claimedCount / batch.rosterCount) * 100)
  const guestCount = registration?.guests.length ?? 0

  async function submitReferral() {
    if (!referTarget) return
    setBusy(true)
    await api.addReferral({ name: referTarget.name, phone: referPhone, batchYear: referTarget.batchYear })
    setReferDone((d) => [...d, referTarget.id])
    setReferPhone('')
    setReferTarget(null)
    setBusy(false)
  }

  return (
    <div className="space-y-6">
      {/* ---------- Greeting ---------- */}
      <div className="flex items-center gap-4">
        <Avatar name={me.name} photoUrl={me.photoUrl} size="lg" />
        <div className="min-w-0">
          <p className="text-ink-500">{t('dash.greeting')},</p>
          <h1 className="truncate text-2xl font-extrabold text-ink-900">{me.nameBn || me.name}</h1>
          <Badge tone="green">
            {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(me.batchYear)}
          </Badge>
        </div>
      </div>

      {/* ---------- Membership verification — separate from the event ticket ---------- */}
      {application && (
        <Card
          className={cx(
            'flex items-start gap-3 py-3',
            application.memberStatus === 'APPROVED'
              ? 'border-brand-200 bg-brand-50'
              : application.memberStatus === 'REJECTED'
                ? 'border-red-200 bg-red-50'
                : 'border-gold-200 bg-gold-50',
          )}
        >
          {application.memberStatus === 'APPROVED' ? (
            <CheckCircle2 className="mt-0.5 size-6 shrink-0 text-brand-600" />
          ) : (
            <Clock className="mt-0.5 size-6 shrink-0 text-gold-600" />
          )}
          <div className="min-w-0">
            <p className="font-bold text-ink-900">
              {application.memberStatus === 'APPROVED'
                ? t('dash.registered')
                : application.memberStatus === 'REJECTED'
                  ? t('dash.rejected')
                  : t('dash.awaitingReview')}
            </p>
            <p className="text-sm text-ink-500">
              {application.memberReview?.note ?? t('guests.submittedSub')}
            </p>
          </div>
        </Card>
      )}

      {/* ---------- Event / registration card ---------- */}
      <Card className="relative overflow-hidden bg-gradient-to-br from-brand-700 to-brand-900 text-white">
        <div className="paper-grain absolute inset-0 opacity-40" />
        <div className="relative">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="font-semibold text-gold-300">{t('dash.eventCard')}</p>
              <h2 className="mt-0.5 text-2xl font-extrabold">{lang === 'bn' ? event.titleBn : event.titleEn}</h2>
            </div>
            {application?.memberStatus === 'APPROVED' ? (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-gold-400 px-3 py-1 font-bold text-ink-900">
                <CheckCircle2 className="size-4" />
                {t('dash.registered')}
              </span>
            ) : application?.memberStatus === 'REJECTED' ? (
              <span className="rounded-full bg-red-500/90 px-3 py-1 font-bold text-white">{t('dash.rejected')}</span>
            ) : application ? (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 font-bold text-gold-200 ring-1 ring-white/20">
                <Clock className="size-4" />
                {t('dash.awaitingReview')}
              </span>
            ) : registration ? (
              <span className="rounded-full bg-white/15 px-3 py-1 font-bold text-white/80 ring-1 ring-white/20">
                {t('dash.draft')}
              </span>
            ) : null}
          </div>

          <div className="mt-4 space-y-1.5 text-white/80">
            <div className="flex items-center gap-2">
              <CalendarDays className="size-5 shrink-0 text-gold-300" />
              {new Date(event.date).toLocaleDateString(lang === 'bn' ? 'bn-BD' : 'en-GB', {
                weekday: 'long',
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })}
            </div>
            <div className="flex items-center gap-2">
              <MapPin className="size-5 shrink-0 text-gold-300" />
              {lang === 'bn' ? event.venueBn : event.venueEn}
            </div>
          </div>

          <div className="mt-5 border-t border-white/15 pt-4">
            {!registration ? (
              <>
                <p className="mb-3 text-white/80">{t('dash.notRegistered')}</p>
                <Button variant="gold" size="lg" full icon={<Ticket className="size-5" />} onClick={() => navigate('/app/guests')}>
                  {t('dash.registerNow')}
                </Button>
              </>
            ) : (
              <div className="flex flex-wrap items-end justify-between gap-4">
                <div>
                  <p className="text-sm text-white/60">{t('dash.attending')}</p>
                  <p className="text-2xl font-extrabold text-gold-200">
                    {n(1 + guestCount)} {t('dash.people')}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-white/60">{t('dash.totalDue')}</p>
                  <p className="text-2xl font-extrabold text-gold-200 tabular-nums">{money(registration.amountDue)}</p>
                </div>
                <Button
                  variant="gold"
                  full
                  className="sm:w-auto"
                  icon={<Users2 className="size-5" />}
                  onClick={() => navigate('/app/guests')}
                >
                  {t('guests.title')}
                </Button>
              </div>
            )}
          </div>
        </div>
      </Card>

      {/* ---------- Batch + profile ---------- */}
      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <SectionTitle>{t('dash.yourBatch')}</SectionTitle>
          <div className="flex items-end justify-between gap-3">
            <div>
              <div className="text-3xl font-extrabold tabular-nums text-brand-700">
                {n(batch.claimedCount)}
                <span className="text-lg font-semibold text-ink-400">/{n(batch.rosterCount)}</span>
              </div>
              <p className="text-ink-500">
                {lang === 'bn' ? 'ব্যাচমেট খুঁজে পাওয়া গেছে' : 'batchmates found'}
              </p>
            </div>
            <Badge tone={batchPct > 50 ? 'green' : 'gold'}>{n(batchPct)}%</Badge>
          </div>
          <div className="mt-3 h-3 overflow-hidden rounded-full bg-paper-2">
            <div className="h-full rounded-full bg-brand-500 transition-all duration-700" style={{ width: `${batchPct}%` }} />
          </div>
          <Link
            to={`/app/batches/${batch.year}`}
            className="mt-3 inline-flex items-center gap-1 font-semibold text-brand-700 underline underline-offset-4"
          >
            {t('cta.viewAll')} <ArrowRight className="size-4" />
          </Link>
        </Card>

        <Card>
          <SectionTitle>{t('dash.profileComplete')}</SectionTitle>
          <div className="flex items-center gap-4">
            <Ring value={profileCompleteness} />
            <div className="min-w-0 flex-1">
              <p className="text-ink-500">
                {profileCompleteness < 100
                  ? lang === 'bn'
                    ? 'আর কয়েকটি তথ্য দিলেই সম্পূর্ণ হবে।'
                    : 'A few more details and it is complete.'
                  : lang === 'bn'
                    ? 'সম্পূর্ণ! ধন্যবাদ।'
                    : 'Complete. Thank you.'}
              </p>
              <Link
                to="/app/profile"
                className="mt-1.5 inline-flex items-center gap-1 font-semibold text-brand-700 underline underline-offset-4"
              >
                {t('profile.title')} <ArrowRight className="size-4" />
              </Link>
            </div>
          </div>
        </Card>
      </div>

      {/* ---------- Missing classmates — the referral loop ---------- */}
      <section>
        <SectionTitle>{t('dash.missingTitle')}</SectionTitle>
        <p className="mb-3 text-ink-500">{t('dash.missingSub')}</p>
        <div className="grid gap-2 sm:grid-cols-2">
          {missingFromBatch.map((p) => {
            const done = referDone.includes(p.id)
            return (
              <Card key={p.id} className={cx('flex items-center gap-3 p-3', done && 'border-brand-200 bg-brand-50/50')}>
                <Avatar name={p.name} photoUrl={p.photoUrl} />
                <div className="min-w-0 flex-1">
                  <div className="truncate font-bold text-ink-900">{p.nameBn || p.name}</div>
                  <div className="text-sm text-ink-400">
                    {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(p.batchYear)}
                  </div>
                </div>
                {done ? (
                  <Badge tone="green">
                    <CheckCircle2 className="size-4" />
                    {lang === 'bn' ? 'ধন্যবাদ' : 'Thanks'}
                  </Badge>
                ) : (
                  <Button size="sm" variant="outline" onClick={() => setReferTarget(p)}>
                    {t('dash.iKnowThem')}
                  </Button>
                )}
              </Card>
            )
          })}
        </div>
      </section>

      {/* ---------- Notices ---------- */}
      <section>
        <SectionTitle>
          <span className="inline-flex items-center gap-2">
            <Megaphone className="size-5 text-gold-600" />
            {t('dash.notices')}
          </span>
        </SectionTitle>
        <div className="space-y-3">
          {notices.map((notice) => (
            <Card key={notice.id} className={cx(notice.pinned && 'border-gold-300 bg-gold-50/40')}>
              <h3 className="font-bold text-ink-900">{lang === 'bn' ? notice.titleBn : notice.titleEn}</h3>
              <p className="mt-1 text-ink-500">{lang === 'bn' ? notice.bodyBn : notice.bodyEn}</p>
            </Card>
          ))}
        </div>
      </section>

      {/* ---------- Referral sheet ---------- */}
      <Sheet open={!!referTarget} onClose={() => setReferTarget(null)} title={t('dash.quickAdd')}>
        {referTarget && (
          <div className="space-y-4">
            <div className="flex items-center gap-3 rounded-2xl bg-white p-3">
              <Avatar name={referTarget.name} photoUrl={referTarget.photoUrl} />
              <div className="min-w-0">
                <div className="truncate font-bold text-ink-900">
                  {referTarget.nameBn || referTarget.name}
                </div>
                <div className="text-sm text-ink-400">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(referTarget.batchYear)}
                </div>
              </div>
            </div>

            <Field
              label={lang === 'bn' ? 'তার মোবাইল নম্বর' : 'Their mobile number'}
              hint={
                lang === 'bn'
                  ? 'আমরা তাকে একটি এসএমএস পাঠাব — আপনার নাম উল্লেখ করে।'
                  : 'We will send them an SMS mentioning your name.'
              }
            >
              <Input
                type="tel"
                inputMode="numeric"
                placeholder="01XXXXXXXXX"
                value={referPhone}
                onChange={(e) => setReferPhone(e.target.value)}
                className="text-center text-xl tracking-widest tabular-nums"
              />
            </Field>

            <Button full size="lg" loading={busy} icon={<UserPlus className="size-5" />} onClick={submitReferral}>
              {t('cta.add')}
            </Button>
          </div>
        )}
      </Sheet>
    </div>
  )
}
