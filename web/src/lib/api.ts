/**
 * ============================================================================
 *  HTTP API CLIENT
 * ============================================================================
 *  Every screen in this app talks to the Spring Boot backend through this file.
 *  Set VITE_API_BASE_URL in .env.local; defaults to the local dev server.
 * ============================================================================
 */

import {
  TICKET_TYPES,
  EVENT,
  type AdminRole,
  type Application,
  type Batch,
  type BloodGroup,
  type Gender,
  type Guest,
  type GuestRelation,
  type PaymentMethod,
  type PaymentStatus,
  type Person,
  type Registration,
  type ReviewStatus,
} from '../mock/data'

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8090/smbc'

export const CONTACT_PHONE = (import.meta.env.VITE_CONTACT_PHONE as string | undefined) ?? '01943177909'

const MEMBER_ACCESS = 'sammalani.member.access'
const MEMBER_REFRESH = 'sammalani.member.refresh'
const ADMIN_ACCESS = 'sammalani.admin.access'

/* ------------------------------------------------------------------ */
/* Error                                                                */
/* ------------------------------------------------------------------ */

export class ApiError extends Error {
  constructor(
    message: string,
    public messageBn: string,
    public status?: number,
    public code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/* ------------------------------------------------------------------ */
/* HTTP primitives                                                      */
/* ------------------------------------------------------------------ */

async function http<T>(method: string, path: string, body?: unknown, token?: string | null): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 204) return undefined as T

  const ct = res.headers.get('content-type') ?? ''
  const data: unknown = ct.includes('json') ? await res.json() : undefined

  if (!res.ok) {
    const d = (data ?? {}) as Record<string, unknown>
    const msg = String(d.detail ?? d.message ?? 'Something went wrong')
    const msgBn = String(d.messageBn ?? 'কিছু একটা সমস্যা হয়েছে')
    const code = d.code ? String(d.code) : undefined
    throw new ApiError(msg, msgBn, res.status, code)
  }

  return data as T
}

async function memberHttpMultipart<T>(method: string, path: string, formData: FormData): Promise<T> {
  const token = localStorage.getItem(MEMBER_ACCESS)
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = `Bearer ${token}`
  // Do NOT set Content-Type — the browser must set it with the boundary.
  const res = await fetch(`${BASE}${path}`, { method, headers, body: formData })
  if (res.status === 204) return undefined as T
  const ct = res.headers.get('content-type') ?? ''
  const data: unknown = ct.includes('json') ? await res.json() : undefined
  if (!res.ok) {
    const d = (data ?? {}) as Record<string, unknown>
    const msg = String(d.detail ?? d.message ?? 'Something went wrong')
    const msgBn = String(d.messageBn ?? 'কিছু একটা সমস্যা হয়েছে')
    const code = d.code ? String(d.code) : undefined
    throw new ApiError(msg, msgBn, res.status, code)
  }
  return data as T
}

async function memberHttp<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = localStorage.getItem(MEMBER_ACCESS)
  try {
    return await http<T>(method, path, body, token)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      const refresh = localStorage.getItem(MEMBER_REFRESH)
      if (refresh) {
        try {
          const s = await http<SessionRaw>('POST', '/api/v1/auth/refresh', { refreshToken: refresh })
          localStorage.setItem(MEMBER_ACCESS, s.accessToken)
          localStorage.setItem(MEMBER_REFRESH, s.refreshToken)
          return await http<T>(method, path, body, s.accessToken)
        } catch {
          localStorage.removeItem(MEMBER_ACCESS)
          localStorage.removeItem(MEMBER_REFRESH)
        }
      }
    }
    throw e
  }
}

function adminHttp<T>(method: string, path: string, body?: unknown): Promise<T> {
  return http<T>(method, path, body, localStorage.getItem(ADMIN_ACCESS))
}

/* ------------------------------------------------------------------ */
/* Server response shapes (internal)                                   */
/* ------------------------------------------------------------------ */

type ChallengeRaw = { challengeId: string; expiresInSeconds: number; devCode?: string }

type PersonRaw = {
  id: string
  name: string
  nameBn?: string
  batchYear: number
  status: string
  phone?: string
  email?: string
  gender?: Gender
  dob?: string
  bloodGroup?: string
  occupation?: string
  city?: string
  photoUrl?: string
  deceased: boolean
}

