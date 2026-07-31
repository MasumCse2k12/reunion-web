import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ArrowLeft, KeyRound, ShieldCheck } from 'lucide-react'
import { adminApi, ApiError } from '../../lib/api'
import { useAdmin } from '../../lib/adminStore'
import { useApp } from '../../lib/store'
import { LangToggle } from '../../components/Layout'
import { Button, Card, Field, Input, Spinner, cx } from '../../components/ui'

export default function AdminLogin() {
  const { t, lang } = useApp()
  const { admin, setAdmin, ready } = useAdmin()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (!ready) return <Spinner label={t('common.loading')} />
  if (admin) return <Navigate to="/admin" replace />

  async function signIn() {
    setError('')
    setBusy(true)
    try {
      setAdmin(await adminApi.login(username, password))
      navigate('/admin')
    } catch (e) {
      setError(e instanceof ApiError ? (lang === 'bn' ? e.messageBn : e.message) : 'Error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={cx('flex min-h-dvh flex-col bg-ink-900', lang === 'bn' && 'font-bn')}>
      <header className="border-b border-white/10">
        <div className="mx-auto flex max-w-lg items-center gap-3 px-4 py-3">
          <Link to="/" className="grid size-10 shrink-0 place-items-center rounded-xl text-white/70 hover:bg-white/10">
            <ArrowLeft className="size-6" />
          </Link>
          <span className="min-w-0 flex-1 truncate font-bold text-white">{t('admin.portal')}</span>
          <LangToggle dark />
        </div>
      </header>

      <main className="mx-auto w-full max-w-lg flex-1 px-4 py-10">
        <div className="mb-6 text-center">
          <span className="relative mb-4 inline-block">
            <img
              src="/school-logo.png"
              alt=""
              className="size-24 rounded-full bg-white object-contain p-1.5 shadow-xl shadow-ink-900/40 ring-2 ring-white/20"
            />
            <span className="absolute -bottom-1 -right-1 grid size-9 place-items-center rounded-full bg-gold-400 text-ink-900 ring-4 ring-ink-900">
              <ShieldCheck className="size-5" />
            </span>
          </span>
          <h1 className="text-3xl font-extrabold text-white">{t('admin.login')}</h1>
          <p className="mt-1.5 text-white/60">{t('admin.loginSub')}</p>
        </div>

        <Card className="space-y-4">
          <Field label={t('admin.username')} required>
            <Input
              value={username}
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              onChange={(e) => setUsername(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && signIn()}
            />
          </Field>

          <Field label={t('admin.password')} error={error} required>
            <Input
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && signIn()}
            />
          </Field>

          <Button full size="lg" loading={busy} icon={<KeyRound className="size-5" />} onClick={signIn}>
            {t('admin.signIn')}
          </Button>
        </Card>

      </main>
    </div>
  )
}
