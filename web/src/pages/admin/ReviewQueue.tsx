import { useCallback, useEffect, useState } from 'react'
import { Check, Clock, Search, Users2, Wallet, X } from 'lucide-react'
import {
  adminApi,
  ApiError,
  type Application,
  type PaymentStatus,
  type ReviewStatus,
} from '../../lib/api'
import { useApp, type TKey } from '../../lib/store'
import { Avatar, Badge, Button, Card, Field, Input, SectionTitle, Select, Sheet, Spinner, cx } from '../../components/ui'

type Mode = 'MEMBER' | 'PAYMENT'

const MEMBER_STATUSES: (ReviewStatus | 'ALL')[] = ['PENDING', 'APPROVED', 'REJECTED', 'ALL']
const PAYMENT_STATUSES: (PaymentStatus | 'ALL')[] = ['REPORTED', 'UNPAID', 'CONFIRMED', 'REJECTED', 'ALL']

const MEMBER_LABEL: Record<ReviewStatus, TKey> = {
  PENDING: 'dash.awaitingReview',
  APPROVED: 'dash.registered',
  REJECTED: 'dash.rejected',
}

const PAYMENT_LABEL: Record<PaymentStatus, TKey> = {
  UNPAID: 'admin.noPaymentYet',
  REPORTED: 'guests.payReported',
  CONFIRMED: 'guests.payConfirmed',
  REJECTED: 'guests.payRejected',
}

function memberTone(s: ReviewStatus) {
  return s === 'APPROVED' ? 'green' : s === 'REJECTED' ? 'red' : 'gold'
}

function paymentTone(s: PaymentStatus) {
  return s === 'CONFIRMED' ? 'green' : s === 'REJECTED' ? 'red' : s === 'REPORTED' ? 'gold' : 'muted'
}

