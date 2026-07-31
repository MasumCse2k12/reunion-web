import { useRef, useState } from 'react'
import { Camera, CheckCircle2, Save, Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import { BLOOD_GROUPS, GENDERS, type BloodGroup, type Gender } from '../mock/data'
import { useApp } from '../lib/store'
import { Avatar, Badge, Button, Card, Field, Input, SectionTitle, Select } from '../components/ui'

/**
 * Resize and compress an image file to a JPEG using the Canvas API.
 * Max dimension 800 px, quality 0.82 — brings a typical phone photo from
 * 3-5 MB down to 80-200 KB before it ever leaves the device.
 */
function compressImage(file: File): Promise<File> {
  const MAX_DIM = 800
  const QUALITY = 0.82
  return new Promise((resolve, reject) => {
    const img = new Image()
    const objectUrl = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(objectUrl)
      let { width, height } = img
      if (width > MAX_DIM || height > MAX_DIM) {
        if (width >= height) {
          height = Math.round((height * MAX_DIM) / width)
          width = MAX_DIM
        } else {
          width = Math.round((width * MAX_DIM) / height)
          height = MAX_DIM
        }
      }
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      canvas.getContext('2d')!.drawImage(img, 0, 0, width, height)
      canvas.toBlob(
        (blob) => {
          if (!blob) { reject(new Error('Image compression failed')); return }
          resolve(new File([blob], 'profile.jpg', { type: 'image/jpeg' }))
        },
        'image/jpeg',
        QUALITY,
      )
    }
    img.onerror = () => { URL.revokeObjectURL(objectUrl); reject(new Error('Failed to load image')) }
    img.src = objectUrl
  })
}

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
  const [photoUploading, setPhotoUploading] = useState(false)
  const [photoError, setPhotoError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  async function handlePhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setPhotoError(null)
    setPhotoUploading(true)
    try {
      const compressed = await compressImage(file)
      const updated = await api.uploadPhoto(compressed)
      setUser(updated)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Upload failed'
      setPhotoError(msg)
    } finally {
      setPhotoUploading(false)
      // Reset so the same file can be selected again after an error.
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  async function handlePhotoDelete() {
    setPhotoError(null)
    setPhotoUploading(true)
    try {
      await api.deletePhoto()
      setUser({ ...user!, photoUrl: undefined })
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Delete failed'
      setPhotoError(msg)
    } finally {
      setPhotoUploading(false)
    }
  }

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
          <Avatar name={form.name || 'A'} photoUrl={user?.photoUrl} size="xl" />
          {/* Hidden file input — accept images only, 5 MB max enforced server-side */}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            onChange={handlePhotoChange}
          />
          <button
            type="button"
            disabled={photoUploading}
            onClick={() => fileInputRef.current?.click()}
            className="absolute -bottom-1 -right-1 grid size-10 place-items-center rounded-full bg-brand-600 text-white shadow-md disabled:opacity-60"
            title={lang === 'bn' ? 'ছবি পরিবর্তন করুন' : 'Change photo'}
          >
            <Camera className="size-5" />
          </button>
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-xl font-extrabold text-ink-900">{form.name}</div>
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge tone="green">
              {lang === 'bn' ? 'এসএসসি' : 'SSC'} {yr(form.batchYear)}
            </Badge>
            {form.bloodGroup && <Badge tone="red">{form.bloodGroup}</Badge>}
          </div>
          {user?.phone && <div className="mt-1 text-sm tabular-nums text-ink-400">{n(user.phone)}</div>}
          {photoUploading && (
            <p className="mt-1 text-sm text-brand-600">{lang === 'bn' ? 'আপলোড হচ্ছে…' : 'Uploading…'}</p>
          )}
          {photoError && <p className="mt-1 text-sm text-red-600">{photoError}</p>}
          {!photoUploading && !photoError && (
            <p className="mt-1 text-xs text-ink-400">
              {lang === 'bn' ? 'JPG, PNG বা WebP · সর্বোচ্চ ৫ MB' : 'JPG, PNG or WebP · max 5 MB'}
            </p>
          )}
          {user?.photoUrl && !photoUploading && (
            <button
              type="button"
              onClick={handlePhotoDelete}
              className="mt-1.5 flex items-center gap-1 text-xs text-ink-400 hover:text-red-600"
            >
              <Trash2 className="size-3" />
              {lang === 'bn' ? 'ছবি মুছুন' : 'Remove photo'}
            </button>
          )}
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
