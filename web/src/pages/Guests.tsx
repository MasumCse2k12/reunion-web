import { useEffect, useState } from 'react'
import {
  BadgeCheck,
  CheckCircle2,
  Clock,
  HandCoins,
  Phone,
  Plus,
  Send,
  Shirt,
  Ticket,
  Trash2,
  Users2,
  Wallet,
  XCircle,
} from 'lucide-react'
import {
  api,
  ApiError,
  TICKET_TYPES,
  type Application,
  type GuestRelation,
  type PaymentMethod,
  type Registration,
} from '../lib/api'
import { PAYMENT_METHODS, TSHIRT_SIZES } from '../mock/data'
import { useApp } from '../lib/store'
import { Avatar, Badge, Button, Card, Field, Input, Select, SectionTitle, Sheet, Spinner, cx } from '../components/ui'

const RELATIONS: GuestRelation[] = ['SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER']
const FOODS = ['REGULAR', 'NO_BEEF', 'VEG'] as const

type Coordinator = { id: string; name: string; nameBn: string; phone: string }

/** Ticket type follows from relation + age — the user never picks a price. */
function ticketFor(relation: GuestRelation, age?: number): string {
  if (relation === 'SPOUSE') return 'tt-spouse'
  if (relation === 'CHILD') {
    if (age !== undefined && age < 5) return 'tt-child-free'
    if (age !== undefined && age <= 12) return 'tt-child'
    return 'tt-guest'
  }
  return 'tt-guest'
}