type GuestRaw = {
  id: string
  name: string
  relation: GuestRelation
  age?: number
  ticketTypeCode: string
  tshirtSize?: string
  amount: number
}

type ReviewRaw = { adminName: string; at: string; note?: string }
type PaymentRaw = { method: PaymentMethod; reference: string; amount: number; reportedAt: string; status: string }

type RegistrationRaw = {
  id: string
  personId: string
  batchYear: number
  guests: GuestRaw[]
  tshirtSize: string
  foodPref: string
  memberNote?: string
  amountDue: number
  status: string
  paymentStatus: PaymentStatus
  submittedAt?: string
  memberReview?: ReviewRaw
  paymentReview?: ReviewRaw
  payment?: PaymentRaw
}

type SessionRaw = { accessToken: string; refreshToken: string; expiresInSeconds: number; person: PersonRaw }
type BatchRaw = { year: number; rosterCount: number; claimedCount: number }
type TotalsRaw = { roster: number; claimed: number; batches: number }
type NoticeRaw = { id: string; title: string; titleBn: string; body: string; bodyBn: string; pinned: boolean; publishedAt: string }
type CoordinatorRaw = { id: string; name: string; nameBn: string; phone: string }
type AdminAccountRaw = {
  id: string
  name: string
  nameBn: string
  username: string
  phone: string
  role: AdminRole
  batches: number[]
  active: boolean
  createdAt: string
}
type AdminStatsRaw = {
  pendingMembers: number
  pendingPayments: number
  approvedMembers: number
  rejectedMembers: number
  confirmedAmount: number
  outstandingAmount: number
  batchesCovered: number
}
type ApplicationRaw = {
  id: string
  personId: string
  name: string
  nameBn: string
  batchYear: number
  phone: string
  email?: string
  gender?: Gender
  dob?: string
  bloodGroup?: string
  occupation?: string
  city?: string
  guests: GuestRaw[]
  amountDue: number
  submittedAt: string
  memberNote?: string
  memberStatus: ReviewStatus
  memberReview?: ReviewRaw
  paymentStatus: PaymentStatus
  payment?: PaymentRaw
  paymentReview?: ReviewRaw
}
type CursorPageRaw<T> = { items: T[]; nextCursor: string | null; total: number }
type BulkRaw = { updated: ApplicationRaw[]; skipped: Array<{ id: string; name: string; reason: string; reasonBn: string }> }

/* ------------------------------------------------------------------ */
/* Mapping helpers                                                      */
/* ------------------------------------------------------------------ */

function ticketIdFromCode(code: string): string {
  switch (code) {
    case 'ALUMNI':     return 'tt-alumni'
    case 'SPOUSE':     return 'tt-spouse'
    case 'CHILD':      return 'tt-child'
    case 'CHILD_FREE': return 'tt-child-free'
    case 'GUEST':      return 'tt-guest'
    default:           return 'tt-guest'
  }
}

function ticketIdFromRelation(relation: GuestRelation, age?: number): string {
  if (relation === 'SPOUSE') return 'tt-spouse'
  if (relation === 'CHILD') {
    if (age !== undefined && age < 5) return 'tt-child-free'
    if (age !== undefined && age <= 12) return 'tt-child'
    return 'tt-guest'
  }
  return 'tt-guest'
}

function mapPerson(r: PersonRaw): Person {
  return {
    id: r.id,
    name: r.name,
    nameBn: r.nameBn ?? r.name,
    batchYear: r.batchYear,
    status: r.status === 'SEEDED' ? 'SEEDED' : 'CLAIMED',
    phone: r.phone,
    email: r.email,
    gender: r.gender,
    dob: r.dob,
    bloodGroup: r.bloodGroup as BloodGroup | undefined,
    occupation: r.occupation,
    city: r.city,
    photoUrl: r.photoUrl,
    deceased: r.deceased,
  }
}

function mapGuest(g: GuestRaw): Guest {
  return {
    id: g.id,
    name: g.name,
    relation: g.relation,
    age: g.age,
    ticketTypeId: g.ticketTypeCode ? ticketIdFromCode(g.ticketTypeCode) : ticketIdFromRelation(g.relation, g.age),
    tshirtSize: g.tshirtSize,
  }
}

