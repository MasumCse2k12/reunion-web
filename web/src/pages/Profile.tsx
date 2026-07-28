import { useState } from 'react'
import { Camera, CheckCircle2, Save } from 'lucide-react'
import { api } from '../lib/api'
import { BLOOD_GROUPS, GENDERS, type BloodGroup, type Gender } from '../mock/data'
import { useApp } from '../lib/store'
import { Avatar, Badge, Button, Card, Field, Input, SectionTitle, Select } from '../components/ui'

export default function Profile() {
  const { t, lang, n, yr, user, setUser } = useApp()
  const [form, setForm] = useState({
    name: user?.name ?? '',
    batchYear: String(user?.batchYear ?? ''),
    phone: user?.phone ?? '',
    occupation: user?.occupation ?? '',
    city: user?.city ?? '',
    email: user?.email ?? '',
    gender: (user?.gender ?? '') as Gender | '',
    dob: user?.dob ?? '',
    bloodGroup: (user?.bloodGroup ?? '') as BloodGroup | '',
  })
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)

  async function save() {
    setBusy(true)
    const updated = await api.updateMe({
      name: form.name,
      batchYear: Number(form.batchYear),
      phone: form.phone,
      occupation: form.occupation,
      city: form.city,
      // Empty means "not given" — store undefined rather than an empty string,
      // so an unanswered field never looks like an answered one.
      email: form.email.trim() || undefined,
      gender: form.gender || undefined,
      dob: form.dob || undefined,
      bloodGroup: form.bloodGroup || undefined,
    })
    setUser(updated)
    setBusy(false)
    setSaved(true)
    setTimeout(() => setSaved(false), 2500)
  }

  const set =
    (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      setForm((f) => ({ ...f, [k]: e.target.value }))

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-extrabold text-ink-900">{t('profile.title')}</h1>

      <Card className="flex items-center gap-4">
        <div className="relative">
          <Avatar name={form.name || 'A'} size="xl" />
          <button className="absolute -bottom-1 -right-1 grid size-10 place-items-center rounded-full bg-brand-600 text-white shadow-md">
            <Camera className="size-5" />
          </button>
        </div>
        <div className="min-w-0">
          <div className="truncate text-xl font-extrabold text-ink-900">{form.name}</div>
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge tone="green">
              {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(form.batchYear)}
            </Badge>
            {form.bloodGroup && <Badge tone="red">{form.bloodGroup}</Badge>}
          </div>
          {user?.phone && <div className="mt-1 text-sm tabular-nums text-ink-400">{n(user.phone)}</div>}
        </div>
      </Card>

      <Card className="space-y-4">
        <SectionTitle>{lang === 'bn' ? 'তথ্য হালনাগাদ করুন' : 'Update your details'}</SectionTitle>

        <Field label={t('profile.name')} required>
          <Input value={form.name} onChange={set('name')} />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t('profile.batch')} required>
            <Input value={form.batchYear} onChange={set('batchYear')} inputMode="numeric" className="tabular-nums" />
          </Field>
          <Field label={t('auth.phone')}>
            <Input value={form.phone} onChange={set('phone')} inputMode="numeric" className="tabular-nums" />
          </Field>
        </div>

        <Field
          label={t('profile.occupation')}
          hint={lang === 'bn' ? 'ঐচ্ছিক — পরে দিলেও চলবে' : 'Optional — you can add this later'}
        >
          <Input value={form.occupation} onChange={set('occupation')} />
        </Field>

        <Field label={t('profile.city')} hint={t('profile.optional')}>
          <Input value={form.city} onChange={set('city')} />
        </Field>
      </Card>

      {/* Everything below is optional on purpose — an elder who stops here is still registered. */}
      <Card className="space-y-4">
        <SectionTitle>{t('profile.moreDetails')}</SectionTitle>
        <p className="-mt-2 text-sm text-ink-400">{t('profile.moreDetailsSub')}</p>

        <Field label={t('profile.email')} hint={t('profile.optional')}>
          <Input
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="name@example.com"
            value={form.email}
            onChange={set('email')}
          />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t('profile.gender')} hint={t('profile.optional')}>
            <Select value={form.gender} onChange={set('gender')}>
              <option value="">{t('common.notSet')}</option>
              {GENDERS.map((g) => (
                <option key={g} value={g}>
                  {t(`gender.${g}` as never)}
                </option>
              ))}
            </Select>
          </Field>

          <Field label={t('profile.blood')} hint={t('profile.optional')}>
            <Select value={form.bloodGroup} onChange={set('bloodGroup')}>
              <option value="">{t('common.notSet')}</option>
              {BLOOD_GROUPS.map((b) => (
                <option key={b} value={b}>
                  {b}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <Field label={t('profile.dob')} hint={t('profile.optional')}>
          <Input type="date" value={form.dob} onChange={set('dob')} className="tabular-nums" />
        </Field>

        <Button full size="lg" loading={busy} onClick={save} icon={<Save className="size-5" />}>
          {t('cta.save')}
        </Button>

        {saved && (
          <p className="animate-pop text-center font-semibold text-brand-600">
            <CheckCircle2 className="mr-1.5 inline size-5" />
            {t('profile.saved')}
          </p>
        )}
      </Card>

      <Card>
        <SectionTitle>{lang === 'bn' ? 'গোপনীয়তা' : 'Privacy'}</SectionTitle>
        <p className="text-ink-500">
          {lang === 'bn'
            ? 'আপনার মোবাইল নম্বর, ইমেইল ও জন্ম তারিখ শুধু আপনার ব্যাচের সদস্য এবং কমিটির অ্যাডমিনরা দেখতে পাবেন। কখনোই প্রকাশ্যে দেখানো হবে না।'
            : 'Your mobile number, email and date of birth are visible only to your own batchmates and the committee admins. They are never shown publicly.'}
        </p>
      </Card>
    </div>
  )
}