export default function ReviewQueue({ mode }: { mode: Mode }) {
  const { t, lang, n, yr, money } = useApp()

  const [apps, setApps] = useState<Application[] | null>(null)
  const [batches, setBatches] = useState<number[]>([])
  const [query, setQuery] = useState('')
  const [batchYear, setBatchYear] = useState<'ALL' | number>('ALL')
  const [status, setStatus] = useState<string>(mode === 'MEMBER' ? 'PENDING' : 'REPORTED')

  const [open, setOpen] = useState<Application | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    adminApi.myBatches().then(setBatches)
  }, [])

  const load = useCallback(async () => {
    const list = await adminApi.applications({
      query,
      batchYear,
      ...(mode === 'MEMBER'
        ? { memberStatus: status as ReviewStatus | 'ALL' }
        : { paymentStatus: status as PaymentStatus | 'ALL' }),
    })
    setApps(list)
  }, [mode, query, batchYear, status])

  useEffect(() => {
    setApps(null)
    load()
  }, [load])

  async function decide(verdict: 'APPROVE' | 'REJECT') {
    if (!open) return
    setError('')
    setBusy(true)
    try {
      const updated =
        mode === 'MEMBER'
          ? await adminApi.reviewMember(open.id, verdict === 'APPROVE' ? 'APPROVED' : 'REJECTED', note)
          : await adminApi.reviewPayment(open.id, verdict === 'APPROVE' ? 'CONFIRMED' : 'REJECTED', note)
      setOpen(null)
      setNote('')
      // Reload rather than patch in place — the row usually leaves the current filter.
      setApps((prev) => prev?.map((a) => (a.id === updated.id ? updated : a)) ?? null)
      load()
    } catch (e) {
      setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  const statuses: string[] = mode === 'MEMBER' ? MEMBER_STATUSES : PAYMENT_STATUSES
  const statusLabel = (s: string): string =>
    s === 'ALL'
      ? t('admin.all')
      : mode === 'MEMBER'
        ? t(MEMBER_LABEL[s as ReviewStatus])
        : t(PAYMENT_LABEL[s as PaymentStatus])

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-extrabold text-ink-900">{t(mode === 'MEMBER' ? 'admin.members' : 'admin.payments')}</h1>

      {/* ---------- Filters ---------- */}
      <Card className="grid gap-3 sm:grid-cols-3">
        <div className="relative sm:col-span-3 lg:col-span-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-ink-400" />
          <Input
            placeholder={t('admin.search')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="pl-11"
          />
        </div>

        <Field label={t('admin.filterBatch')}>
          <Select
            value={String(batchYear)}
            onChange={(e) => setBatchYear(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
          >
            <option value="ALL">{t('admin.all')}</option>
            {batches.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </Select>
        </Field>

        <Field label={t('admin.filterStatus')}>
          <Select value={status} onChange={(e) => setStatus(e.target.value)}>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {statusLabel(s)}
              </option>
            ))}
          </Select>
        </Field>
      </Card>

      {/* ---------- Queue ---------- */}
      {!apps ? (
        <Spinner label={t('common.loading')} />
      ) : apps.length === 0 ? (
        <Card className="border-dashed py-10 text-center">
          <Clock className="mx-auto mb-2 size-10 text-ink-400/50" />
          <p className="font-semibold text-ink-500">{t('admin.noResults')}</p>
        </Card>
      ) : (
        <div className="space-y-2">
          {apps.map((a) => (
            <Card key={a.id} className="flex flex-wrap items-center gap-3 p-3">
              <Avatar name={a.name} />
              <div className="min-w-0 flex-1">
                <div className="truncate font-bold text-ink-900">{lang === 'bn' ? a.nameBn : a.name}</div>
                <div className="truncate text-sm text-ink-400">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(a.batchYear)} · <span className="tabular-nums">{n(a.phone)}</span>
                  {a.guests.length > 0 && (
                    <>
                      {' '}
                      · <Users2 className="inline size-3.5" /> {n(a.guests.length)}
                    </>
                  )}
                </div>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  <Badge tone={memberTone(a.memberStatus)}>{t(MEMBER_LABEL[a.memberStatus])}</Badge>
                  <Badge tone={paymentTone(a.paymentStatus)}>
                    <Wallet className="size-3.5" />
                    {t(PAYMENT_LABEL[a.paymentStatus])}
                  </Badge>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span className="font-extrabold tabular-nums text-ink-900">{money(a.amountDue)}</span>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setOpen(a)
                    setNote('')
                    setError('')
                  }}
                >
                  {t('admin.details')}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* ---------- Detail + decision ---------- */}
      <Sheet open={!!open} onClose={() => setOpen(null)} title={t('admin.details')}>
        {open && (
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <Avatar name={open.name} size="lg" />
              <div className="min-w-0">
                <div className="truncate text-lg font-extrabold text-ink-900">
                  {lang === 'bn' ? open.nameBn : open.name}
                </div>
                <div className="text-ink-500">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(open.batchYear)}
                </div>
              </div>
            </div>

            <Card className="space-y-1.5 p-4">
              <Row label={t('auth.phone')} value={n(open.phone)} />
              <Row label={t('profile.email')} value={open.email ?? t('common.notSet')} />
              <Row label={t('profile.gender')} value={open.gender ? t(`gender.${open.gender}` as never) : t('common.notSet')} />
              <Row label={t('profile.dob')} value={open.dob ? n(open.dob) : t('common.notSet')} />
              <Row label={t('profile.blood')} value={open.bloodGroup ?? t('common.notSet')} />
              <Row label={t('profile.occupation')} value={open.occupation ?? t('common.notSet')} />
              <Row label={t('profile.city')} value={open.city ?? t('common.notSet')} />
              <Row label={t('admin.submittedOn')} value={n(open.submittedAt.slice(0, 10))} />
            </Card>

            {open.memberNote && (
              <Card className="border-gold-200 bg-gold-50 p-4">
                <p className="text-sm font-bold text-gold-700">{t('admin.memberNote')}</p>
                <p className="mt-1 text-ink-700">{open.memberNote}</p>
              </Card>
            )}

            {open.guests.length > 0 && (
              <div>
                <SectionTitle>
                  <span className="text-base">
                    {t('admin.familyMembers')} ({n(open.guests.length)})
                  </span>
                </SectionTitle>
                <div className="space-y-1.5">
                  {open.guests.map((g) => (
                    <div key={g.id} className="flex items-center gap-2 rounded-xl bg-paper-2/70 px-3 py-2">
                      <span className="min-w-0 flex-1 truncate font-semibold text-ink-700">{g.name}</span>
                      <span className="shrink-0 text-sm text-ink-400">
                        {t(`guests.rel.${g.relation}` as never)}
                        {g.age !== undefined && ` · ${n(g.age)}`}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="flex items-center justify-between rounded-xl bg-ink-900 px-4 py-3 text-white">
              <span className="font-semibold">{t('guests.total')}</span>
              <span className="text-2xl font-extrabold tabular-nums text-gold-300">{money(open.amountDue)}</span>
            </div>

            {/* What the member says they paid — the coordinator matches it by hand */}
            <Card className="p-4">
              <p className="text-sm font-bold text-ink-500">{t('admin.paymentReported')}</p>
              {open.payment ? (
                <div className="mt-1.5 space-y-1">
                  <Row label={t('guests.payMethod')} value={t(`pay.${open.payment.method}` as never)} />
                  <Row label={t('guests.payRef')} value={open.payment.reference} mono />
                  <Row label={t('guests.payAmount')} value={money(open.payment.amount)} />
                </div>
              ) : (
                <p className="mt-1 text-ink-400">{t('admin.noPaymentYet')}</p>
              )}
              <div className="mt-2 flex flex-wrap gap-1.5">
                <Badge tone={memberTone(open.memberStatus)}>{t(MEMBER_LABEL[open.memberStatus])}</Badge>
                <Badge tone={paymentTone(open.paymentStatus)}>{t(PAYMENT_LABEL[open.paymentStatus])}</Badge>
              </div>
            </Card>

            {(open.memberReview || open.paymentReview) && (
              <Card className="p-4 text-sm text-ink-500">
                {[open.memberReview, open.paymentReview].filter(Boolean).map((r, i) => (
                  <p key={i} className={cx(i > 0 && 'mt-1.5')}>
                    <span className="font-semibold text-ink-700">
                      {t('admin.reviewedBy')}: {r!.adminName}
                    </span>
                    {r!.note && <span className="block">“{r!.note}”</span>}
                  </p>
                ))}
              </Card>
            )}

            {/* ---- Decision ---- */}
            <Field label={mode === 'MEMBER' ? t('admin.reviewNote') : t('admin.reviewNote')} error={error}>
              <textarea
                rows={2}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder={t('admin.rejectReason')}
                className="w-full rounded-xl border-2 border-paper-2 bg-white px-4 py-3 text-ink-900 placeholder:text-ink-400 focus:border-brand-400 focus:outline-none"
              />
            </Field>

            <div className="grid grid-cols-2 gap-2">
              <Button variant="danger" size="lg" loading={busy} icon={<X className="size-5" />} onClick={() => decide('REJECT')}>
                {t('cta.reject')}
              </Button>
              <Button
                size="lg"
                loading={busy}
                disabled={mode === 'PAYMENT' && !open.payment}
                icon={<Check className="size-5" />}
                onClick={() => decide('APPROVE')}
              >
                {mode === 'MEMBER' ? t('cta.approve') : t('cta.confirm')}
              </Button>
            </div>
          </div>
        )}
      </Sheet>
    </div>
  )
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="shrink-0 text-sm text-ink-400">{label}</span>
      <span className={cx('min-w-0 truncate text-right font-semibold text-ink-700', mono && 'font-mono')}>{value}</span>
    </div>
  )
}