function mapRegistration(r: RegistrationRaw): Registration {
  return {
    id: r.id,
    personId: r.personId,
    ticketTypeId: 'tt-alumni',
    guests: r.guests.map(mapGuest),
    tshirtSize: r.tshirtSize ?? 'L',
    foodPref: (r.foodPref ?? 'REGULAR') as Registration['foodPref'],
    status: r.status as Registration['status'],
    amountDue: Number(r.amountDue),
    createdAt: r.submittedAt ?? new Date().toISOString(),
    submittedAt: r.submittedAt,
    applicationId: r.id,
  }
}

function mapReview(rv: ReviewRaw) {
  return { adminId: '', adminName: rv.adminName, at: rv.at, note: rv.note }
}

/** Member-side Application built from a RegistrationRaw (no separate person fetch needed). */
function mapApplication(r: RegistrationRaw): Application {
  const memberStatus: ReviewStatus =
    r.status === 'APPROVED' ? 'APPROVED' : r.status === 'REJECTED' ? 'REJECTED' : 'PENDING'
  return {
    id: r.id,
    personId: r.personId,
    name: '',
    nameBn: '',
    batchYear: r.batchYear,
    phone: '',
    guests: r.guests.map(mapGuest),
    amountDue: Number(r.amountDue),
    submittedAt: r.submittedAt ?? new Date().toISOString(),
    memberNote: r.memberNote,
    memberStatus,
    memberReview: r.memberReview ? mapReview(r.memberReview) : undefined,
    paymentStatus: r.paymentStatus,
    payment: r.payment
      ? { method: r.payment.method, reference: r.payment.reference, amount: Number(r.payment.amount), reportedAt: r.payment.reportedAt }
      : undefined,
    paymentReview: r.paymentReview ? mapReview(r.paymentReview) : undefined,
  }
}

/** Admin-side Application: includes person name and contact details. */
function mapAdminApplication(r: ApplicationRaw): Application {
  return {
    id: r.id,
    personId: r.personId,
    name: r.name,
    nameBn: r.nameBn ?? r.name,
    batchYear: r.batchYear,
    phone: r.phone,
    email: r.email,
    gender: r.gender,
    dob: r.dob,
    bloodGroup: r.bloodGroup as BloodGroup | undefined,
    occupation: r.occupation,
    city: r.city,
    guests: r.guests.map(mapGuest),
    amountDue: Number(r.amountDue),
    submittedAt: r.submittedAt,
    memberNote: r.memberNote,
    memberStatus: r.memberStatus,
    memberReview: r.memberReview ? mapReview(r.memberReview) : undefined,
    paymentStatus: r.paymentStatus,
    payment: r.payment
      ? { method: r.payment.method, reference: r.payment.reference, amount: Number(r.payment.amount), reportedAt: r.payment.reportedAt }
      : undefined,
    paymentReview: r.paymentReview ? mapReview(r.paymentReview) : undefined,
  }
}

type GuestPutItem = { id?: string; name: string; relation: GuestRelation; age?: number; tshirtSize?: string }

async function fetchCurrentReg(): Promise<RegistrationRaw | null> {
  try {
    return await memberHttp<RegistrationRaw>('GET', '/api/v1/me/registration')
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null
    throw e
  }
}

async function putRegistration(
  guests: GuestPutItem[],
  tshirtSize: string,
  foodPref: string,
  memberNote?: string,
): Promise<RegistrationRaw> {
  return memberHttp<RegistrationRaw>('PUT', '/api/v1/me/registration', {
    guests: guests.map((g) => ({ id: g.id, name: g.name, relation: g.relation, age: g.age, tshirtSize: g.tshirtSize })),
    tshirtSize,
    foodPref,
    memberNote,
  })
}

function mapNotice(n: NoticeRaw) {
  return { id: String(n.id), titleEn: n.title, titleBn: n.titleBn, bodyEn: n.body, bodyBn: n.bodyBn, date: n.publishedAt, pinned: n.pinned }
}

function completeness(p: Person, reg: Registration | null): number {
  const checks = [!!p.name, !!p.batchYear, !!p.phone, !!p.occupation, !!p.city, !!reg]
  return Math.round((checks.filter(Boolean).length / checks.length) * 100)
}

/* ------------------------------------------------------------------ */
/* Exported types                                                       */
/* ------------------------------------------------------------------ */

export type AdminAccount = AdminAccountRaw

