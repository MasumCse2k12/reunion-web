import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { KeyRound, MessageSquare, Smartphone } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { useApp } from '../lib/store'
import AuthShell from '../components/AuthShell'
import { Button, Card, Field, Input } from '../components/ui'

export default function Login() {
  const { t, lang, setUser, n } = useApp()
  const navigate = useNavigate()

  const [step, setStep] = useState<'phone' | 'otp'>('phone')
  const [phone, setPhone] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [code, setCode] = useState(['', '', '', '', '', ''])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [hint, setHint] = useState<'signup' | 'resend' | null>(null)
  const boxes = useRef<(HTMLInputElement | null)[]>([])

  function showError(e: unknown) {
    setHint(null)
    if (e instanceof ApiError) {
      setError(lang === 'bn' ? e.messageBn : e.message)
      if (e.code === 'not_found') setHint('signup')
      else if (e.code === 'otp_expired') setHint('resend')
    } else {
      setError(lang === 'bn' ? 'কিছু একটা সমস্যা হয়েছে' : 'Something went wrong')
    }
  }

  async function sendCode() {
    setError('')
    setHint(null)
    setBusy(true)
    try {
      const res = await api.requestOtp(phone)
      setChallengeId(res.challengeId)
      setStep('otp')
      setTimeout(() => boxes.current[0]?.focus(), 50)
    } catch (e) {
      showError(e)
    } finally {
      setBusy(false)
    }
  }

  async function verify(fullCode?: string) {
    setError('')
    setHint(null)
    setBusy(true)
    try {
      const { person } = await api.verifyOtp(challengeId, fullCode ?? code.join(''))
      setUser(person)
      navigate('/app')
    } catch (e) {
      showError(e)
      setCode(['', '', '', '', '', ''])
      boxes.current[0]?.focus()
    } finally {
      setBusy(false)
    }
  }

  function onDigit(i: number, v: string) {
    const digit = v.replace(/\D/g, '').slice(-1)
    const next = [...code]
    next[i] = digit
    setCode(next)
    if (digit && i < 5) boxes.current[i + 1]?.focus()
    if (next.every((d) => d)) verify(next.join(''))
  }

  return (
    <AuthShell onBack={step === 'otp' ? () => setStep('phone') : undefined}>
      {step === 'phone' ? (
        <div className="animate-fade-up">
          <div className="mb-6 text-center">
            <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-brand-50 text-brand-600">
              <Smartphone className="size-8" />
            </span>
            <h1 className="text-3xl font-extrabold text-ink-900">{t('auth.loginTitle')}</h1>
            <p className="mt-1.5 text-ink-500">{t('auth.loginSub')}</p>
          </div>

          <Card className="space-y-4">
            <Field label={t('auth.phone')} hint={t('auth.phoneHelp')} error={error} required>
              <Input
                type="tel"
                inputMode="numeric"
                autoComplete="tel"
                placeholder="01XXXXXXXXX"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && sendCode()}
                className="text-center text-xl tracking-widest tabular-nums"
                maxLength={14}
              />
            </Field>

            {hint === 'signup' && (
              <p className="rounded-xl bg-brand-50 px-4 py-3 text-center text-sm text-brand-700">
                {lang === 'bn' ? 'প্রথমবার?' : 'First time?'}{' '}
                <Link to="/signup" className="font-bold underline underline-offset-4">
                  {t('cta.findMe')}
                </Link>
              </p>
            )}

            <Button full size="lg" loading={busy} onClick={sendCode} icon={<MessageSquare className="size-5" />}>
              {t('auth.sendCode')}
            </Button>
          </Card>

          <div className="mt-6 text-center text-ink-500">
            {t('auth.noAccount')}{' '}
            <Link to="/signup" className="font-bold text-brand-700 underline underline-offset-4">
              {t('cta.findMe')}
            </Link>
          </div>
        </div>
      ) : (
        <div className="animate-fade-up">
          <div className="mb-6 text-center">
            <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-gold-50 text-gold-600">
              <KeyRound className="size-8" />
            </span>
            <h1 className="text-3xl font-extrabold text-ink-900">{t('auth.otpTitle')}</h1>
            <p className="mt-1.5 text-ink-500">{t('auth.otpSub')}</p>
            <p className="mt-1 font-bold tabular-nums text-ink-700">{n(phone)}</p>
          </div>

          <Card>
            <div dir="ltr" className="flex justify-center gap-2">
              {code.map((d, i) => (
                <input
                  key={i}
                  ref={(el) => {
                    boxes.current[i] = el
                  }}
                  value={d}
                  onChange={(e) => onDigit(i, e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Backspace' && !code[i] && i > 0) boxes.current[i - 1]?.focus()
                  }}
                  inputMode="numeric"
                  maxLength={1}
                  aria-label={`Digit ${i + 1}`}
                  className="h-14 w-12 rounded-xl border-2 border-paper-2 bg-white text-center text-2xl font-bold tabular-nums focus:border-brand-400 focus:outline-none"
                />
              ))}
            </div>

            {error && <p className="mt-3 text-center font-medium text-red-600">{error}</p>}
            {hint === 'resend' && (
              <p className="mt-1 text-center text-sm text-ink-500">
                <button onClick={sendCode} className="min-h-0 font-semibold text-brand-700 underline underline-offset-4">
                  {t('auth.resend')}
                </button>
              </p>
            )}

            <Button full size="lg" className="mt-4" loading={busy} onClick={() => verify()}>
              {t('auth.verify')}
            </Button>

            {hint !== 'resend' && (
              <button onClick={sendCode} className="mt-3 w-full text-center font-semibold text-brand-700 underline underline-offset-4">
                {t('auth.resend')}
              </button>
            )}
          </Card>
        </div>
      )}
    </AuthShell>
  )
}
