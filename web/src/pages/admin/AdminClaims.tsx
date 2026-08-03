import { useCallback, useEffect, useMemo, useState } from 'react'
import { Check, Clock, FileText, Search, X } from 'lucide-react'
import { adminApi, ApiError, PAGE_SIZE, type Claim, type ClaimStatus } from '../../lib/api'
import { useApp, type TKey } from '../../lib/store'
import { useAdmin } from '../../lib/adminStore'
import {
  Avatar,
  Badge,
  Button,
  Card,
  Field,
  Input,
  Select,
  Sheet,
  Spinner,
} from '../../components/ui'

const STATUSES: ClaimStatus[] = ['CLAIMED', 'VERIFIED', 'REJECTED']

const STATUS_LABEL: Record<ClaimStatus, TKey> = {
  CLAIMED: 'claims.waiting',
  VERIFIED: 'claims.verified',
  REJECTED: 'claims.rejected',
}

function statusTone(s: ClaimStatus) {
  return s === 'VERIFIED' ? 'green' : s === 'REJECTED' ? 'red' : 'gold'
}

/**
 * The identity queue: people who have proved a mobile number and are waiting to
 * be told they are of the batch they say.
 *
 * This is a separate screen from the application queue and not a tab on it,
 * because it answers a different question about a different row. An application
 * is about one event and carries an amount, a guest list and a payment. A claim
 * is about who somebody is, may have no registration behind it at all, and stays
 * true after the reunion is over.
 */
