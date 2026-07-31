import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { CalendarRange, Check, CheckCircle2, Search, UserPlus } from 'lucide-react'
import { api, ApiError, type Person } from '../lib/api'
import { SCHOOL } from '../mock/data'
import { useApp } from '../lib/store'
import AuthShell from '../components/AuthShell'
import { Avatar, Badge, Button, Card, Field, Input, Spinner, cx } from '../components/ui'

type Step = 'year' | 'name' | 'confirm' | 'done'

export default function Signup() {
  const { t, lang, n, yr, setUser } = useApp()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const [step, setStep] = useState<Step>('year')
  const [year, setYear] = useState(params.get('year') ?? '')
  const [roster, setRoster] = useState<Person[] | null>(null)
  const [query, setQuery] = useState('')
  const [picked, setPicked] = useState<Person | null>(null)
  const [manualName, setManualName] = useState('')
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [otpSent, setOtpSent] = useState(false)
  const [challengeId, setChallengeId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [hint, setHint] = useState<'login' | 'resend' | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  const yearNum = Number(year)
  const validYear = yearNum >= SCHOOL.firstBatch && yearNum <= SCHOOL.lastBatch

  useEffect(() => {
    if (params.get('year')) loadYear(params.get('year')!)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function showError(e: unknown) {
    setHint(null)
    if (e instanceof ApiError) {
      setError(lang === 'bn' ? e.messageBn : e.message)
      if (e.code === 'phone_taken') setHint('login')
      else if (e.code === 'otp_expired') setHint('resend')
    } else {
      setError(lang === 'bn' ? 'কিছু একটা সমস্যা হয়েছে' : 'Something went wrong')
    }
  }

  async function loadYear(y: string) {
    const num = Number(y)
    if (!(num >= SCHOOL.firstBatch && num <= SCHOOL.lastBatch)) {
      setError(
        lang === 'bn'
          ? `সাল ${SCHOOL.firstBatch} থেকে ${SCHOOL.lastBatch} এর মধ্যে হতে হবে`
          : `Year must be between ${SCHOOL.firstBatch} and ${SCHOOL.lastBatch}`,
      )
      return
    }
    setError('')
    setYear(y)
    setBusy(true)
    setStep('name')
    try {
      setRoster(await api.lookupBatch(num))
    } catch (e) {
      showError(e)
    } finally {
      setBusy(false)
    }
  }

  const filtered = useMemo(() => {
    if (!roster) return []
    const q = query.trim().toLowerCase()
    if (!q) return roster
    return roster.filter((p) => p.name.toLowerCase().includes(q) || p.nameBn.includes(query.trim()))
  }, [roster, query])

  async function sendOtp() {
    setError('')
    setHint(null)
    setBusy(true)
    try {
      const res = picked
        ? await api.claimProfile(picked.id, phone, yearNum)
        : await api.registerNew(manualName, manualName, yearNum, phone)
      setChallengeId(res.challengeId)
      setOtpSent(true)
    } catch (e) {
      showError(e)
    } finally {
      setBusy(false)
    }
  }

  async function finish() {
    setError('')
    setHint(null)
    setBusy(true)
    try {
      const { person } = await api.verifyOtp(challengeId, otp.replace(/\D/g, ''))
      setUser(person)
      setStep('done')
    } catch (e) {
      showError(e)
    } finally {
      setBusy(false)
    }
  }

  /* ---------------- Step indicator ---------------- */
  const stepIndex = { year: 0, name: 1, confirm: 2, done: 3 }[step]

  return (
    <AuthShell
      onBack={
        step === 'name'
          ? () => setStep('year')
          : step === 'confirm'
            ? () => setStep('name')
            : undefined
      }
    >
      {step !== 'done' && (
        <div className="mb-6 flex items-center gap-2">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className={cx('h-1.5 flex-1 rounded-full transition-all', i <= stepIndex ? 'bg-brand-600' : 'bg-paper-2')}
            />
          ))}
        </div>
      )}

      {/* ============ STEP 1 : year ============ */}
      {step === 'year' && (
        <div className="animate-fade-up">
          <div className="mb-6 text-center">
            <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-brand-50 text-brand-600">
              <CalendarRange className="size-8" />
            </span>
            <h1 className="text-3xl font-extrabold text-ink-900">{t('signup.title')}</h1>
            <p className="mt-1.5 text-ink-500">{t('signup.sub')}</p>
          </div>

          <Card className="space-y-4">
            <Field label={t('signup.step1')} error={error} required>
              <Input
                type="number"
                inputMode="numeric"
                placeholder={t('signup.yearPlaceholder')}
                value={year}
                onChange={(e) => setYear(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && validYear && loadYear(year)}
                className="text-center text-2xl font-bold tracking-wider tabular-nums"
              />
            </Field>

            <div>
              <p className="mb-2 text-sm font-semibold text-ink-400">
                {lang === 'bn' ? 'অথবা বেছে নিন' : 'Or pick a decade'}
              </p>
              <div className="grid grid-cols-3 gap-2">
                {[1970, 1980, 1990, 2000, 2010, 2020].map((decade) => (
                  <button
                    key={decade}
                    onClick={() => setYear(String(decade))}
                    className="rounded-xl border-2 border-paper-2 bg-white py-2.5 font-bold tabular-nums text-ink-600 transition hover:border-brand-300 hover:bg-brand-50"
                  >
                    {n(String(decade))}s
                  </button>
                ))}
              </div>
            </div>

            <Button full size="lg" disabled={!validYear} onClick={() => loadYear(year)}>
              {t('cta.continue')}
            </Button>
          </Card>
        </div>
      )}

      {/* ============ STEP 2 : pick your name ============ */}
      {step === 'name' && (
        <div className="animate-fade-up">
          <div className="mb-4">
            <Badge tone="green">
              {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(year)}
            </Badge>
            <h1 className="mt-2 text-2xl font-extrabold text-ink-900">{t('signup.step2')}</h1>
            {roster && (
              <p className="mt-1 text-ink-500">
                {n(roster.length)} {lang === 'bn' ? 'জনের নাম আছে' : 'names on the register'}
              </p>
            )}
          </div>

          <div className="sticky top-0 z-10 -mx-4 mb-3 bg-paper px-4 pb-3 pt-1">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-ink-400" />
              <Input
                placeholder={t('signup.searchName')}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="pl-11"
              />
            </div>
          </div>

          {busy && !roster ? (
            <Spinner label={t('common.loading')} />
          ) : (
            <div ref={listRef} className="space-y-2">
              {filtered.map((p) => (
                <button
                  key={p.id}
                  onClick={() => {
                    setPicked(p)
                    setStep('confirm')
                  }}
                  className="flex w-full items-center gap-3 rounded-2xl border-2 border-paper-2 bg-white p-3 text-left transition hover:border-brand-300 hover:bg-brand-50"
                >
                  <Avatar name={p.name} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-bold text-ink-900">{lang === 'bn' ? p.nameBn : p.name}</span>
                    <span className="block truncate text-sm text-ink-400">
                      {p.status === 'CLAIMED'
                        ? lang === 'bn'
                          ? 'ইতিমধ্যে যুক্ত হয়েছেন'
                          : 'Already joined'
                        : lang === 'bn'
                          ? 'এখনো যুক্ত হননি'
                          : 'Not joined yet'}
                    </span>
                  </span>
                  {p.status === 'CLAIMED' ? (
                    <Check className="size-5 shrink-0 text-brand-500" />
                  ) : (
                    <span className="shrink-0 rounded-lg bg-brand-600 px-3 py-1.5 text-sm font-bold text-white">
                      {t('signup.thisIsMe')}
                    </span>
                  )}
                </button>
              ))}

              {filtered.length === 0 && (
                <p className="py-8 text-center text-ink-400">
                  {lang === 'bn' ? 'কোনো নাম মেলেনি' : 'No matching name'}
                </p>
              )}

              <Card className="mt-4 border-dashed border-gold-300 bg-gold-50/50 text-center">
                <p className="font-semibold text-ink-700">{t('signup.notFound')}</p>
                <p className="mt-1 text-sm text-ink-500">
                  {lang === 'bn'
                    ? 'কোনো সমস্যা নেই — নিজের নাম লিখে যুক্ত হোন।'
                    : 'No problem — add yourself by typing your name.'}
                </p>
                <Button
                  variant="outline"
                  className="mt-3"
                  icon={<UserPlus className="size-5" />}
                  onClick={() => {
                    setPicked(null)
                    setStep('confirm')
                  }}
                >
                  {t('cta.add')}
                </Button>
              </Card>
            </div>
          )}
        </div>
      )}

      {/* ============ STEP 3 : confirm with phone ============ */}
      {step === 'confirm' && (
        <div className="animate-fade-up">
          <h1 className="text-2xl font-extrabold text-ink-900">{t('signup.step3')}</h1>

          {picked ? (
            <Card className="mt-4 flex items-center gap-3 border-brand-200 bg-brand-50/60">
              <Avatar name={picked.name} size="lg" />
              <div className="min-w-0">
                <div className="truncate text-lg font-bold text-ink-900">
                  {lang === 'bn' ? picked.nameBn : picked.name}
                </div>
                <div className="text-ink-500">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(picked.batchYear)}
                </div>
              </div>
            </Card>
          ) : (
            <Card className="mt-4">
              <Field label={t('signup.newName')} required>
                <Input value={manualName} onChange={(e) => setManualName(e.target.value)} placeholder="মোঃ ..." />
              </Field>
            </Card>
          )}

          <Card className="mt-4 space-y-4">
            <Field label={t('auth.phone')} hint={t('auth.phoneHelp')} error={!otpSent ? error : undefined} required>
              <Input
                type="tel"
                inputMode="numeric"
                placeholder="01XXXXXXXXX"
                value={phone}
                onChange={(e) => { setPhone(e.target.value); setHint(null); setError('') }}
                className="text-center text-xl tracking-widest tabular-nums"
                maxLength={14}
                disabled={otpSent}
              />
            </Field>

            {!otpSent && hint === 'login' && (
              <p className="rounded-xl bg-brand-50 px-4 py-3 text-center text-sm text-brand-700">
                {lang === 'bn' ? 'এই নম্বরে অ্যাকাউন্ট আছে।' : 'This number already has an account.'}{' '}
                <Link to="/login" className="font-bold underline underline-offset-4">
                  {lang === 'bn' ? 'লগইন করুন' : 'Log in instead'}
                </Link>
              </p>
            )}

            {!otpSent ? (
              <Button
                full
                size="lg"
                loading={busy}
                disabled={!picked && manualName.trim().length < 3}
                onClick={sendOtp}
              >
                {t('auth.sendCode')}
              </Button>
            ) : (
              <>
                <Field label={t('auth.otpTitle')} error={otpSent ? error : undefined}>
                  <Input
                    inputMode="numeric"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => { setOtp(e.target.value); setError('') }}
                    placeholder="······"
                    className="text-center text-2xl font-bold tracking-[0.5em] tabular-nums"
                  />
                </Field>
                {hint === 'resend' && (
                  <p className="text-center text-sm text-ink-500">
                    <button onClick={() => { setOtpSent(false); setError(''); setHint(null) }} className="min-h-0 font-semibold text-brand-700 underline underline-offset-4">
                      {t('auth.resend')}
                    </button>
                  </p>
                )}
                <Button full size="lg" loading={busy} onClick={finish}>
                  {t('cta.continue')}
                </Button>
              </>
            )}
          </Card>
        </div>
      )}

      {/* ============ DONE ============ */}
      {step === 'done' && (
        <div className="animate-pop text-center">
          <span className="mx-auto mb-4 grid size-24 place-items-center rounded-full bg-brand-50 text-brand-600">
            <CheckCircle2 className="size-14" />
          </span>
          <h1 className="text-3xl font-extrabold text-ink-900">{t('signup.welcome')}</h1>
          <p className="mt-2 text-ink-500">{t('signup.welcomeSub')}</p>

          <Card className="mt-6 text-left">
            <div className="flex items-center gap-3">
              <Avatar name={picked?.name ?? manualName} size="lg" />
              <div className="min-w-0">
                <div className="truncate text-lg font-bold text-ink-900">
                  {picked ? (lang === 'bn' ? picked.nameBn : picked.name) : manualName}
                </div>
                <div className="text-ink-500">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(yearNum)}
                </div>
              </div>
            </div>
          </Card>

          <Button full size="lg" className="mt-6" onClick={() => navigate('/app')}>
            {t('nav.dashboard')}
          </Button>
        </div>
      )}
    </AuthShell>
  )
}