export type DashboardData = {
  me: Person
  batch: Batch
  event: typeof EVENT
  registration: Registration | null
  application: Application | null
  totals: { roster: number; claimed: number; batches: number; registeredForEvent: number; teachers: number }
  notices: Array<{ id: string; titleEn: string; titleBn: string; bodyEn: string; bodyBn: string; date: string; pinned: boolean }>
  missingFromBatch: Person[]
  profileCompleteness: number
}

export type Notice = ReturnType<typeof mapNotice>

export type AdminStats = AdminStatsRaw

export type ApplicationFilter = {
  memberStatus?: ReviewStatus | 'ALL'
  paymentStatus?: PaymentStatus | 'ALL'
  batchYear?: number | 'ALL'
  query?: string
  cursor?: string | null
  limit?: number
}

export type Page<T> = { items: T[]; nextCursor: string | null; total: number }

export type BulkReviewResult = {
  updated: Application[]
  skipped: Array<{ id: string; name: string; reason: string; reasonBn: string }>
}

export const PAGE_SIZE = 10

export type {
  AdminRole,
  Application,
  Batch,
  BloodGroup,
  Gender,
  Guest,
  GuestRelation,
  PaymentMethod,
  PaymentStatus,
  Person,
  Registration,
  ReviewStatus,
}
export { TICKET_TYPES, EVENT }

/* ------------------------------------------------------------------ */
/* Member API                                                           */
/* ------------------------------------------------------------------ */

