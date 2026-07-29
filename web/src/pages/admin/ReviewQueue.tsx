import { useCallback, useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Check, Clock, Search, Users2, Wallet, X } from 'lucide-react'
import {
  adminApi,
  ApiError,
  PAGE_SIZE,
  type Application,
  type BulkReviewResult,
  type PaymentStatus,
  type ReviewStatus,
} from '../../lib/api'
import { useApp, type Lang, type TKey } from '../../lib/store'
import {
  Avatar,
  Badge,
  Button,
  Card,
  Checkbox,
  Field,
  Input,
  SectionTitle,
  Select,
  Sheet,
  Spinner,
  cx,
} from '../../components/ui'

type Mode = 'MEMBER' | 'PAYMENT'
type Verdict = 'APPROVE' | 'REJECT'

const MEMBER_STATUSES: (ReviewStatus | 'ALL')[] = ['PENDING', 'APPROVED', 'REJECTED', 'ALL']
const PAYMENT_STATUSES: (PaymentStatus | 'ALL')[] = ['REPORTED', 'UNPAID', 'CONFIRMED', 'REJECTED', 'ALL']

/** How many names the bulk confirmation lists before it stops and counts the rest. */
const NAME_PREVIEW = 8

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
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [total, setTotal] = useState(0)
  const [more, setMore] = useState(false)
  const [batches, setBatches] = useState<number[]>([])
  const [query, setQuery] = useState('')
  const [batchYear, setBatchYear] = useState<'ALL' | number>('ALL')
  const [status, setStatus] = useState<string>(mode === 'MEMBER' ? 'PENDING' : 'REPORTED')

  const [open, setOpen] = useState<Application | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  /* ---- bulk selection ---- */
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulk, setBulk] = useState<Verdict | null>(null)
  const [bulkNote, setBulkNote] = useState('')
  const [bulkBusy, setBulkBusy] = useState(false)
  const [bulkError, setBulkError] = useState('')
  const [result, setResult] = useState<BulkReviewResult | null>(null)

  /** "5 selected" in English, "৫টি নির্বাচিত" in Bangla — the space differs. */
  const withCount = (v: number, k: TKey) => (lang === 'bn' ? `${n(v)}${t(k)}` : `${n(v)} ${t(k)}`)

  useEffect(() => {
    adminApi.myBatches().then(setBatches)
  }, [])

  const fetchPage = useCallback(
    (cursor: string | null, limit?: number) =>
      adminApi.applications({
        query,
        batchYear,
        cursor,
        limit,
        ...(mode === 'MEMBER'
          ? { memberStatus: status as ReviewStatus | 'ALL' }
          : { paymentStatus: status as PaymentStatus | 'ALL' }),
      }),
    [mode, query, batchYear, status],
  )

  /**
   * Load from the top. `keep` re-requests as many rows as the admin had already
   * pulled in, so acting on a row twelve pages down does not throw them back to
   * the first page.
   */
  const load = useCallback(
    async (keep?: number) => {
      const page = await fetchPage(null, keep)
      setApps(page.items)
      setNextCursor(page.nextCursor)
      setTotal(page.total)
    },
    [fetchPage],
  )

  useEffect(() => {
    setApps(null)
    // A tick that is no longer on screen must not be acted on later.
    setSelected(new Set())
    // The last outcome described the previous filter's rows, not these.
    setResult(null)
    load()
  }, [load])

  async function loadMore() {
    if (!nextCursor || more) return
    setMore(true)
    try {
      const page = await fetchPage(nextCursor)
      setApps((prev) => [...(prev ?? []), ...page.items])
      setNextCursor(page.nextCursor)
      setTotal(page.total)
    } finally {
      setMore(false)
    }
  }

  const selectedApps = useMemo(() => apps?.filter((a) => selected.has(a.id)) ?? [], [apps, selected])

  /** Payment rows with nothing reported yet cannot be confirmed — warn before, not after. */
  const unpayable = mode === 'PAYMENT' ? selectedApps.filter((a) => !a.payment).length : 0

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  function toggleAll(on: boolean) {
    setSelected(on ? new Set((apps ?? []).map((a) => a.id)) : new Set())
  }

  /** How many rows to pull back after a decision — never fewer than one page. */
  const openWindow = () => Math.max(PAGE_SIZE, apps?.length ?? 0)

  async function decide(verdict: Verdict) {
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
      load(openWindow())
    } catch (e) {
      setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  async function decideMany(verdict: Verdict) {
    const ids = [...selected]
    setBulkError('')
    setBulkBusy(true)
    try {
      const outcome =
        mode === 'MEMBER'
          ? await adminApi.reviewMembersBulk(ids, verdict === 'APPROVE' ? 'APPROVED' : 'REJECTED', bulkNote)
          : await adminApi.reviewPaymentsBulk(ids, verdict === 'APPROVE' ? 'CONFIRMED' : 'REJECTED', bulkNote)
      setBulk(null)
      setBulkNote('')
      setSelected(new Set())
      setResult(outcome)
      load(openWindow())
    } catch (e) {
      setBulkError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBulkBusy(false)
    }
  }

  const statuses: string[] = mode === 'MEMBER' ? MEMBER_STATUSES : PAYMENT_STATUSES
  const statusLabel = (s: string): string =>
    s === 'ALL'
      ? t('admin.all')
      : mode === 'MEMBER'
        ? t(MEMBER_LABEL[s as ReviewStatus])
        : t(PAYMENT_LABEL[s as PaymentStatus])

  const approveLabel = mode === 'MEMBER' ? t('cta.approve') : t('cta.confirm')
  const bulkApproveLabel = mode === 'MEMBER' ? t('admin.approveSelected') : t('admin.confirmSelected')
  const allSelected = !!apps && apps.length > 0 && selected.size === apps.length
  const hasMore = !!nextCursor
  // "Select all" only tells the truth when everything matching is already on screen.
  const selectAllLabel = hasMore ? t('admin.selectLoaded') : t('admin.selectAll')

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

      {/* ---------- Outcome of the last bulk decision ---------- */}
      {result && <BulkSummary result={result} onClose={() => setResult(null)} withCount={withCount} lang={lang} />}

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
          {/* Select-all header. It ticks the rows that are actually on screen — never
              the unloaded remainder, which nobody has read. The label says so, and the
              count next to it is loaded-of-total. */}
          <div className="flex items-center gap-1 px-1">
            <Checkbox
              checked={allSelected}
              indeterminate={selected.size > 0 && !allSelected}
              onChange={toggleAll}
              label={selectAllLabel}
            />
            <button
              type="button"
              onClick={() => toggleAll(!allSelected)}
              className="font-semibold text-ink-500 hover:text-ink-700"
            >
              {selectAllLabel} ({n(apps.length)}
              {hasMore && ` / ${n(total)}`})
            </button>
          </div>

          {apps.map((a) => {
            const picked = selected.has(a.id)
            return (
              <Card
                key={a.id}
                className={cx(
                  'flex flex-wrap items-center gap-1.5 p-3 transition',
                  picked && 'border-brand-300 bg-brand-50/60 ring-1 ring-brand-200',
                )}
              >
                <Checkbox checked={picked} onChange={() => toggle(a.id)} label={t('admin.selectRow')} />
                <Avatar name={a.name} />
                <div className="ml-1.5 min-w-0 flex-1">
                  <div className="truncate font-bold text-ink-900">{lang === 'bn' ? a.nameBn : a.name}</div>
                  <div className="truncate text-sm text-ink-400">
                    {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(a.batchYear)} ·{' '}
                    <span className="tabular-nums">{n(a.phone)}</span>
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
            )
          })}

          {/* Load more, rather than numbered pages: this queue is worked top to
              bottom on a phone, and a page number is a place you have to keep. */}
          {hasMore && (
            <div className="pt-2 text-center">
              <Button variant="outline" size="lg" loading={more} onClick={loadMore}>
                {t('cta.loadMore')} ({n(total - apps.length)})
              </Button>
            </div>
          )}
        </div>
      )}

      {/* ---------- Bulk action bar ---------- */}
      {selected.size > 0 && (
        <>
          {/* Spacer so the fixed bar never covers the last row */}
          <div className="h-24" aria-hidden />
          <div className="fixed inset-x-0 bottom-0 z-40 border-t border-paper-2 bg-white/95 p-3 shadow-[0_-4px_16px_rgba(0,0,0,0.06)] backdrop-blur">
            <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-2">
              <span className="font-extrabold text-ink-900">{withCount(selected.size, 'admin.selectedCount')}</span>
              <button
                type="button"
                onClick={() => setSelected(new Set())}
                className="font-semibold text-ink-400 underline hover:text-ink-700"
              >
                {t('admin.clearSelection')}
              </button>
              <div className="ml-auto flex gap-2">
                <Button
                  variant="danger"
                  icon={<X className="size-5" />}
                  onClick={() => {
                    setBulk('REJECT')
                    setBulkNote('')
                    setBulkError('')
                  }}
                >
                  {t('admin.rejectSelected')}
                </Button>
                <Button
                  icon={<Check className="size-5" />}
                  onClick={() => {
                    setBulk('APPROVE')
                    setBulkNote('')
                    setBulkError('')
                  }}
                >
                  {bulkApproveLabel}
                </Button>
              </div>
            </div>
          </div>
        </>
      )}

      {/* ---------- Bulk confirmation ---------- */}
      <Sheet
        open={!!bulk}
        onClose={() => setBulk(null)}
        title={
          bulk === 'REJECT'
            ? t('admin.bulkRejectTitle')
            : mode === 'MEMBER'
              ? t('admin.bulkApproveTitle')
              : t('admin.bulkConfirmTitle')
        }
      >
        {bulk && (
          <div className="space-y-4">
            <p className="font-semibold text-ink-700">{withCount(selectedApps.length, 'admin.selectedCount')}</p>

            <Card className="max-h-56 space-y-1 overflow-y-auto p-4">
              {selectedApps.slice(0, NAME_PREVIEW).map((a) => (
                <div key={a.id} className="flex items-baseline justify-between gap-3">
                  <span className="min-w-0 truncate font-semibold text-ink-700">
                    {lang === 'bn' ? a.nameBn : a.name}
                  </span>
                  <span className="shrink-0 text-sm text-ink-400">{yr(a.batchYear)}</span>
                </div>
              ))}
              {selectedApps.length > NAME_PREVIEW && (
                <p className="pt-1 text-sm text-ink-400">
                  + {withCount(selectedApps.length - NAME_PREVIEW, 'admin.andMore')}
                </p>
              )}
            </Card>

            {bulk === 'APPROVE' && unpayable > 0 && (
              <Card className="flex gap-2.5 border-gold-200 bg-gold-50 p-4">
                <AlertTriangle className="mt-0.5 size-5 shrink-0 text-gold-700" />
                <p className="text-sm font-semibold text-ink-700">{withCount(unpayable, 'admin.bulkWarnUnpaid')}</p>
              </Card>
            )}

            <Field label={t('admin.reviewNote')} hint={t('admin.bulkNoteHint')} error={bulkError}>
              <textarea
                rows={2}
                value={bulkNote}
                onChange={(e) => setBulkNote(e.target.value)}
                placeholder={t('admin.rejectReason')}
                className="w-full rounded-xl border-2 border-paper-2 bg-white px-4 py-3 text-ink-900 placeholder:text-ink-400 focus:border-brand-400 focus:outline-none"
              />
            </Field>

            <div className="grid grid-cols-2 gap-2">
              <Button variant="outline" size="lg" onClick={() => setBulk(null)}>
                {t('cta.cancel')}
              </Button>
              {bulk === 'REJECT' ? (
                <Button
                  variant="danger"
                  size="lg"
                  loading={bulkBusy}
                  icon={<X className="size-5" />}
                  onClick={() => decideMany('REJECT')}
                >
                  {t('cta.reject')}
                </Button>
              ) : (
                <Button
                  size="lg"
                  loading={bulkBusy}
                  icon={<Check className="size-5" />}
                  onClick={() => decideMany('APPROVE')}
                >
                  {approveLabel}
                </Button>
              )}
            </div>
          </div>
        )}
      </Sheet>

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
            <Field label={t('admin.reviewNote')} error={error}>
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
                {approveLabel}
              </Button>
            </div>
          </div>
        )}
      </Sheet>
    </div>
  )
}