export default function Guests() {
  const { t, lang, n, money, user } = useApp()
  const [reg, setReg] = useState<Registration | null>(null)
  const [app, setApp] = useState<Application | null>(null)
  const [coordinators, setCoordinators] = useState<Coordinator[]>([])
  const [loading, setLoading] = useState(true)
  const [sheet, setSheet] = useState(false)
  const [paySheet, setPaySheet] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [payError, setPayError] = useState('')

  // add-guest form
  const [name, setName] = useState('')
  const [relation, setRelation] = useState<GuestRelation>('SPOUSE')
  const [age, setAge] = useState('')
  const [tshirt, setTshirt] = useState('M')

  // own ticket
  const [myTshirt, setMyTshirt] = useState('L')
  const [myFood, setMyFood] = useState<Registration['foodPref']>('REGULAR')
  const [note, setNote] = useState('')

  // payment report
  const [payMethod, setPayMethod] = useState<PaymentMethod>('BKASH')
  const [payRef, setPayRef] = useState('')
  const [payTo, setPayTo] = useState('')

  useEffect(() => {
    Promise.allSettled([
      api.getRegistration(),
      api.myApplication(),
      user ? api.coordinatorsFor(user.batchYear) : Promise.resolve([] as Coordinator[]),
    ]).then(([rResult, aResult, cResult]) => {
      const r = rResult.status === 'fulfilled' ? rResult.value : null
      const a = aResult.status === 'fulfilled' ? aResult.value : null
      const c = cResult.status === 'fulfilled' ? cResult.value : []
      setReg(r)
      setApp(a)
      setCoordinators(c)
      setPayTo(c[0]?.id ?? '')
      if (r) {
        setMyTshirt(r.tshirtSize)
        setMyFood(r.foodPref)
      }
      setLoading(false)
    })
  }, [user])

  const ticket = (id: string) => TICKET_TYPES.find((x) => x.id === id) ?? TICKET_TYPES.find((x) => x.id === 'tt-guest')!
  const previewTicket = ticketFor(relation, age ? Number(age) : undefined)

  async function ensureRegistration() {
    if (reg) return reg
    const created = await api.startRegistration({ tshirtSize: myTshirt, foodPref: myFood })
    setReg(created)
    return created
  }

  async function addGuest() {
    setError('')
    if (name.trim().length < 2) {
      setError(lang === 'bn' ? 'নাম লিখুন' : 'Please enter a name')
      return
    }
    setBusy(true)
    try {
      await ensureRegistration()
      const updated = await api.addGuest({
        name: name.trim(),
        relation,
        age: age ? Number(age) : undefined,
        ticketTypeId: previewTicket,
        tshirtSize: relation === 'CHILD' ? undefined : tshirt,
      })
      setReg(updated)
      setSheet(false)
      setName('')
      setAge('')
      setRelation('SPOUSE')
    } catch (e) {
      setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: string) {
    setReg(await api.removeGuest(id))
  }

  async function submit() {
    setError('')
    setBusy(true)
    try {
      await ensureRegistration()
      const { registration, application } = await api.submitRegistration(note)
      setReg(registration)
      setApp(application)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  async function reportPayment() {
    setPayError('')
    setBusy(true)
    try {
      const updated = await api.reportPayment({
        method: payMethod,
        reference: payRef,
        amount: total,
        paidToAdminId: payTo || undefined,
      })
      setApp(updated)
      setPaySheet(false)
      setPayRef('')
    } catch (e) {
      setPayError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label={t('common.loading')} />

  const guests = reg?.guests ?? []
  const alumniTicket = ticket('tt-alumni')
  const total = (reg?.amountDue ?? alumniTicket.amount) || alumniTicket.amount
  const submitted = reg?.status === 'SUBMITTED' || reg?.status === 'APPROVED'
  const approved = app?.memberStatus === 'APPROVED'
  const rejected = app?.memberStatus === 'REJECTED'
  const locked = submitted && !rejected

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-extrabold text-ink-900">{t('guests.title')}</h1>
        <p className="mt-1 text-ink-500">{t('guests.sub')}</p>
      </div>

      {/* ---------- Review status — only once this registration has actually been sent ---------- */}
      {app && reg && reg.status !== 'DRAFT' && (
        <Card
          className={cx(
            'animate-pop',
            approved
              ? 'border-brand-300 bg-brand-50'
              : rejected
                ? 'border-red-200 bg-red-50'
                : 'border-gold-300 bg-gold-50',
          )}
        >
          <div className="flex items-start gap-3">
            {approved ? (
              <BadgeCheck className="mt-0.5 size-7 shrink-0 text-brand-600" />
            ) : rejected ? (
              <XCircle className="mt-0.5 size-7 shrink-0 text-red-600" />
            ) : (
              <Clock className="mt-0.5 size-7 shrink-0 text-gold-600" />
            )}
            <div className="min-w-0">
              <h2 className="text-lg font-extrabold text-ink-900">
                {approved ? t('guests.approved') : rejected ? t('guests.rejected') : t('guests.submitted')}
              </h2>
              <p className="mt-0.5 text-ink-600">
                {approved ? t('guests.approvedSub') : rejected ? '' : t('guests.submittedSub')}
              </p>
              {app.memberReview?.note && (
                <p className="mt-2 rounded-xl bg-white/70 px-3 py-2 text-sm text-ink-600">
                  <span className="font-semibold">{app.memberReview.adminName}:</span> {app.memberReview.note}
                </p>
              )}
              <p className="mt-2 font-mono text-sm text-ink-400">#{app.id.toUpperCase()}</p>
            </div>
          </div>
        </Card>
      )}

      {/* ---------- Your own ticket ---------- */}
      <Card>
        <SectionTitle>
          <span className="inline-flex items-center gap-2">
            <Ticket className="size-5 text-brand-600" />
            {t('guests.yourTicket')}
          </span>
        </SectionTitle>

        <div className="flex items-center justify-between gap-3 rounded-xl bg-brand-50 px-4 py-3">
          <span className="font-bold text-brand-800">{lang === 'bn' ? alumniTicket.nameBn : alumniTicket.nameEn}</span>
          <span className="font-extrabold tabular-nums text-brand-800">{money(alumniTicket.amount)}</span>
        </div>
        <p className="mt-1.5 text-sm text-ink-400">{lang === 'bn' ? alumniTicket.noteBn : alumniTicket.noteEn}</p>

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <Field label={t('guests.tshirt')}>
            <Select
              value={myTshirt}
              disabled={locked}
              onChange={(e) => {
                setMyTshirt(e.target.value)
                if (reg) api.startRegistration({ tshirtSize: e.target.value, foodPref: myFood }).then(setReg)
              }}
            >
              {TSHIRT_SIZES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('guests.food')}>
            <Select
              value={myFood}
              disabled={locked}
              onChange={(e) => {
                const v = e.target.value as Registration['foodPref']
                setMyFood(v)
                if (reg) api.startRegistration({ tshirtSize: myTshirt, foodPref: v }).then(setReg)
              }}
            >
              {FOODS.map((f) => (
                <option key={f} value={f}>
                  {t(`guests.food.${f}` as never)}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Card>

      {/* ---------- Family members ---------- */}
      <section>
        <SectionTitle
          action={
            !locked && (
              <Button size="sm" icon={<Plus className="size-5" />} onClick={() => setSheet(true)}>
                {t('guests.addMember')}
              </Button>
            )
          }
        >
          <span className="inline-flex items-center gap-2">
            <Users2 className="size-5 text-brand-600" />
            {t('nav.guests')}
            {guests.length > 0 && <Badge tone="green">{n(guests.length)}</Badge>}
          </span>
        </SectionTitle>

        {guests.length === 0 ? (
          <Card className="border-dashed py-8 text-center">
            <Users2 className="mx-auto mb-2 size-10 text-ink-400/50" />
            <p className="font-semibold text-ink-500">{t('guests.none')}</p>
            {!locked && (
              <Button variant="outline" className="mt-4" icon={<Plus className="size-5" />} onClick={() => setSheet(true)}>
                {t('guests.addMember')}
              </Button>
            )}
          </Card>
        ) : (
          <div className="space-y-2">
            {guests.map((g) => {
              const tt = ticket(g.ticketTypeId)
              return (
                <Card key={g.id} className="flex items-center gap-3 p-3">
                  <Avatar name={g.name} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-bold text-ink-900">{g.name}</div>
                    <div className="flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-sm text-ink-400">
                      <span>{t(`guests.rel.${g.relation}` as never)}</span>
                      {g.age !== undefined && (
                        <span className="tabular-nums">
                          · {n(g.age)} {lang === 'bn' ? 'বছর' : 'yrs'}
                        </span>
                      )}
                      {g.tshirtSize && (
                        <span className="inline-flex items-center gap-1">
                          · <Shirt className="size-3.5" />
                          {g.tshirtSize}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="shrink-0 text-right">
                    <div className="font-extrabold tabular-nums text-ink-900">
                      {tt.amount === 0 ? (
                        <span className="text-brand-600">{t('guests.free')}</span>
                      ) : (
                        money(tt.amount)
                      )}
                    </div>
                    {!locked && (
                      <button
                        onClick={() => remove(g.id)}
                        className="mt-0.5 inline-flex min-h-0 items-center gap-1 text-sm font-semibold text-red-600 hover:underline"
                      >
                        <Trash2 className="size-4" />
                        {t('cta.remove')}
                      </button>
                    )}
                  </div>
                </Card>
              )
            })}
          </div>
        )}
      </section>

      {/* ---------- Cost summary ---------- */}
      <Card className="bg-ink-900 text-white">
        <SectionTitle>
          <span className="inline-flex items-center gap-2 text-white">
            <Wallet className="size-5 text-gold-300" />
            {t('guests.summary')}
          </span>
        </SectionTitle>

        <div className="space-y-2 border-b border-white/15 pb-3">
          <div className="flex justify-between gap-3">
            <span className="text-white/75">{lang === 'bn' ? alumniTicket.nameBn : alumniTicket.nameEn}</span>
            <span className="font-semibold tabular-nums">{money(alumniTicket.amount)}</span>
          </div>
          {guests.map((g) => {
            const tt = ticket(g.ticketTypeId)
            return (
              <div key={g.id} className="flex justify-between gap-3">
                <span className="min-w-0 truncate text-white/75">
                  {g.name} <span className="text-white/40">· {t(`guests.rel.${g.relation}` as never)}</span>
                </span>
                <span className="shrink-0 font-semibold tabular-nums">
                  {tt.amount === 0 ? t('guests.free') : money(tt.amount)}
                </span>
              </div>
            )
          })}
        </div>

        <div className="mt-3 flex items-end justify-between gap-3">
          <span className="text-lg font-bold">{t('guests.total')}</span>
          <span className="text-3xl font-extrabold tabular-nums text-gold-300">{money(total)}</span>
        </div>

        {!locked ? (
          <>
            <Field label={t('guests.note')}>
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                rows={2}
                className="mt-1 w-full rounded-xl border-2 border-white/15 bg-white/10 px-4 py-3 text-white placeholder:text-white/40 focus:border-gold-300 focus:outline-none"
              />
            </Field>
            {error && <p className="mt-2 font-medium text-red-300">{error}</p>}
            <Button variant="gold" size="lg" full className="mt-3" loading={busy} icon={<Send className="size-5" />} onClick={submit}>
              {t('cta.submit')}
            </Button>
          </>
        ) : (
          <div className="mt-4 rounded-xl bg-white/10 px-4 py-3 text-center font-semibold text-gold-200">
            <CheckCircle2 className="mr-1.5 inline size-5" />
            {approved ? t('guests.approved') : t('guests.submitted')}
          </div>
        )}
      </Card>

      {/* ---------- How to pay — offline, to a named human ---------- */}
      {locked && (
        <Card>
          <SectionTitle>
            <span className="inline-flex items-center gap-2">
              <HandCoins className="size-5 text-gold-600" />
              {t('guests.howToPay')}
            </span>
          </SectionTitle>
          <p className="text-ink-500">{t('guests.howToPaySub')}</p>

          <div className="mt-4 space-y-2">
            <p className="font-semibold text-ink-700">{t('guests.coordinator')}</p>
            {coordinators.map((c) => (
              <div key={c.id} className="flex items-center gap-3 rounded-xl bg-paper-2/70 px-3 py-2.5">
                <Avatar name={c.name} size="sm" />
                <span className="min-w-0 flex-1 truncate font-semibold text-ink-700">
                  {lang === 'bn' ? c.nameBn : c.name}
                </span>
                <a
                  href={`tel:+88${c.phone}`}
                  className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-brand-600 px-3.5 py-1.5 text-sm font-semibold text-white"
                >
                  <Phone className="size-4" />
                  <span className="tabular-nums">{n(c.phone)}</span>
                </a>
              </div>
            ))}
          </div>

          {/* Payment status — reported by the member, confirmed by the coordinator */}
          {app?.paymentStatus === 'CONFIRMED' ? (
            <p className="mt-4 rounded-xl bg-brand-50 px-4 py-3 text-center font-semibold text-brand-700 ring-1 ring-brand-200">
              <CheckCircle2 className="mr-1.5 inline size-5" />
              {t('guests.payConfirmed')}
            </p>
          ) : app?.paymentStatus === 'REPORTED' ? (
            <div className="mt-4 rounded-xl bg-gold-50 px-4 py-3 text-center font-semibold text-gold-700 ring-1 ring-gold-200">
              <Clock className="mr-1.5 inline size-5" />
              {t('guests.payReported')}
              {app.payment && (
                <div className="mt-1 font-mono text-sm font-normal">
                  {t(`pay.${app.payment.method}` as never)} · {app.payment.reference}
                </div>
              )}
            </div>
          ) : (
            <>
              {app?.paymentStatus === 'REJECTED' && (
                <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 font-semibold text-red-700 ring-1 ring-red-200">
                  <XCircle className="mr-1.5 inline size-5" />
                  {t('guests.payRejected')}
                  {app.paymentReview?.note && <span className="block font-normal">{app.paymentReview.note}</span>}
                </p>
              )}
              <Button variant="outline" full className="mt-4" icon={<HandCoins className="size-5" />} onClick={() => setPaySheet(true)}>
                {t('guests.reportPayment')}
              </Button>
            </>
          )}
        </Card>
      )}

      {/* ---------- Add-member sheet ---------- */}
      <Sheet open={sheet} onClose={() => setSheet(false)} title={t('guests.addMember')}>
        <div className="space-y-4">
          <Field label={t('guests.name')} error={error} required>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={lang === 'bn' ? 'পুরো নাম' : 'Full name'} />
          </Field>

          <Field label={t('guests.relation')} required>
            <div className="grid grid-cols-2 gap-2">
              {RELATIONS.map((r) => (
                <button
                  key={r}
                  onClick={() => setRelation(r)}
                  className={cx(
                    'rounded-xl border-2 px-3 py-2.5 font-semibold transition',
                    relation === r
                      ? 'border-brand-600 bg-brand-600 text-white'
                      : 'border-paper-2 bg-white text-ink-600 hover:border-brand-300',
                  )}
                >
                  {t(`guests.rel.${r}` as never)}
                </button>
              ))}
            </div>
          </Field>

          {relation === 'CHILD' && (
            <Field
              label={t('guests.age')}
              hint={lang === 'bn' ? '৫ বছরের নিচে হলে বিনামূল্যে' : 'Under 5 years is free'}
              required
            >
              <Input
                type="number"
                inputMode="numeric"
                value={age}
                onChange={(e) => setAge(e.target.value)}
                placeholder="8"
                className="text-center text-xl font-bold tabular-nums"
                min={0}
                max={30}
              />
            </Field>
          )}

          {relation !== 'CHILD' && (
            <Field label={t('guests.tshirt')}>
              <Select value={tshirt} onChange={(e) => setTshirt(e.target.value)}>
                {TSHIRT_SIZES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </Select>
            </Field>
          )}

          {/* Live price feedback — no surprises later */}
          <div className="flex items-center justify-between rounded-xl bg-gold-50 px-4 py-3 ring-1 ring-gold-200">
            <span className="font-semibold text-gold-700">
              {lang === 'bn' ? ticket(previewTicket).nameBn : ticket(previewTicket).nameEn}
            </span>
            <span className="text-xl font-extrabold tabular-nums text-gold-700">
              {ticket(previewTicket).amount === 0 ? t('guests.free') : money(ticket(previewTicket).amount)}
            </span>
          </div>

          <Button full size="lg" loading={busy} icon={<Plus className="size-5" />} onClick={addGuest}>
            {t('cta.add')}
          </Button>
        </div>
      </Sheet>

      {/* ---------- Report-payment sheet ---------- */}
      <Sheet open={paySheet} onClose={() => setPaySheet(false)} title={t('guests.reportPayment')}>
        <div className="space-y-4">
          <Field label={t('guests.payMethod')} required>
            <Select value={payMethod} onChange={(e) => setPayMethod(e.target.value as PaymentMethod)}>
              {PAYMENT_METHODS.map((m) => (
                <option key={m} value={m}>
                  {t(`pay.${m}` as never)}
                </option>
              ))}
            </Select>
          </Field>

          {coordinators.length > 0 && (
            <Field label={t('guests.coordinator')}>
              <Select value={payTo} onChange={(e) => setPayTo(e.target.value)}>
                {coordinators.map((c) => (
                  <option key={c.id} value={c.id}>
                    {lang === 'bn' ? c.nameBn : c.name}
                  </option>
                ))}
              </Select>
            </Field>
          )}

          <Field
            label={t('guests.payRef')}
            error={payError}
            hint={lang === 'bn' ? 'বিকাশ/নগদ TrxID অথবা ব্যাংক স্লিপ নম্বর' : 'bKash/Nagad TrxID or bank slip number'}
            required
          >
            <Input value={payRef} onChange={(e) => setPayRef(e.target.value)} placeholder="TRX123456" className="font-mono" />
          </Field>

          <div className="flex items-center justify-between rounded-xl bg-paper-2 px-4 py-3">
            <span className="font-semibold text-ink-600">{t('guests.payAmount')}</span>
            <span className="text-xl font-extrabold tabular-nums text-ink-900">{money(total)}</span>
          </div>

          <Button full size="lg" loading={busy} icon={<Send className="size-5" />} onClick={reportPayment}>
            {t('cta.submit')}
          </Button>
        </div>
      </Sheet>
    </div>
  )
}
