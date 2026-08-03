import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, CalendarDays, LayoutDashboard, LogIn, MapPin, Phone, Search, Sparkles, UserPlus } from 'lucide-react'
import { useApp } from '../lib/store'
import { api, CONTACT_PHONE, type Batch, type EventInfo, type Notice } from '../lib/api'
import { SCHOOL, TEACHERS } from '../mock/data'
import { Badge, Button, Card, SectionTitle, Stat, cx } from '../components/ui'
import { LangToggle, SchoolMark } from '../components/Layout'

/** Counts down to `target`, or to nothing at all until the date has loaded. */
function useCountdown(target?: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])
  const diff = target ? Math.max(0, new Date(target).getTime() - now) : 0
  return {
    days: Math.floor(diff / 86400000),
    hours: Math.floor((diff / 3600000) % 24),
    mins: Math.floor((diff / 60000) % 60),
    secs: Math.floor((diff / 1000) % 60),
  }
}

/**
 * Log in / Register sitting side by side. A visitor who has never signed up
 * should not have to guess that "Find my name" is the way in, so the register
 * action is named plainly and given the loud colour.
 * `stacked` renders them as two equal full-width buttons for the phone row.
 */
function AuthActions({ stacked }: { stacked?: boolean }) {
  const { t, user } = useApp()
  const size = stacked ? 'md' : 'sm'

  // The gold variant draws no border, the translucent one draws 2px. Left alone
  // that is a 4px height difference between two buttons sitting side by side, so
  // gold gets a transparent border of the same width to match. `h-full` lets each
  // button fill its stretched grid cell in the stacked row.
  const shell = cx('border-2 border-transparent', stacked && 'h-full')
  const quiet = cx('border-2 border-white/25 bg-white/10 text-white hover:bg-white/20', stacked && 'h-full')
  const linkCx = cx(stacked && 'flex')

  if (user) {
    return (
      <Link to="/app" className={linkCx}>
        <Button size={size} full={stacked} icon={<LayoutDashboard className="size-4" />} className={quiet}>
          {t('nav.dashboard')}
        </Button>
      </Link>
    )
  }

  return (
    <>
      <Link to="/login" className={linkCx}>
        <Button size={size} full={stacked} icon={<LogIn className="size-4" />} className={quiet}>
          {t('nav.login')}
        </Button>
      </Link>
      <Link to="/signup" className={linkCx}>
        <Button size={size} full={stacked} variant="gold" icon={<UserPlus className="size-4" />} className={shell}>
          {t('nav.signup')}
        </Button>
      </Link>
    </>
  )
}

export default function Landing() {
  const { t, n, yr, lang, user } = useApp()
  const navigate = useNavigate()
  const [batches, setBatches] = useState<Batch[]>([])
  const [totals, setTotals] = useState({ roster: 0, claimed: 0, batches: 0 })
  const [notices, setNotices] = useState<Notice[]>([])
  /** Null until the reunion loads. The hero says nothing rather than the wrong thing. */
  const [event, setEvent] = useState<EventInfo | null>(null)

  const cd = useCountdown(event?.date)

  useEffect(() => {
    api.batches().then(setBatches)
    api.totals().then(setTotals)
    api.notices().then(setNotices)
    api.event().then(setEvent).catch(() => setEvent(null))
  }, [])

  const pct = totals.roster > 0 ? Math.round((totals.claimed / totals.roster) * 100) : 0

  const eventDate = useMemo(
    () =>
      event?.date
        ? new Date(event.date).toLocaleDateString(lang === 'bn' ? 'bn-BD' : 'en-GB', {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
            year: 'numeric',
          })
        : null,
    [lang, event?.date],
  )

  return (
    <div className={cx('min-h-dvh', lang === 'bn' && 'font-bn')}>
      {/* ---------------- Hero ---------------- */}
      <header className="relative overflow-hidden bg-brand-800 text-white">
        <div className="paper-grain absolute inset-0 opacity-60" />
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-brand-600/40 blur-3xl" />
        <div className="absolute -bottom-32 -left-20 size-96 rounded-full bg-gold-600/20 blur-3xl" />

        <div className="relative mx-auto max-w-6xl px-4 py-4">
          <div className="flex items-center justify-between gap-4">
            <SchoolMark dark />
            <div className="flex shrink-0 items-center gap-2">
              <LangToggle dark />
              <div className="hidden items-center gap-2 sm:flex">
                <AuthActions />
              </div>
            </div>
          </div>

          {/* On a phone the crest and the language toggle already fill the row,
              so the same two actions get their own full-width row underneath.
              A 2-column grid rather than flex-1: `নিবন্ধন করুন` is much wider than
              `লগইন`, and flex items refuse to shrink under their content, which
              would hand the register button the larger half. */}
          <div className={cx('mt-3 grid gap-2 sm:hidden', user ? 'grid-cols-1' : 'grid-cols-2')}>
            <AuthActions stacked />
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

          {/* Title, date and venue come from the database. Until they arrive the
              hero holds their space rather than showing a guess — the date in
              particular is one the committee moves, and a stale one sends people
              to the school on the wrong day. */}
          <h1 className="mx-auto mt-5 max-w-3xl text-4xl font-extrabold leading-tight tracking-tight sm:text-6xl">
            {event ? (
              lang === 'bn' ? event.titleBn : event.titleEn
            ) : (
              <span className="mx-auto block h-10 w-80 max-w-full animate-pulse rounded-xl bg-white/15 sm:h-14" />
            )}
          </h1>
          <p className="mx-auto mt-3 max-w-xl text-lg text-white/75">{t('landing.heroSub')}</p>

          <div className="mt-5 flex min-h-7 flex-wrap items-center justify-center gap-x-6 gap-y-2 text-white/80">
            {event && (
              <>
                {eventDate && (
                  <span className="inline-flex items-center gap-2">
                    <CalendarDays className="size-5 text-gold-300" />
                    {eventDate}
                  </span>
                )}
                {(lang === 'bn' ? event.venueBn : event.venueEn) && (
                  <span className="inline-flex items-center gap-2">
                    <MapPin className="size-5 text-gold-300" />
                    {lang === 'bn' ? event.venueBn : event.venueEn}
                  </span>
                )}
              </>
            )}
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
          <div className="mt-4 flex flex-col items-center gap-3">
            <a
              href={`tel:+880${CONTACT_PHONE.replace(/^0/, '')}`}
              className="inline-flex items-center gap-2 rounded-full bg-brand-600 px-5 py-2.5 font-semibold text-white"
            >
              <Phone className="size-5" />
              {t('auth.needHelp')}: {CONTACT_PHONE}
            </a>
            <Link to="/admin/login" className="text-xs font-semibold text-ink-400 underline underline-offset-4">
              {t('admin.portal')}
            </Link>
          </div>
        </div>
      </footer>
    </div>
  )
}