/** What the bulk decision actually did — including, by name, what it refused to do. */
function BulkSummary({
  result,
  onClose,
  withCount,
  lang,
}: {
  result: BulkReviewResult
  onClose: () => void
  withCount: (v: number, k: TKey) => string
  lang: Lang
}) {
  const clean = result.skipped.length === 0
  return (
    <Card
      className={cx('flex gap-3 p-4', clean ? 'border-brand-200 bg-brand-50' : 'border-gold-200 bg-gold-50')}
      as="section"
    >
      {clean ? (
        <Check className="mt-0.5 size-5 shrink-0 text-brand-700" />
      ) : (
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-gold-700" />
      )}
      <div className="min-w-0 flex-1">
        <p className={cx('font-bold', clean ? 'text-brand-700' : 'text-gold-700')}>
          {withCount(result.updated.length, 'admin.bulkDone')}
          {!clean && ` · ${withCount(result.skipped.length, 'admin.bulkSkipped')}`}
        </p>
        {result.skipped.length > 0 && (
          <ul className="mt-1.5 space-y-0.5 text-sm text-ink-700">
            {result.skipped.map((s) => (
              <li key={s.id}>
                <span className="font-semibold">{s.name}</span> — {lang === 'bn' ? s.reasonBn : s.reason}
              </li>
            ))}
          </ul>
        )}
      </div>
      <button
        onClick={onClose}
        aria-label="Close"
        className="grid size-8 shrink-0 place-items-center rounded-full text-ink-500 hover:bg-white/60"
      >
        <X className="size-5" />
      </button>
    </Card>
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