export default function AdminClaims() {
  const { t, lang, n, yr } = useApp()
  const { isSuper } = useAdmin()

  const [claims, setClaims] = useState<Claim[] | null>(null)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [total, setTotal] = useState(0)
  const [more, setMore] = useState(false)
  const [batches, setBatches] = useState<number[]>([])
  const [query, setQuery] = useState('')
  const [batchYear, setBatchYear] = useState<'ALL' | number>('ALL')
  const [status, setStatus] = useState<ClaimStatus>('CLAIMED')

  const [open, setOpen] = useState<Claim | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [override, setOverride] = useState(false)

  useEffect(() => {
    adminApi.myBatches().then(setBatches)
  }, [])

  const fetchPage = useCallback(
    (cursor: string | null, limit?: number) => adminApi.claims({ status, batchYear, query, cursor, limit }),
    [status, batchYear, query],
  )

  const load = useCallback(
    async (keep?: number) => {
      const page = await fetchPage(null, keep)
      setClaims(page.items)
      setNextCursor(page.nextCursor)
      setTotal(page.total)
    },
    [fetchPage],
  )

  useEffect(() => {
    setClaims(null)
    load()
  }, [load])

  async function loadMore() {
    if (!nextCursor || more) return
    setMore(true)
    try {
      const page = await fetchPage(nextCursor)
      setClaims((prev) => [...(prev ?? []), ...page.items])
      setNextCursor(page.nextCursor)
      setTotal(page.total)
    } finally {
      setMore(false)
    }
  }

  /**
   * A settled claim is closed to everyone; only a super admin can reopen it, and
   * only to the opposite verdict. Same rule as the application queue, for the
   * same reason — deciding the same way twice is never anything but a mistake.
   */
  const verdicts = useMemo(() => {
    if (!open) return null
    if (open.status === 'CLAIMED') return { verify: true, reject: true, settled: false }
    return {
      verify: isSuper && open.status !== 'VERIFIED',
      reject: isSuper && open.status !== 'REJECTED',
      settled: true,
    }
  }, [open, isSuper])

  function openRow(c: Claim) {
    setOpen(c)
    setNote('')
    setError('')
    setOverride(false)
  }

  async function decide(verdict: 'VERIFIED' | 'REJECTED') {
    if (!open) return
    setError('')
    setBusy(true)
    try {
      await adminApi.reviewClaim(open.personId, verdict, note)
      setOpen(null)
      setNote('')
      setOverride(false)
      // Reload rather than patch: the row leaves the CLAIMED filter it came from.
      load(Math.max(PAGE_SIZE, claims?.length ?? 0))
    } catch (e) {
      setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  const hasMore = !!nextCursor

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-extrabold text-ink-900">{t('claims.title')}</h1>
        <p className="mt-1 text-ink-500">{t('claims.intro')}</p>
      </div>

      {/* ---------- Filters ---------- */}
      <Card className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div className="relative sm:col-span-2 lg:col-span-1">
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
          <Select value={status} onChange={(e) => setStatus(e.target.value as ClaimStatus)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(STATUS_LABEL[s])}
              </option>
            ))}
          </Select>
        </Field>
      </Card>

      {/* ---------- Queue ---------- */}
      {!claims ? (
        <Spinner label={t('common.loading')} />
      ) : claims.length === 0 ? (
        <Card className="border-dashed py-10 text-center">
          <Clock className="mx-auto mb-2 size-10 text-ink-400/50" />
          <p className="font-semibold text-ink-500">{t('admin.noResults')}</p>
        </Card>
      ) : (
        <div className="space-y-2">
          <p className="px-1 font-semibold text-ink-500">
            {n(claims.length)}
            {hasMore && ` / ${n(total)}`}
          </p>

          {claims.map((c) => (
            <Card key={c.personId} className="flex items-center gap-3 p-3">
              <Avatar name={c.name} />
              <div className="min-w-0 flex-1">
                <div className="truncate font-bold text-ink-900">{c.nameBn || c.name}</div>
                <div className="truncate text-sm text-ink-400">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {c.batchYear ? yr(c.batchYear) : '—'}
                  {c.phone && (
                    <>
                      {' · '}
                      <span className="tabular-nums">{n(c.phone)}</span>
                    </>
                  )}
                </div>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  <Badge tone={statusTone(c.status)}>{t(STATUS_LABEL[c.status])}</Badge>
                  {c.hasRegistration && (
                    <Badge tone="muted">
                      <FileText className="size-3.5" />
                      {t('claims.hasRegistration')}
                    </Badge>
                  )}
                </div>
              </div>
              <Button size="sm" variant="outline" onClick={() => openRow(c)}>
                {t('admin.details')}
              </Button>
            </Card>
          ))}

          {hasMore && (
            <div className="pt-2 text-center">
              <Button variant="outline" size="lg" loading={more} onClick={loadMore}>
                {t('cta.loadMore')} ({n(total - claims.length)})
              </Button>
            </div>
          )}
        </div>
      )}

      {/* ---------- Detail + decision ---------- */}
      <Sheet open={!!open} onClose={() => setOpen(null)} title={t('admin.details')}>
        {open && (
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <Avatar name={open.name} size="lg" />
              <div className="min-w-0">
                <div className="truncate text-lg font-extrabold text-ink-900">{open.nameBn || open.name}</div>
                <div className="text-ink-500">
                  {lang === 'bn' ? 'এসএসসি' : 'SSC'} {open.batchYear ? yr(open.batchYear) : '—'}
                </div>
              </div>
            </div>

            <Card className="space-y-1.5 p-4">
              <Row label={t('auth.phone')} value={open.phone ? n(open.phone) : t('common.notSet')} />
              <Row label={t('profile.email')} value={open.email ?? t('common.notSet')} />
              <Row
                label={t('profile.gender')}
                value={open.gender ? t(`gender.${open.gender}` as never) : t('common.notSet')}
              />
              <Row label={t('profile.occupation')} value={open.occupation ?? t('common.notSet')} />
              <Row label={t('profile.city')} value={open.city ?? t('common.notSet')} />
              <Row label={t('claims.claimedOn')} value={n(open.claimedAt.slice(0, 10))} />
            </Card>

            {/* What verifying does and does not do — the two states are separate,
                and a coordinator should not be surprised by which one they moved. */}
            <Card className="border-brand-200 bg-brand-50 p-4">
              <p className="text-sm font-semibold text-ink-700">
                {open.hasRegistration ? t('claims.alsoRegistered') : t('claims.noRegistration')}
              </p>
            </Card>

            {open.lastReview && (
              <Card className="p-4 text-sm text-ink-500">
                <span className="font-semibold text-ink-700">
                  {t('admin.reviewedBy')}: {open.lastReview.adminName}
                </span>
                {open.lastReview.note && <span className="block">“{open.lastReview.note}”</span>}
              </Card>
            )}

            {verdicts?.settled && !override ? (
              <Card className="space-y-3 border-ink-400/30 bg-paper-2/60 p-4">
                <p className="font-semibold text-ink-700">{t('claims.settled')}</p>
                {isSuper ? (
                  <Button variant="outline" size="lg" className="w-full" onClick={() => setOverride(true)}>
                    {t('admin.override')}
                  </Button>
                ) : (
                  <p className="text-sm text-ink-500">{t('admin.askSuper')}</p>
                )}
              </Card>
            ) : (
              <>
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
                  <Button
                    variant="danger"
                    size="lg"
                    loading={busy}
                    disabled={!verdicts?.reject}
                    icon={<X className="size-5" />}
                    onClick={() => decide('REJECTED')}
                  >
                    {t('cta.reject')}
                  </Button>
                  <Button
                    size="lg"
                    loading={busy}
                    disabled={!verdicts?.verify}
                    icon={<Check className="size-5" />}
                    onClick={() => decide('VERIFIED')}
                  >
                    {t('claims.verify')}
                  </Button>
                </div>
              </>
            )}
          </div>
        )}
      </Sheet>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="shrink-0 text-sm text-ink-400">{label}</span>
      <span className="min-w-0 truncate text-right font-semibold text-ink-700">{value}</span>
    </div>
  )
}
