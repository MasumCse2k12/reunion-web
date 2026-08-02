import { useEffect, useState } from 'react'
import { KeyRound, Pencil, Plus, ShieldCheck, Trash2, UserCog } from 'lucide-react'
import { adminApi, ApiError, type AdminAccount, type AdminRole } from '../../lib/api'
import { SCHOOL } from '../../mock/data'
import { useAdmin } from '../../lib/adminStore'
import { useApp } from '../../lib/store'
import { Avatar, Badge, Button, Card, Field, Input, SectionTitle, Select, Sheet, Spinner, cx } from '../../components/ui'

const EMPTY = {
  name: '',
  nameBn: '',
  username: '',
  password: '',
  phone: '',
  role: 'GROUP_ADMIN' as AdminRole,
  from: String(SCHOOL.firstBatch),
  to: String(SCHOOL.lastBatch),
}

function yearRange(from: number, to: number): number[] {
  const out: number[] = []
  for (let y = Math.min(from, to); y <= Math.max(from, to); y++) out.push(y)
  return out
}

export default function AdminAccounts() {
  const { t, lang, n, yr } = useApp()
  const { isSuper } = useAdmin()

  const [admins, setAdmins] = useState<AdminAccount[] | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<AdminAccount | null>(null)
  const [pwFor, setPwFor] = useState<AdminAccount | null>(null)
  const [form, setForm] = useState({ ...EMPTY })
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (isSuper) adminApi.admins().then(setAdmins)
  }, [isSuper])

  if (!isSuper) {
    return (
      <Card className="border-red-200 bg-red-50 py-10 text-center">
        <ShieldCheck className="mx-auto mb-2 size-10 text-red-500" />
        <p className="font-semibold text-red-700">{t('admin.superOnly')}</p>
      </Card>
    )
  }

  if (!admins) return <Spinner label={t('common.loading')} />

  function fail(e: unknown) {
    setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
  }

  async function refresh() {
    setAdmins(await adminApi.admins())
  }

  async function create() {
    setError('')
    setBusy(true)
    try {
      await adminApi.createAdmin({
        name: form.name,
        nameBn: form.nameBn,
        username: form.username,
        password: form.password,
        phone: form.phone,
        role: form.role,
        batches: form.role === 'SUPER_ADMIN' ? [] : yearRange(Number(form.from), Number(form.to)),
      })
      await refresh()
      setCreateOpen(false)
      setForm({ ...EMPTY })
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  async function saveEdit() {
    if (!editing) return
    setError('')
    setBusy(true)
    try {
      await adminApi.updateAdmin(editing.id, {
        name: form.name,
        nameBn: form.nameBn,
        phone: form.phone,
        batches: editing.role === 'GROUP_ADMIN' ? yearRange(Number(form.from), Number(form.to)) : undefined,
      })
      await refresh()
      setEditing(null)
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  async function savePassword() {
    if (!pwFor) return
    setError('')
    setBusy(true)
    try {
      await adminApi.setPassword(pwFor.id, newPassword)
      setPwFor(null)
      setNewPassword('')
    } catch (e) {
      fail(e)
    } finally {
      setBusy(false)
    }
  }

  async function toggleActive(a: AdminAccount) {
    try {
      await adminApi.updateAdmin(a.id, { active: !a.active })
      await refresh()
    } catch (e) {
      fail(e)
    }
  }

  async function remove(a: AdminAccount) {
    if (!confirm(lang === 'bn' ? `${a.name} — অ্যাকাউন্টটি মুছে ফেলবেন?` : `Remove the account for ${a.name}?`)) return
    try {
      await adminApi.deleteAdmin(a.id)
      await refresh()
    } catch (e) {
      fail(e)
    }
  }

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }))

  return (
    <div className="space-y-5">
      <SectionTitle
        action={
          <Button
            size="sm"
            icon={<Plus className="size-5" />}
            onClick={() => {
              setForm({ ...EMPTY })
              setError('')
              setCreateOpen(true)
            }}
          >
            {t('admin.newAdmin')}
          </Button>
        }
      >
        <span className="inline-flex items-center gap-2">
          <UserCog className="size-6 text-brand-600" />
          {t('admin.accounts')}
        </span>
      </SectionTitle>

      {error && !createOpen && !editing && !pwFor && <p className="font-semibold text-red-600">{error}</p>}

      <div className="space-y-2">
        {admins.map((a) => (
          <Card key={a.id} className={cx('flex flex-wrap items-center gap-3 p-3', !a.active && 'opacity-60')}>
            <Avatar name={a.name} />
            <div className="min-w-0 flex-1">
              <div className="truncate font-bold text-ink-900">{a.nameBn || a.name}</div>
              <div className="truncate font-mono text-sm text-ink-400">
                {a.username} · <span className="tabular-nums">{n(a.phone)}</span>
              </div>
              <div className="mt-1 flex flex-wrap gap-1.5">
                <Badge tone={a.role === 'SUPER_ADMIN' ? 'gold' : 'green'}>{t(`admin.role${a.role}` as never)}</Badge>
                <Badge tone="neutral">
                  {a.role === 'SUPER_ADMIN'
                    ? t('admin.scopeAll')
                    : `${yr(a.batches[0])}–${yr(a.batches[a.batches.length - 1])}`}
                </Badge>
                {!a.active && <Badge tone="red">{t('admin.disabled')}</Badge>}
              </div>
            </div>

            <div className="flex shrink-0 flex-wrap items-center gap-1.5">
              <Button
                size="sm"
                variant="outline"
                icon={<Pencil className="size-4" />}
                onClick={() => {
                  setForm({
                    ...EMPTY,
                    name: a.name,
                    nameBn: a.nameBn,
                    username: a.username,
                    phone: a.phone,
                    role: a.role,
                    from: String(a.batches[0] ?? SCHOOL.firstBatch),
                    to: String(a.batches[a.batches.length - 1] ?? SCHOOL.lastBatch),
                  })
                  setError('')
                  setEditing(a)
                }}
              >
                {t('cta.edit')}
              </Button>
              <Button
                size="sm"
                variant="outline"
                icon={<KeyRound className="size-4" />}
                onClick={() => {
                  setNewPassword('')
                  setError('')
                  setPwFor(a)
                }}
              >
                {t('admin.setPassword')}
              </Button>
              {a.role !== 'SUPER_ADMIN' && (
                <>
                  <Button size="sm" variant="ghost" onClick={() => toggleActive(a)}>
                    {a.active ? t('admin.disabled') : t('admin.active')}
                  </Button>
                  <Button size="sm" variant="danger" icon={<Trash2 className="size-4" />} onClick={() => remove(a)}>
                    {t('cta.remove')}
                  </Button>
                </>
              )}
            </div>
          </Card>
        ))}
      </div>

      {/* ---------- Create ---------- */}
      <Sheet open={createOpen} onClose={() => setCreateOpen(false)} title={t('admin.newAdmin')}>
        <div className="space-y-4">
          <Field label={t('profile.name')} required>
            <Input value={form.name} onChange={set('name')} />
          </Field>
          <Field label={`${t('profile.name')} (বাংলা)`} hint={t('profile.optional')}>
            <Input value={form.nameBn} onChange={set('nameBn')} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t('admin.username')} required>
              <Input value={form.username} autoCapitalize="none" spellCheck={false} onChange={set('username')} />
            </Field>
            <Field label={t('admin.contact')} required>
              <Input value={form.phone} inputMode="numeric" onChange={set('phone')} className="tabular-nums" />
            </Field>
          </div>
          <Field label={t('admin.password')} hint={lang === 'bn' ? 'কমপক্ষে ৮ অক্ষর' : 'At least 8 characters'} required>
            <Input type="text" value={form.password} onChange={set('password')} className="font-mono" />
          </Field>

          <Field label={t('admin.filterStatus')} required>
            <Select value={form.role} onChange={set('role')}>
              <option value="GROUP_ADMIN">{t('admin.roleGROUP_ADMIN')}</option>
              <option value="SUPER_ADMIN">{t('admin.roleSUPER_ADMIN')}</option>
            </Select>
          </Field>

          {form.role === 'GROUP_ADMIN' && (
            <Field label={t('admin.assignBatches')} required>
              <div className="grid grid-cols-2 gap-2">
                <Input
                  type="number"
                  value={form.from}
                  onChange={set('from')}
                  min={SCHOOL.firstBatch}
                  max={SCHOOL.lastBatch}
                  className="tabular-nums"
                  aria-label={t('admin.batchFrom')}
                />
                <Input
                  type="number"
                  value={form.to}
                  onChange={set('to')}
                  min={SCHOOL.firstBatch}
                  max={SCHOOL.lastBatch}
                  className="tabular-nums"
                  aria-label={t('admin.batchTo')}
                />
              </div>
            </Field>
          )}

          {error && <p className="font-semibold text-red-600">{error}</p>}

          <Button full size="lg" loading={busy} icon={<Plus className="size-5" />} onClick={create}>
            {t('cta.add')}
          </Button>
        </div>
      </Sheet>

      {/* ---------- Edit ---------- */}
      <Sheet open={!!editing} onClose={() => setEditing(null)} title={t('admin.editAdmin')}>
        <div className="space-y-4">
          <Field label={t('profile.name')} required>
            <Input value={form.name} onChange={set('name')} />
          </Field>
          <Field label={`${t('profile.name')} (বাংলা)`}>
            <Input value={form.nameBn} onChange={set('nameBn')} />
          </Field>
          <Field label={t('admin.contact')}>
            <Input value={form.phone} inputMode="numeric" onChange={set('phone')} className="tabular-nums" />
          </Field>

          {editing?.role === 'GROUP_ADMIN' && (
            <Field label={t('admin.assignBatches')}>
              <div className="grid grid-cols-2 gap-2">
                <Input type="number" value={form.from} onChange={set('from')} className="tabular-nums" />
                <Input type="number" value={form.to} onChange={set('to')} className="tabular-nums" />
              </div>
            </Field>
          )}

          {error && <p className="font-semibold text-red-600">{error}</p>}

          <Button full size="lg" loading={busy} onClick={saveEdit}>
            {t('cta.save')}
          </Button>
        </div>
      </Sheet>

      {/* ---------- Set password ---------- */}
      <Sheet open={!!pwFor} onClose={() => setPwFor(null)} title={t('admin.setPassword')}>
        <div className="space-y-4">
          <p className="text-ink-500">
            {lang === 'bn' ? pwFor?.nameBn : pwFor?.name} · <span className="font-mono">{pwFor?.username}</span>
          </p>
          <Field
            label={t('admin.password')}
            error={error}
            hint={
              lang === 'bn'
                ? 'পাসওয়ার্ডটি অ্যাডমিনকে নিজে জানিয়ে দিন — এটি আর দেখা যাবে না।'
                : 'Tell the admin this password yourself — it is not shown again.'
            }
            required
          >
            <Input type="text" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} className="font-mono" />
          </Field>
          <Button full size="lg" loading={busy} icon={<KeyRound className="size-5" />} onClick={savePassword}>
            {t('cta.save')}
          </Button>
        </div>
      </Sheet>
    </div>
  )
}