export const api = {
  /* auth */

  async requestOtp(phone: string): Promise<{ challengeId: string; hint: string }> {
    const r = await http<ChallengeRaw>('POST', '/api/v1/auth/otp/request', { phone })
    return { challengeId: r.challengeId, hint: r.devCode ?? '' }
  },

  async claimProfile(personId: string, phone: string, _year: number): Promise<{ challengeId: string; hint: string }> {
    const r = await http<ChallengeRaw>('POST', '/api/v1/public/claims', { personId, phone })
    return { challengeId: r.challengeId, hint: r.devCode ?? '' }
  },

  async registerNew(name: string, nameBn: string, batchYear: number, phone: string): Promise<{ challengeId: string; hint: string }> {
    const r = await http<ChallengeRaw>('POST', '/api/v1/public/register', { name, nameBn, batchYear, phone })
    return { challengeId: r.challengeId, hint: r.devCode ?? '' }
  },

  async verifyOtp(challengeId: string, code: string): Promise<{ token: string; person: Person }> {
    const s = await http<SessionRaw>('POST', '/api/v1/auth/otp/verify', { challengeId, code })
    localStorage.setItem(MEMBER_ACCESS, s.accessToken)
    localStorage.setItem(MEMBER_REFRESH, s.refreshToken)
    return { token: s.accessToken, person: mapPerson(s.person) }
  },

  async logout(): Promise<void> {
    await http('POST', '/api/v1/auth/logout', undefined, localStorage.getItem(MEMBER_ACCESS)).catch(() => {})
    localStorage.removeItem(MEMBER_ACCESS)
    localStorage.removeItem(MEMBER_REFRESH)
  },

  getSession(): { personId: string; token: string } | null {
    const token = localStorage.getItem(MEMBER_ACCESS)
    return token ? { personId: '', token } : null
  },

  async me(): Promise<Person> {
    return mapPerson(await memberHttp<PersonRaw>('GET', '/api/v1/me'))
  },

  async uploadPhoto(file: File): Promise<Person> {
    const fd = new FormData()
    fd.append('file', file)
    return mapPerson(await memberHttpMultipart<PersonRaw>('POST', '/api/v1/me/photo', fd))
  },

  async deletePhoto(): Promise<void> {
    await memberHttp<void>('DELETE', '/api/v1/me/photo')
  },

  async updateMe(patch: Partial<Person>): Promise<Person> {
    return mapPerson(
      await memberHttp<PersonRaw>('PATCH', '/api/v1/me', {
        name: patch.name,
        nameBn: patch.nameBn,
        batchYear: patch.batchYear,
        email: patch.email,
        gender: patch.gender,
        dob: patch.dob,
        bloodGroup: patch.bloodGroup,
        occupation: patch.occupation,
        city: patch.city,
      }),
    )
  },

  /* lookup */

  async lookupBatch(year: number, query = ''): Promise<Person[]> {
    const q = query.trim()
    const qs = q ? `?batchYear=${year}&q=${encodeURIComponent(q)}` : `?batchYear=${year}`
    return (await http<PersonRaw[]>('GET', `/api/v1/public/lookup${qs}`)).map(mapPerson)
  },

  /* batches */

  async batches(): Promise<Batch[]> {
    return http<BatchRaw[]>('GET', '/api/v1/batches')
  },

  async batch(year: number): Promise<{ batch: Batch; members: Person[] }> {
    const [all, members] = await Promise.all([
      this.batches(),
      memberHttp<PersonRaw[]>('GET', `/api/v1/batches/${year}/members`),
    ])
    const batch = all.find((b) => b.year === year)
    if (!batch) throw new ApiError('Batch not found', 'ব্যাচ পাওয়া যায়নি')
    return { batch, members: members.map(mapPerson) }
  },

  /* dashboard — aggregated from multiple endpoints */

  async dashboard(): Promise<DashboardData> {
    const [meRaw, allBatches, totals, noticesRaw] = await Promise.all([
      memberHttp<PersonRaw>('GET', '/api/v1/me'),
      http<BatchRaw[]>('GET', '/api/v1/batches'),
      http<TotalsRaw>('GET', '/api/v1/batches/totals'),
      http<NoticeRaw[]>('GET', '/api/v1/notices'),
    ])
    const me = mapPerson(meRaw)
    const batch = allBatches.find((b) => b.year === me.batchYear) ?? allBatches[allBatches.length - 1]

    let registration: Registration | null = null
    let application: Application | null = null
    try {
      const reg = await memberHttp<RegistrationRaw>('GET', '/api/v1/me/registration')
      registration = mapRegistration(reg)
      if (reg.status !== 'DRAFT') application = mapApplication(reg)
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 404)) throw e
    }

    let missingFromBatch: Person[] = []
    try {
      const missing = await memberHttp<PersonRaw[]>('GET', `/api/v1/batches/${batch.year}/missing`)
      missingFromBatch = missing.slice(0, 6).map(mapPerson)
    } catch { /* non-critical */ }

    return {
      me,
      batch,
      event: EVENT,
      registration,
      application,
      totals: { roster: totals.roster, claimed: totals.claimed, batches: totals.batches, registeredForEvent: 0, teachers: 0 },
      notices: noticesRaw.map(mapNotice),
      missingFromBatch,
      profileCompleteness: completeness(me, registration),
    }
  },

  /* notices */

  async notices() {
    return (await http<NoticeRaw[]>('GET', '/api/v1/notices')).map(mapNotice)
  },

  async totals(): Promise<{ roster: number; claimed: number; batches: number }> {
    return http<TotalsRaw>('GET', '/api/v1/batches/totals')
  },

  /* registration */

  async getRegistration(): Promise<Registration | null> {
    const r = await fetchCurrentReg()
    return r ? mapRegistration(r) : null
  },

  async startRegistration(input: { tshirtSize: string; foodPref: Registration['foodPref'] }): Promise<Registration> {
    const current = await fetchCurrentReg()
    return mapRegistration(
      await putRegistration(
        (current?.guests ?? []).map((g) => ({ id: g.id, name: g.name, relation: g.relation, age: g.age, tshirtSize: g.tshirtSize })),
        input.tshirtSize,
        input.foodPref,
        current?.memberNote,
      ),
    )
  },

  async addGuest(guest: Omit<Guest, 'id'>): Promise<Registration> {
    const current = await fetchCurrentReg()
    if ((current?.guests.length ?? 0) >= 8) {
      throw new ApiError(
        'Maximum 8 family members per alumni. Contact the committee for a larger group.',
        'সর্বোচ্চ ৮ জন পরিবারের সদস্য যুক্ত করা যাবে। বেশি হলে কমিটির সাথে যোগাযোগ করুন।',
      )
    }
    const existing = (current?.guests ?? []).map((g) => ({ id: g.id, name: g.name, relation: g.relation, age: g.age, tshirtSize: g.tshirtSize }))
    return mapRegistration(
      await putRegistration(
        [...existing, { name: guest.name, relation: guest.relation, age: guest.age, tshirtSize: guest.tshirtSize }],
        current?.tshirtSize ?? 'L',
        current?.foodPref ?? 'REGULAR',
        current?.memberNote,
      ),
    )
  },

  async updateGuest(id: string, patch: Partial<Guest>): Promise<Registration> {
    const current = await fetchCurrentReg()
    if (!current) throw new ApiError('No registration', 'কোনো রেজিস্ট্রেশন নেই')
    return mapRegistration(
      await putRegistration(
        current.guests.map((g) =>
          g.id === id
            ? { id: g.id, name: patch.name ?? g.name, relation: patch.relation ?? g.relation, age: patch.age ?? g.age, tshirtSize: patch.tshirtSize ?? g.tshirtSize }
            : { id: g.id, name: g.name, relation: g.relation, age: g.age, tshirtSize: g.tshirtSize },
        ),
        current.tshirtSize,
        current.foodPref,
        current.memberNote,
      ),
    )
  },

  async removeGuest(id: string): Promise<Registration> {
    const current = await fetchCurrentReg()
    if (!current) throw new ApiError('No registration', 'কোনো রেজিস্ট্রেশন নেই')
    return mapRegistration(
      await putRegistration(
        current.guests.filter((g) => g.id !== id).map((g) => ({ id: g.id, name: g.name, relation: g.relation, age: g.age, tshirtSize: g.tshirtSize })),
        current.tshirtSize,
        current.foodPref,
        current.memberNote,
      ),
    )
  },

  async submitRegistration(note?: string): Promise<{ registration: Registration; application: Application }> {
    if (note?.trim()) {
      const current = await fetchCurrentReg()
      if (current) {
        await putRegistration(
          current.guests.map((g) => ({ id: g.id, name: g.name, relation: g.relation, age: g.age, tshirtSize: g.tshirtSize })),
          current.tshirtSize,
          current.foodPref,
          note.trim(),
        )
      }
    }
    const dto = await memberHttp<RegistrationRaw>('POST', '/api/v1/me/registration/submit')
    return { registration: mapRegistration(dto), application: mapApplication(dto) }
  },

  async reportPayment(input: {
    method: PaymentMethod
    reference: string
    amount: number
    paidToAdminId?: string
  }): Promise<Application> {
    const dto = await memberHttp<RegistrationRaw>('POST', '/api/v1/me/registration/payment-report', {
      method: input.method,
      reference: input.reference,
      amount: input.amount,
      paidToId: input.paidToAdminId,
    })
    return mapApplication(dto)
  },

  async myApplication(): Promise<Application | null> {
    const r = await fetchCurrentReg()
    return r && r.status !== 'DRAFT' ? mapApplication(r) : null
  },

  async coordinatorsFor(_year: number): Promise<CoordinatorRaw[]> {
    return memberHttp<CoordinatorRaw[]>('GET', '/api/v1/me/registration/coordinators')
  },

  /* referrals */

  async addReferral(input: { name: string; phone: string; batchYear: number }): Promise<void> {
    await memberHttp<void>('POST', '/api/v1/referrals', input)
  },

  async referrals() {
    return [] as { id: string; name: string; phone: string; batchYear: number }[]
  },
}

