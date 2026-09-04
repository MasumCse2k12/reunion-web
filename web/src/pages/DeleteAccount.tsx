import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, ShieldAlert, Smartphone, TriangleAlert } from 'lucide-react'
import { api, ApiError, CONTACT_PHONE, type DeletionPreview } from '../lib/api'
import { useApp } from '../lib/store'
import { Button, Card, Field, Input, cx } from '../components/ui'
import { LangToggle, SchoolMark } from '../components/Layout'

/**
 * Account deletion from the web, at a public URL.
 *
 * Google Play wants this route to exist alongside the in-app one, for someone
 * who has uninstalled the app or never had an Android phone. It could have been
 * a form that emails the committee — but a request somebody has to action by
 * hand is a promise, not a deletion, and it would need a new unauthenticated
 * endpoint for anyone to abuse. So this signs the member in with the same
 * one-time code the login page uses and calls the same authenticated
 * `DELETE /api/v1/me`. Nothing new is exposed, and the deletion is real.
 *
 * Four steps: prove the number, see what it costs, confirm, done. The third
 * exists because the second may be carrying bad news about a paid ticket, and
 * the number the committee would ring about it is about to be erased.
 */

type Step = 'phone' | 'otp' | 'confirm' | 'done'

export default function DeleteAccount() {
  const { lang, n, money } = useApp()
  const bn = lang === 'bn'

  const [step, setStep] = useState<Step>('phone')
  const [phone, setPhone] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [code, setCode] = useState(['', '', '', '', '', ''])
  const [preview, setPreview] = useState<DeletionPreview | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const boxes = useRef<(HTMLInputElement | null)[]>([])

  function showError(e: unknown) {
    if (e instanceof ApiError) setError(bn ? e.messageBn : e.message)
    else setError(bn ? 'কিছু একটা সমস্যা হয়েছে' : 'Something went wrong')
  }

  async function sendCode() {
    setError('')
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
    setBusy(true)
    try {
      await api.verifyOtp(challengeId, fullCode ?? code.join(''))
      // Signed in only so the deletion can be authenticated as this member.
      setPreview(await api.deletionPreview())
      setStep('confirm')
    } catch (e) {
      showError(e)
      setCode(['', '', '', '', '', ''])
      boxes.current[0]?.focus()
    } finally {
      setBusy(false)
    }
  }

  async function doDelete() {
    setError('')
    setBusy(true)
    try {
      await api.deleteAccount()
      setStep('done')
    } catch (e) {
      showError(e)
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
    <div className={cx('flex min-h-dvh flex-col bg-paper', bn && 'font-bn')}>
      <header className="border-b border-paper-2 bg-white">
        <div className="mx-auto flex max-w-2xl items-center gap-3 px-4 py-3">
          <Link to="/" className="grid size-10 shrink-0 place-items-center rounded-xl hover:bg-paper-2">
            <ArrowLeft className="size-6 text-ink-600" />
          </Link>
          <div className="min-w-0 flex-1">
            <SchoolMark />
          </div>
          <LangToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-lg flex-1 px-4 py-8">
        {step === 'phone' && (
          <div>
            <div className="mb-6 text-center">
              <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-red-50 text-red-500">
                <ShieldAlert className="size-8" />
              </span>
              <h1 className="text-3xl font-extrabold text-ink-900">
                {bn ? 'অ্যাকাউন্ট মুছে ফেলুন' : 'Delete your account'}
              </h1>
              <p className="mt-1.5 text-ink-500">
                {bn
                  ? 'নিশ্চিত করতে আপনার নম্বরে একটি কোড পাঠানো হবে।'
                  : 'We will send a code to your number to confirm it is you.'}
              </p>
            </div>

            <Card className="space-y-4">
              <p className="leading-relaxed text-ink-700">
                {bn
                  ? 'আপনার মোবাইল নম্বর, ইমেইল, জন্ম তারিখ, রক্তের গ্রুপ, পেশা, শহর ও ছবি মুছে যাবে এবং আপনার নিবন্ধন বাতিল হবে। নাম ও ব্যাচ বিদ্যালয়ের তালিকায় থেকে যাবে।'
                  : 'This erases your mobile number, email, date of birth, blood group, occupation, city and photo, and cancels your registration. Your name and batch year stay on the school roster.'}
              </p>
              <p className="text-sm text-ink-500">
                {bn ? 'বিস্তারিত: ' : 'Full details in the '}
                <Link to="/privacy" className="font-semibold text-brand-700 underline underline-offset-4">
                  {bn ? 'গোপনীয়তা নীতিমালা' : 'privacy policy'}
                </Link>
                .
              </p>

              <Field label={bn ? 'মোবাইল নম্বর' : 'Mobile number'} error={error} required>
                <Input
                  type="tel"
                  inputMode="numeric"
                  autoComplete="tel"
                  placeholder="01XXXXXXXXX"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && phone && sendCode()}
                />
              </Field>

              <Button onClick={sendCode} disabled={!phone || busy} className="w-full">
                {busy ? (bn ? 'পাঠানো হচ্ছে…' : 'Sending…') : bn ? 'কোড পাঠান' : 'Send code'}
              </Button>
            </Card>
          </div>
        )}

        {step === 'otp' && (
          <div>
            <div className="mb-6 text-center">
              <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-brand-50 text-brand-600">
                <Smartphone className="size-8" />
              </span>
              <h1 className="text-3xl font-extrabold text-ink-900">{bn ? 'কোড দিন' : 'Enter the code'}</h1>
              <p className="mt-1.5 text-ink-500">{n(phone)}</p>
            </div>

            <Card className="space-y-4">
              <div className="flex justify-center gap-2">
                {code.map((digit, i) => (
                  <input
                    key={i}
                    ref={(el) => { boxes.current[i] = el }}
                    value={digit}
                    onChange={(e) => onDigit(i, e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Backspace' && !code[i] && i > 0) boxes.current[i - 1]?.focus()
                    }}
                    inputMode="numeric"
                    maxLength={1}
                    className="size-12 rounded-xl border-2 border-paper-2 text-center text-xl font-bold text-ink-900 focus:border-brand-500 focus:outline-none"
                  />
                ))}
              </div>
              {error && <p className="text-center text-sm font-semibold text-red-500">{error}</p>}
              <Button onClick={() => verify()} disabled={code.some((d) => !d) || busy} className="w-full">
                {busy ? (bn ? 'যাচাই হচ্ছে…' : 'Checking…') : bn ? 'যাচাই করুন' : 'Verify'}
              </Button>
            </Card>
          </div>
        )}

        {step === 'confirm' && (
          <div>
            <div className="mb-6 text-center">
              <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-red-50 text-red-500">
                <TriangleAlert className="size-8" />
              </span>
              <h1 className="text-3xl font-extrabold text-ink-900">
                {bn ? 'শেষ নিশ্চিতকরণ' : 'Last check'}
              </h1>
            </div>

            <Card className="space-y-4">
              {preview?.refundPending && preview.amountPaid > 0 && (
                <div className="rounded-xl border-2 border-red-200 bg-red-50 p-4">
                  <p className="font-bold text-red-600">
                    {bn
                      ? `আপনি টিকিটের জন্য ${money(preview.amountPaid)} পরিশোধ করেছেন।`
                      : `You have paid ${money(preview.amountPaid)} for your ticket.`}
                  </p>
                  <p className="mt-1.5 leading-relaxed text-ink-700">
                    {bn
                      ? 'মুছে ফেললে নিবন্ধন বাতিল হবে এবং টিকিটটি আর থাকবে না। আপনার নম্বরটিও মুছে যাচ্ছে, তাই কেউ আপনাকে ফোন করে জানাতে পারবে না — এগিয়ে যাওয়ার আগে নিচের নম্বরটি লিখে রাখুন এবং ফোন করার সময় লেনদেন রেফারেন্স বলুন।'
                      : 'Deleting cancels that registration and forfeits the ticket. Your number is being deleted too, so nobody can call you back about it — write the number below down before you continue, and quote your transaction reference when you call.'}
                  </p>
                  {preview.coordinators.length > 0 && (
                    <ul className="mt-3 space-y-1">
                      {preview.coordinators.map((c) => (
                        <li key={c.id} className="font-semibold text-ink-900">
                          {bn && c.nameBn ? c.nameBn : c.name} — {n(c.phone)}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              <p className="leading-relaxed text-ink-700">
                {bn
                  ? 'এটি আর ফেরানো যাবে না। এখনই আপনার অ্যাকাউন্ট মুছে ফেলবেন?'
                  : 'This cannot be undone. Delete your account now?'}
              </p>

              {error && <p className="text-sm font-semibold text-red-500">{error}</p>}

              <div className="flex gap-3">
                <Link
                  to="/"
                  className="flex-1 rounded-xl border-2 border-paper-2 px-4 py-3 text-center font-semibold text-ink-700"
                >
                  {bn ? 'বাতিল' : 'Cancel'}
                </Link>
                <button
                  onClick={doDelete}
                  disabled={busy}
                  className="flex-1 rounded-xl bg-red-500 px-4 py-3 font-semibold text-white disabled:opacity-60"
                >
                  {busy ? (bn ? 'মুছে ফেলা হচ্ছে…' : 'Deleting…') : bn ? 'হ্যাঁ, মুছে ফেলুন' : 'Yes, delete it'}
                </button>
              </div>
            </Card>
          </div>
        )}

        {step === 'done' && (
          <div className="text-center">
            <span className="mx-auto mb-3 grid size-16 place-items-center rounded-2xl bg-brand-50 text-brand-600">
              <CheckCircle2 className="size-8" />
            </span>
            <h1 className="text-3xl font-extrabold text-ink-900">
              {bn ? 'অ্যাকাউন্ট মুছে ফেলা হয়েছে' : 'Your account is deleted'}
            </h1>
            <p className="mt-3 leading-relaxed text-ink-700">
              {bn
                ? 'আপনার তথ্য মুছে ফেলা হয়েছে। কোনো প্রশ্ন থাকলে কমিটিকে ফোন করুন।'
                : 'Your details have been erased. Call the committee if you have any questions.'}
            </p>
            <a
              href={`tel:+880${CONTACT_PHONE.replace(/^0/, '')}`}
              className="mt-5 inline-block font-semibold text-brand-700"
            >
              {CONTACT_PHONE}
            </a>
            <div className="mt-8">
              <Link to="/" className="font-semibold text-ink-500 underline underline-offset-4">
                {bn ? 'হোমে ফিরুন' : 'Back to home'}
              </Link>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
