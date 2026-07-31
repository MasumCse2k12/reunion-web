import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, CalendarDays, MapPin, Phone, Search, Sparkles } from 'lucide-react'
import { useApp } from '../lib/store'
import { api, EVENT, type Batch, type Notice } from '../lib/api'
import { SCHOOL, TEACHERS } from '../mock/data'
import { Badge, Button, Card, SectionTitle, Stat, cx } from '../components/ui'
import { DemoBanner, LangToggle, SchoolMark } from '../components/Layout'

function useCountdown(target: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])
  const diff = Math.max(0, new Date(target).getTime() - now)
  return {
    days: Math.floor(diff / 86400000),
    hours: Math.floor((diff / 3600000) % 24),
    mins: Math.floor((diff / 60000) % 60),
    secs: Math.floor((diff / 1000) % 60),
  }
}

export default function Landing() {
  const { t, n, yr, lang, user } = useApp()
  const navigate = useNavigate()
  const cd = useCountdown(EVENT.date)

  const [batches, setBatches] = useState<Batch[]>([])
  const [totals, setTotals] = useState({ roster: 0, claimed: 0, batches: 0 })
  const [notices, setNotices] = useState<Notice[]>([])

  useEffect(() => {
    api.batches().then(setBatches)
    api.totals().then(setTotals)
    api.notices().then(setNotices)
  }, [])

  const pct = totals.roster > 0 ? Math.round((totals.claimed / totals.roster) * 100) : 0

  const eventDate = useMemo(
    () =>
      new Date(EVENT.date).toLocaleDateString(lang === 'bn' ? 'bn-BD' : 'en-GB', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      }),
    [lang],
  )

  return (
    <div className={cx('min-h-dvh', lang === 'bn' && 'font-bn')}>
      <DemoBanner />

      {/* ---------------- Hero ---------------- */}
      <header className="relative overflow-hidden bg-brand-800 text-white">
        <div className="paper-grain absolute inset-0 opacity-60" />
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-brand-600/40 blur-3xl" />
        <div className="absolute -bottom-32 -left-20 size-96 rounded-full bg-gold-600/20 blur-3xl" />

        <div className="relative mx-auto max-w-6xl px-4 py-4">
          <div className="flex items-center justify-between gap-4">
            <SchoolMark dark />
            <div className="flex items-center gap-2">
              <LangToggle dark />
              <Link to={user ? '/app' : '/login'} className="hidden sm:block">
                <Button variant="ghost" size="sm" className="text-white hover:bg-white/10">
                  {user ? t('nav.dashboard') : t('nav.login')}
                </Button>
              </Link>
            </div>
          </div>
        </div>

        <div className="relative mx-auto max-w-6xl px-4 pb-14 pt-8 text-center sm:pb-20 sm:pt-14">
          <img
            src="/school-logo.png"
            alt={lang === 'bn' ? SCHOOL.nameBn : SCHOOL.nameEn}
            className="mx-auto mb-6 size-24 rounded-full bg-white p-1.5 shadow-xl shadow-ink-900/25 ring-2 ring-white/25 sm:size-28"
          />

          <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-1.5 text-sm font-semibold text-gold-200 ring-1 ring-white/15">
            <Sparkles className="size-4" />
            {t('landing.est')}
          </span>

          <h1 className="mx-auto mt-5 max-w-3xl text-4xl font-extrabold leading-tight tracking-tight sm:text-6xl">
            {lang === 'bn' ? EVENT.titleBn : EVENT.titleEn}
          </h1>
          <p className="mx-auto mt-3 max-w-xl text-lg text-white/75">{t('landing.heroSub')}</p>

          <div className="mt-5 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-white/80">
            <span className="inline-flex items-center gap-2">
              <CalendarDays className="size-5 text-gold-300" />
              {eventDate}
            </span>
            <span className="inline-flex items-center gap-2">
              <MapPin className="size-5 text-gold-300" />
              {lang === 'bn' ? EVENT.venueBn : EVENT.venueEn}
            </span>
          </div>

          {/* Countdown */}
          <div className="mx-auto mt-8 grid max-w-md grid-cols-4 gap-2 sm:gap-3">
            {[
              [cd.days, t('landing.days')],
              [cd.hours, t('landing.hours')],
              [cd.mins, t('landing.mins')],
              [cd.secs, t('landing.secs')],
            ].map(([v, l], i) => (
              <div key={i} className="rounded-2xl bg-white/10 py-3 ring-1 ring-white/15 backdrop-blur-sm">
                <div className="text-2xl font-extrabold tabular-nums text-gold-200 sm:text-3xl">{n(v as number)}</div>
                <div className="text-xs text-white/60">{l as string}</div>
              </div>
            ))}
          </div>

          {/* Primary CTAs */}
          <div className="mx-auto mt-9 flex max-w-md flex-col gap-3 sm:flex-row sm:justify-center">
            <Button size="lg" variant="gold" full icon={<Search className="size-5" />} onClick={() => navigate('/signup')}>
              {t('cta.findMe')}
            </Button>
            <Button
              size="lg"
              full
              className="border-2 border-white/25 bg-white/10 text-white hover:bg-white/20"
              onClick={() => navigate('/login')}
            >
              {t('cta.login')}
            </Button>
          </div>

          {/* Stats */}
          <div className="mx-auto mt-12 grid max-w-3xl grid-cols-2 gap-6 border-t border-white/10 pt-8 sm:grid-cols-4">
            <Stat value={n(totals.claimed)} label={t('landing.foundSoFar')} tone="gold" />
            <Stat value={n(0)} label={t('landing.registered')} />
            <Stat value={n(totals.batches)} label={t('landing.batches')} />
            <Stat value={n(0)} label={t('landing.teachers')} />
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl space-y-10 px-4 py-10">
        {/* ---------------- Progress ---------------- */}
        <Card>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <div className="text-3xl font-extrabold text-brand-700">
                {n(totals.claimed)}{' '}
                <span className="text-lg font-semibold text-ink-400">
                  / {n(totals.roster)} {t('landing.ofTotal')}
                </span>
              </div>
              <p className="mt-1 text-ink-500">{t('landing.foundSoFar')}</p>
            </div>
            <Badge tone="gold">{n(pct)}%</Badge>
          </div>
          <div className="mt-4 h-4 overflow-hidden rounded-full bg-paper-2">
            <div
              className="h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-700 transition-all duration-1000"
              style={{ width: `${pct}%` }}
            />
          </div>
        </Card>

        {/* ---------------- Batch coverage heatmap ---------------- */}
        <section>
          <SectionTitle>{t('landing.batchCoverage')}</SectionTitle>
          <p className="mb-4 text-ink-500">{t('landing.coverageNote')}</p>
          <div className="grid grid-cols-5 gap-1.5 sm:grid-cols-10 lg:grid-cols-12">
            {batches.map((b) => {
              const ratio = b.claimedCount / b.rosterCount
              const shade =
                ratio > 0.6
                  ? 'bg-brand-700 text-white'
                  : ratio > 0.45
                    ? 'bg-brand-500 text-white'
                    : ratio > 0.3
                      ? 'bg-brand-300 text-brand-900'
                      : ratio > 0.18
                        ? 'bg-brand-200 text-brand-900'
                        : 'bg-gold-100 text-gold-700 ring-1 ring-gold-300'
              return (
                <Link
                  key={b.year}
                  to={`/signup?year=${b.year}`}
                  title={`${b.year}: ${b.claimedCount}/${b.rosterCount}`}
                  className={cx(
                    'group flex aspect-square flex-col items-center justify-center rounded-lg text-xs font-bold transition hover:scale-105 hover:shadow-md',
                    shade,
                  )}
                >
                  <span className="tabular-nums">{yr(b.year)}</span>
                  <span className="text-[0.62rem] font-semibold opacity-75 tabular-nums">
                    {n(b.claimedCount)}/{n(b.rosterCount)}
                  </span>
                </Link>
              )
            })}
          </div>
          <p className="mt-3 text-sm text-ink-400">
            {lang === 'bn'
              ? 'সোনালি ঘরগুলো — এই ব্যাচগুলোর বেশিরভাগ শিক্ষার্থীকে এখনো খুঁজে পাওয়া যায়নি।'
              : 'The gold squares are the batches we have barely reached. That is where help is needed.'}
          </p>
        </section>

        {/* ---------------- Notices ---------------- */}
        <section className="grid gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <SectionTitle>{t('dash.notices')}</SectionTitle>
            <div className="space-y-3">
              {notices.map((notice) => (
                <Card key={notice.id} className={cx(notice.pinned && 'border-gold-300 bg-gold-50/40')}>
                  <div className="flex items-start justify-between gap-3">
                    <h3 className="font-bold text-ink-900">{lang === 'bn' ? notice.titleBn : notice.titleEn}</h3>
                    {notice.pinned && <Badge tone="gold">★</Badge>}
                  </div>
                  <p className="mt-1.5 text-ink-500">{lang === 'bn' ? notice.bodyBn : notice.bodyEn}</p>
                </Card>
              ))}
            </div>
          </div>

          <div className="space-y-6">
            {/* Teachers — the only real data in the demo */}
            <Card>
              <SectionTitle>{lang === 'bn' ? 'বর্তমান শিক্ষকবৃন্দ' : 'Current teachers'}</SectionTitle>
              <ul className="space-y-2.5">
                {TEACHERS.map((teacher) => (
                  <li key={teacher.id} className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate font-semibold text-ink-700">
                        {lang === 'bn' ? teacher.nameBn : teacher.name}
                      </div>
                      <div className="truncate text-sm text-ink-400">
                        {lang === 'bn' ? teacher.roleBn : teacher.roleEn}
                      </div>
                    </div>
                    {teacher.memorial && <Badge tone="muted">{lang === 'bn' ? 'স্মরণে' : 'In memoriam'}</Badge>}
                  </li>
                ))}
              </ul>
              <p className="mt-3 border-t border-paper-2 pt-3 text-sm text-ink-400">
                {lang === 'bn'
                  ? 'এই তালিকাটি প্রকৃত — বিদ্যালয়ের বর্তমান শিক্ষকমণ্ডলী। পৃষ্ঠার বাকি সব তথ্য কাল্পনিক।'
                  : 'This list is real — the school’s current staff. Everything else on this page is fictional.'}
              </p>
            </Card>
          </div>
        </section>

        {/* ---------------- Final CTA ---------------- */}
        <Card className="bg-gradient-to-br from-brand-600 to-brand-800 text-center text-white">
          <h2 className="text-2xl font-extrabold sm:text-3xl">
            {lang === 'bn' ? 'আপনার নাম কি তালিকায় আছে?' : 'Is your name on the list?'}
          </h2>
          <p className="mx-auto mt-2 max-w-lg text-white/75">
            {lang === 'bn'
              ? 'এক মিনিটেরও কম সময় লাগবে। কোনো পাসওয়ার্ড লাগবে না, শুধু আপনার মোবাইল নম্বর।'
              : 'It takes less than a minute. No password — just your mobile number.'}
          </p>
          <div className="mt-5 flex justify-center">
            <Button variant="gold" size="lg" icon={<ArrowRight className="size-5" />} onClick={() => navigate('/signup')}>
              {t('cta.findMe')}
            </Button>
          </div>
        </Card>
      </main>

      <footer className="border-t border-paper-2 bg-paper-2/60">
        <div className="mx-auto max-w-6xl px-4 py-8 text-center text-ink-500">
          <div className="font-bold text-ink-700">{lang === 'bn' ? SCHOOL.nameBn : SCHOOL.nameEn}</div>
          <div className="text-sm">{lang === 'bn' ? SCHOOL.locationBn : SCHOOL.locationEn}</div>
          <a
            href="tel:+8801712345678"
            className="mt-4 inline-flex items-center gap-2 rounded-full bg-brand-600 px-5 py-2.5 font-semibold text-white"
          >
            <Phone className="size-5" />
            {t('auth.needHelp')}: 01712-345678
          </a>
          <p className="mt-5 text-xs text-ink-400">
            {lang === 'bn'
              ? 'ডেমো সংস্করণ · সকল তথ্য কাল্পনিক · ২০২৬'
              : 'Demo build · All data is fictional · 2026'}
          </p>
          <Link to="/admin/login" className="mt-2 inline-block text-xs font-semibold text-ink-400 underline underline-offset-4">
            {t('admin.portal')}
          </Link>
        </div>
      </footer>
    </div>
  )
}