/* ================================================================== */
/* Admin API                                                            */
/* ================================================================== */

export const adminApi = {
  /* session */

  async login(username: string, password: string): Promise<AdminAccount> {
    const r = await http<{ accessToken: string; expiresInSeconds: number; admin: AdminAccountRaw }>(
      'POST',
      '/api/v1/admin/auth/login',
      { username, password },
    )
    localStorage.setItem(ADMIN_ACCESS, r.accessToken)
    return r.admin
  },

  async logout(): Promise<void> {
    await adminHttp<void>('POST', '/api/v1/admin/auth/logout').catch(() => {})
    localStorage.removeItem(ADMIN_ACCESS)
  },

  getSession(): { adminId: string; token: string } | null {
    const token = localStorage.getItem(ADMIN_ACCESS)
    return token ? { adminId: '', token } : null
  },

  async me(): Promise<AdminAccount | null> {
    try {
      return await adminHttp<AdminAccount>('GET', '/api/v1/admin/me')
    } catch {
      return null
    }
  },

  /* stats */

  async stats(): Promise<AdminStats> {
    return adminHttp<AdminStatsRaw>('GET', '/api/v1/admin/stats')
  },

  /* review queue */

  async applications(filter: ApplicationFilter = {}): Promise<Page<Application>> {
    const params = new URLSearchParams()
    if (filter.memberStatus && filter.memberStatus !== 'ALL') params.set('memberStatus', filter.memberStatus)
    if (filter.paymentStatus && filter.paymentStatus !== 'ALL') params.set('paymentStatus', filter.paymentStatus)
    if (filter.batchYear && filter.batchYear !== 'ALL') params.set('batchYear', String(filter.batchYear))
    if (filter.query?.trim()) params.set('q', filter.query.trim())
    if (filter.cursor) params.set('cursor', filter.cursor)
    if (filter.limit) params.set('limit', String(filter.limit))
    const qs = params.toString()
    const raw = await adminHttp<CursorPageRaw<ApplicationRaw>>('GET', `/api/v1/admin/applications${qs ? '?' + qs : ''}`)
    return { items: raw.items.map(mapAdminApplication), nextCursor: raw.nextCursor, total: raw.total }
  },

  async myBatches(): Promise<number[]> {
    return adminHttp<number[]>('GET', '/api/v1/admin/applications/batch-years')
  },

  /* decisions */

  async reviewMember(applicationId: string, verdict: 'APPROVED' | 'REJECTED', note?: string): Promise<Application> {
    if (verdict === 'REJECTED' && !note?.trim()) {
      throw new ApiError('Write a reason before rejecting.', 'বাতিল করার আগে কারণ লিখুন।')
    }
    return mapAdminApplication(
      await adminHttp<ApplicationRaw>('POST', `/api/v1/admin/applications/${applicationId}/verify`, { decision: verdict, note: note?.trim() }),
    )
  },

  async reviewPayment(applicationId: string, verdict: 'CONFIRMED' | 'REJECTED', note?: string): Promise<Application> {
    if (verdict === 'REJECTED' && !note?.trim()) {
      throw new ApiError('Write a reason before rejecting.', 'বাতিল করার আগে কারণ লিখুন।')
    }
    return mapAdminApplication(
      await adminHttp<ApplicationRaw>('POST', `/api/v1/admin/applications/${applicationId}/payment`, { decision: verdict, note: note?.trim() }),
    )
  },

  async reviewMembersBulk(ids: string[], verdict: 'APPROVED' | 'REJECTED', note?: string): Promise<BulkReviewResult> {
    if (ids.length === 0) throw new ApiError('Select at least one application.', 'অন্তত একটি আবেদন নির্বাচন করুন।')
    if (verdict === 'REJECTED' && !note?.trim()) throw new ApiError('Write a reason before rejecting.', 'বাতিল করার আগে কারণ লিখুন।')
    const raw = await adminHttp<BulkRaw>('POST', '/api/v1/admin/applications/verify', { ids, decision: verdict, note: note?.trim() })
    return { updated: raw.updated.map(mapAdminApplication), skipped: raw.skipped }
  },

  async reviewPaymentsBulk(ids: string[], verdict: 'CONFIRMED' | 'REJECTED', note?: string): Promise<BulkReviewResult> {
    if (ids.length === 0) throw new ApiError('Select at least one application.', 'অন্তত একটি আবেদন নির্বাচন করুন।')
    if (verdict === 'REJECTED' && !note?.trim()) throw new ApiError('Write a reason before rejecting.', 'বাতিল করার আগে কারণ লিখুন।')
    const raw = await adminHttp<BulkRaw>('POST', '/api/v1/admin/applications/payment', { ids, decision: verdict, note: note?.trim() })
    return { updated: raw.updated.map(mapAdminApplication), skipped: raw.skipped }
  },

  /* admin accounts (super admin only) */

  async admins(): Promise<AdminAccount[]> {
    return adminHttp<AdminAccount[]>('GET', '/api/v1/admin/accounts')
  },

  async createAdmin(input: {
    name: string
    nameBn?: string
    username: string
    password: string
    phone: string
    role: AdminRole
    batches: number[]
  }): Promise<AdminAccount> {
    return adminHttp<AdminAccount>('POST', '/api/v1/admin/accounts', input)
  },

  async updateAdmin(
    id: string,
    patch: { name?: string; nameBn?: string; phone?: string; batches?: number[]; active?: boolean },
  ): Promise<AdminAccount> {
    return adminHttp<AdminAccount>('PATCH', `/api/v1/admin/accounts/${id}`, patch)
  },

  async setPassword(id: string, password: string): Promise<void> {
    await adminHttp<void>('POST', `/api/v1/admin/accounts/${id}/password`, { password })
  },

  async deleteAdmin(id: string): Promise<void> {
    await adminHttp<void>('DELETE', `/api/v1/admin/accounts/${id}`)
  },
}
