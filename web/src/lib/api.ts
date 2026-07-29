/**
 * ============================================================================
 *  THE SWAP POINT
 * ============================================================================
 *  Every screen in this app talks to the backend only through `api.*` below.
 *  Right now each method resolves from the seeded dummy dataset after a short
 *  artificial delay, and writes persist to localStorage so the demo keeps state
 *  across reloads.
 *
 *  When the Spring Boot service is ready:
 *    1. set VITE_API_BASE_URL in .env
 *    2. replace each method body with a `http(...)` call — the signatures and
 *       return shapes already match the REST contract in docs/00-SYSTEM-DESIGN.md §5
 *    3. delete src/mock/
 *
 *  No component needs to change. That is the whole point of this file.
 * ============================================================================
 */

import {
  ADMIN_USERS,
  BATCHES,
  DEMO_USER,
  EVENT,
  NOTICES,
  TICKET_TYPES,
  TOTALS,
  adminCoversBatch,
  membersOfBatch,
  seedApplications,
  type AdminRole,
  type AdminUser,
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

const STORAGE_KEY = 'sammalani.demo.v2'
const LATENCY = [180, 420] as const

/** Demo OTP. In production this is a 6-digit code sent over SMS. */
export const DEMO_OTP = '123456'

/** An admin as the rest of the app is allowed to see one — never carries the password. */
export type AdminAccount = Omit<AdminUser, 'password'>

type Store = {
  session: { personId: string; token: string } | null
  me: Person
  registration: Registration | null
  referrals: { id: string; name: string; phone: string; batchYear: number }[]
  claimedExtra: string[]
  applications: Application[]
  admins: AdminUser[]
  adminSession: { adminId: string; token: string } | null
}

function freshStore(): Store {
  return {
    session: null,
    me: { ...DEMO_USER },
    registration: null,
    referrals: [],
    claimedExtra: [],
    applications: seedApplications(),
    admins: ADMIN_USERS.map((a) => ({ ...a })),
    adminSession: null,
  }
}

function load(): Store {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return freshStore()
    return { ...freshStore(), ...(JSON.parse(raw) as Store) }
  } catch {
    return freshStore()
  }
}

function save(s: Store) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(s))
}

let store = load()

function delay<T>(value: T): Promise<T> {
  const ms = LATENCY[0] + Math.random() * (LATENCY[1] - LATENCY[0])
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

function uid(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 9)}`
}

export class ApiError extends Error {
  constructor(
    message: string,
    public messageBn: string,
  ) {
    super(message)
  }
}

/* ------------------------------------------------------------------ */

export type DashboardData = {
  me: Person
  batch: Batch
  event: typeof EVENT
  registration: Registration | null
  application: Application | null
  totals: typeof TOTALS
  notices: typeof NOTICES
  missingFromBatch: Person[]
  profileCompleteness: number
}

function completeness(p: Person, reg: Registration | null): number {
  const checks = [!!p.name, !!p.batchYear, !!p.phone, !!p.occupation, !!p.city, !!reg]
  return Math.round((checks.filter(Boolean).length / checks.length) * 100)
}

/** The signed-in member's own submission, if they have one. */
function myApplication(): Application | null {
  return store.applications.find((a) => a.personId === store.me.id) ?? null
}

function recalc(reg: Registration): Registration {
  const base = TICKET_TYPES.find((t) => t.id === reg.ticketTypeId)?.amount ?? 0
  const guests = reg.guests.reduce(
    (sum, g) => sum + (TICKET_TYPES.find((t) => t.id === g.ticketTypeId)?.amount ?? 0),
    0,
  )
  return { ...reg, amountDue: base + guests }
}

export const api = {
  /* ---------------- auth ---------------- */

  async requestOtp(phone: string): Promise<{ challengeId: string; hint: string }> {
    const clean = phone.replace(/\D/g, '')
    if (!/^01[3-9]\d{8}$/.test(clean)) {
      throw new ApiError(
        'Enter a valid 11-digit Bangladeshi mobile number',
        'সঠিক ১১ সংখ্যার মোবাইল নম্বর দিন',
      )
    }
    return delay({ challengeId: uid('otp'), hint: DEMO_OTP })
  },

  async verifyOtp(_challengeId: string, code: string): Promise<{ token: string; person: Person }> {
    if (code.replace(/\D/g, '') !== DEMO_OTP) {
      throw new ApiError('That code is not correct. Please try again.', 'কোডটি সঠিক নয়। আবার চেষ্টা করুন।')
    }
    const token = uid('tok')
    store.session = { personId: store.me.id, token }
    save(store)
    return delay({ token, person: store.me })
  },

  /** Presentation shortcut — skips OTP so you never fumble in front of an audience. */
  async demoLogin(): Promise<{ token: string; person: Person }> {
    const token = uid('tok')
    store.session = { personId: store.me.id, token }
    save(store)
    return delay({ token, person: store.me })
  },

  async logout(): Promise<void> {
    store.session = null
    save(store)
    return delay(undefined)
  },

  getSession(): { personId: string; token: string } | null {
    return store.session
  },

  async me(): Promise<Person> {
    return delay(store.me)
  },

  async updateMe(patch: Partial<Person>): Promise<Person> {
    store.me = { ...store.me, ...patch }
    save(store)
    return delay(store.me)
  },

  /* ---------------- claim / signup ---------------- */

  /** Roster lookup for a batch. Returns name + batch only — never contact details. */
  async lookupBatch(year: number, query = ''): Promise<Person[]> {
    const q = query.trim().toLowerCase()
    const all = membersOfBatch(year).filter((p) => !p.deceased)
    const filtered = q ? all.filter((p) => p.name.toLowerCase().includes(q) || p.nameBn.includes(query.trim())) : all
    return delay(filtered)
  },

  async claimProfile(personId: string, phone: string, year: number): Promise<{ challengeId: string; hint: string }> {
    void personId
    void year
    return this.requestOtp(phone)
  },

  async completeClaim(person: Pick<Person, 'name' | 'nameBn' | 'batchYear'>, phone: string): Promise<Person> {
    store.me = {
      ...store.me,
      name: person.name,
      nameBn: person.nameBn,
      batchYear: person.batchYear,
      phone,
      status: 'CLAIMED',
    }
    store.session = { personId: store.me.id, token: uid('tok') }

    // Signing up *is* the identity claim, so it goes straight into the
    // coordinator's queue — a member who never buys a ticket still gets verified.
    store.applications = [
      {
        id: uid('app'),
        personId: store.me.id,
        name: store.me.name,
        nameBn: store.me.nameBn,
        batchYear: store.me.batchYear,
        phone,
        guests: [],
        amountDue: TICKET_TYPES.find((tt) => tt.id === 'tt-alumni')?.amount ?? 0,
        submittedAt: new Date().toISOString(),
        memberStatus: 'PENDING',
        paymentStatus: 'UNPAID',
      },
      ...store.applications.filter((a) => a.personId !== store.me.id),
    ]

    save(store)
    return delay(store.me)
  },

  /* ---------------- batches ---------------- */

  async batches(): Promise<Batch[]> {
    return delay(BATCHES)
  },

  async batch(year: number): Promise<{ batch: Batch; members: Person[] }> {
    const batch = BATCHES.find((b) => b.year === year)
    if (!batch) throw new ApiError('Batch not found', 'ব্যাচ পাওয়া যায়নি')
    return delay({ batch, members: membersOfBatch(year) })
  },

  /* ---------------- dashboard ---------------- */

  async dashboard(): Promise<DashboardData> {
    const batch = BATCHES.find((b) => b.year === store.me.batchYear) ?? BATCHES[BATCHES.length - 1]
    const missing = membersOfBatch(batch.year)
      .filter((p) => p.status === 'SEEDED' && !p.deceased)
      .slice(0, 6)
    return delay({
      me: store.me,
      batch,
      event: EVENT,
      registration: store.registration,
      application: myApplication(),
      totals: TOTALS,
      notices: NOTICES,
      missingFromBatch: missing,
      profileCompleteness: completeness(store.me, store.registration),
    })
  },

  /* ---------------- registration & guests ---------------- */

  async getRegistration(): Promise<Registration | null> {
    return delay(store.registration)
  },

  async startRegistration(input: {
    tshirtSize: string
    foodPref: Registration['foodPref']
  }): Promise<Registration> {
    const existing = store.registration
    const reg: Registration = recalc({
      id: existing?.id ?? uid('reg'),
      personId: store.me.id,
      ticketTypeId: 'tt-alumni',
      guests: existing?.guests ?? [],
      tshirtSize: input.tshirtSize,
      foodPref: input.foodPref,
      status: existing?.status ?? 'DRAFT',
      amountDue: 0,
      createdAt: existing?.createdAt ?? new Date().toISOString(),
      submittedAt: existing?.submittedAt,
      applicationId: existing?.applicationId,
    })
    store.registration = reg
    save(store)
    return delay(reg)
  },

  async addGuest(guest: Omit<Guest, 'id'>): Promise<Registration> {
    if (!store.registration) {
      store.registration = recalc({
        id: uid('reg'),
        personId: store.me.id,
        ticketTypeId: 'tt-alumni',
        guests: [],
        tshirtSize: 'L',
        foodPref: 'REGULAR',
        status: 'DRAFT',
        amountDue: 0,
        createdAt: new Date().toISOString(),
      })
    }
    if (store.registration.status !== 'DRAFT' && store.registration.status !== 'REJECTED') {
      throw new ApiError(
        'Your registration is already with the coordinator. Contact them to change it.',
        'আপনার নিবন্ধন সমন্বয়কারীর কাছে জমা আছে। পরিবর্তনের জন্য তাঁর সাথে যোগাযোগ করুন।',
      )
    }
    if (store.registration.guests.length >= 8) {
      throw new ApiError(
        'Maximum 8 family members per alumni. Contact the committee for a larger group.',
        'সর্বোচ্চ ৮ জন পরিবারের সদস্য যুক্ত করা যাবে। বেশি হলে কমিটির সাথে যোগাযোগ করুন।',
      )
    }
    store.registration.guests.push({ ...guest, id: uid('g') })
    store.registration = recalc(store.registration)
    save(store)
    return delay(store.registration)
  },

  async updateGuest(id: string, patch: Partial<Guest>): Promise<Registration> {
    if (!store.registration) throw new ApiError('No registration', 'কোনো রেজিস্ট্রেশন নেই')
    store.registration.guests = store.registration.guests.map((g) => (g.id === id ? { ...g, ...patch } : g))
    store.registration = recalc(store.registration)
    save(store)
    return delay(store.registration)
  },

  async removeGuest(id: string): Promise<Registration> {
    if (!store.registration) throw new ApiError('No registration', 'কোনো রেজিস্ট্রেশন নেই')
    store.registration.guests = store.registration.guests.filter((g) => g.id !== id)
    store.registration = recalc(store.registration)
    save(store)
    return delay(store.registration)
  },

  /* ---------------- approval, not checkout ---------------- */

  /**
   * Sends the registration to the batch coordinator. There is no payment step
   * here on purpose — money changes hands offline and an admin confirms it.
   */
  async submitRegistration(note?: string): Promise<{ registration: Registration; application: Application }> {
    if (!store.registration) throw new ApiError('No registration', 'কোনো রেজিস্ট্রেশন নেই')
    if (store.registration.status === 'SUBMITTED' || store.registration.status === 'APPROVED') {
      throw new ApiError('Already submitted', 'ইতিমধ্যে জমা দেওয়া হয়েছে')
    }

    const now = new Date().toISOString()
    const me = store.me
    const existing = myApplication()
    const app: Application = {
      id: existing?.id ?? uid('app'),
      personId: me.id,
      name: me.name,
      nameBn: me.nameBn,
      batchYear: me.batchYear,
      phone: me.phone ?? '',
      email: me.email,
      gender: me.gender,
      dob: me.dob,
      bloodGroup: me.bloodGroup,
      occupation: me.occupation,
      city: me.city,
      guests: store.registration.guests.map((g) => ({ ...g })),
      amountDue: store.registration.amountDue,
      submittedAt: now,
      memberNote: note?.trim() || undefined,
      // An already-verified member stays verified when they add family later;
      // anything else goes back into the queue for a fresh look.
      memberStatus: existing?.memberStatus === 'APPROVED' ? 'APPROVED' : 'PENDING',
      memberReview: existing?.memberStatus === 'APPROVED' ? existing.memberReview : undefined,
      paymentStatus: existing?.paymentStatus ?? 'UNPAID',
      payment: existing?.payment,
      paymentReview: existing?.paymentReview,
    }

    // One record per member — re-submitting replaces it rather than piling up.
    store.applications = [app, ...store.applications.filter((a) => a.personId !== me.id)]
    store.registration = { ...store.registration, status: 'SUBMITTED', submittedAt: now, applicationId: app.id }
    save(store)
    return delay({ registration: store.registration, application: app })
  },

  /** The member tells the coordinator what they paid. The coordinator still has to confirm it. */
  async reportPayment(input: {
    method: PaymentMethod
    reference: string
    amount: number
    paidToAdminId?: string
  }): Promise<Application> {
    const app = myApplication()
    if (!app) throw new ApiError('Submit your registration first', 'আগে আপনার নিবন্ধন জমা দিন')
    if (!input.reference.trim()) {
      throw new ApiError('Enter the transaction or slip number', 'লেনদেন বা স্লিপ নম্বর দিন')
    }
    app.payment = {
      method: input.method,
      reference: input.reference.trim(),
      amount: input.amount,
      paidToAdminId: input.paidToAdminId,
      reportedAt: new Date().toISOString(),
    }
    app.paymentStatus = 'REPORTED'
    app.paymentReview = undefined
    save(store)
    return delay(app)
  },

  async myApplication(): Promise<Application | null> {
    return delay(myApplication())
  },

  /**
   * Who to pay, for a given batch. Name and phone only — never the account row,
   * and never a password.
   */
  async coordinatorsFor(year: number): Promise<{ id: string; name: string; nameBn: string; phone: string }[]> {
    const list = store.admins
      .filter((a) => a.active && adminCoversBatch(a, year))
      .map((a) => ({ id: a.id, name: a.name, nameBn: a.nameBn, phone: a.phone }))
    return delay(list)
  },

  /* ---------------- referrals ---------------- */

  async addReferral(input: { name: string; phone: string; batchYear: number }): Promise<void> {
    store.referrals.push({ id: uid('ref'), ...input })
    save(store)
    return delay(undefined)
  },

  async referrals() {
    return delay(store.referrals)
  },

  /* ---------------- demo control ---------------- */

  resetDemo() {
    localStorage.removeItem(STORAGE_KEY)
    store = freshStore()
  },
}

/* ==================================================================== *
 *  ADMIN API
 *
 *  Deliberately a separate object with its own session. An admin token is
 *  never the same thing as a member token, and no member-facing screen can
 *  reach these calls by accident.
 * ==================================================================== */

function strip(a: AdminUser): AdminAccount {
  const { password: _password, ...rest } = a
  return rest
}

function currentAdmin(): AdminUser {
  const id = store.adminSession?.adminId
  const admin = id ? store.admins.find((a) => a.id === id) : undefined
  if (!admin || !admin.active) {
    throw new ApiError('Your admin session has ended. Please sign in again.', 'আপনার অ্যাডমিন সেশন শেষ হয়েছে। আবার লগইন করুন।')
  }
  return admin
}

function requireSuper(): AdminUser {
  const admin = currentAdmin()
  if (admin.role !== 'SUPER_ADMIN') {
    throw new ApiError('Only a super admin can do that.', 'শুধু সুপার অ্যাডমিন এই কাজটি করতে পারেন।')
  }
  return admin
}

/** Applications the given admin is allowed to see at all. */
function visibleTo(admin: AdminUser): Application[] {
  return store.applications.filter((a) => adminCoversBatch(admin, a.batchYear))
}

export type AdminStats = {
  pendingMembers: number
  pendingPayments: number
  approvedMembers: number
  rejectedMembers: number
  confirmedAmount: number
  outstandingAmount: number
  batchesCovered: number
}

export type ApplicationFilter = {
  memberStatus?: ReviewStatus | 'ALL'
  paymentStatus?: PaymentStatus | 'ALL'
  batchYear?: number | 'ALL'
  query?: string
}

/**
 * The outcome of a bulk decision. Deliberately partial rather than all-or-nothing:
 * a coordinator who ticks forty rows and hits Confirm should not lose thirty-nine
 * good decisions because one row slipped out of their batch scope in the meantime.
 * Anything that could not be decided comes back named, so the UI can say why.
 */
export type BulkReviewResult = {
  updated: Application[]
  skipped: { id: string; name: string; reason: string; reasonBn: string }[]
}

/** A rejection always needs a reason — the member is told what to fix. */
function requireReason(isRejection: boolean, note?: string) {
  if (isRejection && !note?.trim()) {
    throw new ApiError('Write a reason before rejecting.', 'বাতিল করার আগে কারণ লিখুন।')
  }
}

function requireSelection(ids: string[]) {
  if (ids.length === 0) {
    throw new ApiError('Select at least one application.', 'অন্তত একটি আবেদন নির্বাচন করুন।')
  }
}

function stamp(admin: AdminUser, note?: string) {
  return { adminId: admin.id, adminName: admin.name, at: new Date().toISOString(), note: note?.trim() || undefined }
}

/**
 * Resolve one id for the given admin, or explain why it cannot be decided.
 * The single-item and bulk paths both go through here, so the scope check and the
 * "no payment reported yet" guard cannot drift apart between them.
 */
function resolveForDecision(
  admin: AdminUser,
  id: string,
  guard?: (app: Application) => { reason: string; reasonBn: string } | null,
): { app: Application } | { skip: BulkReviewResult['skipped'][number] } {
  const app = store.applications.find((a) => a.id === id)
  if (!app) {
    return { skip: { id, name: id, reason: 'Application not found', reasonBn: 'আবেদনটি পাওয়া যায়নি' } }
  }
  if (!adminCoversBatch(admin, app.batchYear)) {
    return {
      skip: {
        id,
        name: app.name,
        reason: 'This batch is outside your assignment.',
        reasonBn: 'এই ব্যাচটি আপনার দায়িত্বের বাইরে।',
      },
    }
  }
  const blocked = guard?.(app)
  if (blocked) return { skip: { id, name: app.name, ...blocked } }
  return { app }
}

/** A payment cannot be confirmed before the member has reported one. */
function paymentReportedGuard(verdict: 'CONFIRMED' | 'REJECTED') {
  return (app: Application) =>
    verdict === 'CONFIRMED' && !app.payment
      ? {
          reason: 'The member has not reported a payment yet.',
          reasonBn: 'সদস্য এখনো পেমেন্টের তথ্য দেননি।',
        }
      : null
}

export const adminApi = {
  /* ---------------- session ---------------- */

  async login(username: string, password: string): Promise<AdminAccount> {
    const found = store.admins.find((a) => a.username.toLowerCase() === username.trim().toLowerCase())
    if (!found || found.password !== password) {
      throw new ApiError('Wrong username or password.', 'ব্যবহারকারীর নাম বা পাসওয়ার্ড ভুল।')
    }
    if (!found.active) {
      throw new ApiError('This account has been disabled.', 'এই অ্যাকাউন্টটি বন্ধ করা হয়েছে।')
    }
    store.adminSession = { adminId: found.id, token: uid('atok') }
    save(store)
    return delay(strip(found))
  },

  async logout(): Promise<void> {
    store.adminSession = null
    save(store)
    return delay(undefined)
  },

  getSession(): { adminId: string; token: string } | null {
    return store.adminSession
  },

  async me(): Promise<AdminAccount | null> {
    const id = store.adminSession?.adminId
    const admin = id ? store.admins.find((a) => a.id === id) : undefined
    return delay(admin && admin.active ? strip(admin) : null)
  },

  /* ---------------- review queue ---------------- */

  async stats(): Promise<AdminStats> {
    const admin = currentAdmin()
    const mine = visibleTo(admin)
    return delay({
      pendingMembers: mine.filter((a) => a.memberStatus === 'PENDING').length,
      pendingPayments: mine.filter((a) => a.paymentStatus === 'REPORTED').length,
      approvedMembers: mine.filter((a) => a.memberStatus === 'APPROVED').length,
      rejectedMembers: mine.filter((a) => a.memberStatus === 'REJECTED').length,
      confirmedAmount: mine
        .filter((a) => a.paymentStatus === 'CONFIRMED')
        .reduce((s, a) => s + (a.payment?.amount ?? 0), 0),
      outstandingAmount: mine
        .filter((a) => a.memberStatus !== 'REJECTED' && a.paymentStatus !== 'CONFIRMED')
        .reduce((s, a) => s + a.amountDue, 0),
      batchesCovered: admin.role === 'SUPER_ADMIN' ? BATCHES.length : admin.batches.length,
    })
  },

  async applications(filter: ApplicationFilter = {}): Promise<Application[]> {
    const admin = currentAdmin()
    const q = filter.query?.trim().toLowerCase() ?? ''
    const list = visibleTo(admin).filter((a) => {
      if (filter.memberStatus && filter.memberStatus !== 'ALL' && a.memberStatus !== filter.memberStatus) return false
      if (filter.paymentStatus && filter.paymentStatus !== 'ALL' && a.paymentStatus !== filter.paymentStatus) return false
      if (filter.batchYear && filter.batchYear !== 'ALL' && a.batchYear !== filter.batchYear) return false
      if (q) {
        const hay = `${a.name} ${a.nameBn} ${a.phone} ${a.batchYear} ${a.email ?? ''} ${a.payment?.reference ?? ''}`
        if (!hay.toLowerCase().includes(q)) return false
      }
      return true
    })
    return delay(list)
  },

  /** Batch years this admin may act on, for filter dropdowns. */
  async myBatches(): Promise<number[]> {
    const admin = currentAdmin()
    return delay(admin.role === 'SUPER_ADMIN' ? BATCHES.map((b) => b.year) : [...admin.batches].sort((a, b) => a - b))
  },

  /* ---------------- decisions ---------------- */

  async reviewMember(applicationId: string, verdict: 'APPROVED' | 'REJECTED', note?: string): Promise<Application> {
    const admin = currentAdmin()
    requireReason(verdict === 'REJECTED', note)
    const found = resolveForDecision(admin, applicationId)
    if ('skip' in found) throw new ApiError(found.skip.reason, found.skip.reasonBn)

    const app = found.app
    app.memberStatus = verdict
    app.memberReview = stamp(admin, note)
    syncOwnRegistration(app)
    save(store)
    return delay(app)
  },

  async reviewPayment(applicationId: string, verdict: 'CONFIRMED' | 'REJECTED', note?: string): Promise<Application> {
    const admin = currentAdmin()
    requireReason(verdict === 'REJECTED', note)
    const found = resolveForDecision(admin, applicationId, paymentReportedGuard(verdict))
    if ('skip' in found) throw new ApiError(found.skip.reason, found.skip.reasonBn)

    const app = found.app
    app.paymentStatus = verdict
    app.paymentReview = stamp(admin, note)
    save(store)
    return delay(app)
  },

  /* ---------------- bulk decisions ----------------
   *
   * One decision, one note, many applications. The note is written onto every
   * row, which is why a bulk rejection demands a reason exactly as a single one
   * does: "not on the 1996 register" has to reach each member individually.
   */

  async reviewMembersBulk(
    applicationIds: string[],
    verdict: 'APPROVED' | 'REJECTED',
    note?: string,
  ): Promise<BulkReviewResult> {
    const admin = currentAdmin()
    requireSelection(applicationIds)
    requireReason(verdict === 'REJECTED', note)

    const result: BulkReviewResult = { updated: [], skipped: [] }
    for (const id of applicationIds) {
      const found = resolveForDecision(admin, id)
      if ('skip' in found) {
        result.skipped.push(found.skip)
        continue
      }
      const app = found.app
      app.memberStatus = verdict
      app.memberReview = stamp(admin, note)
      syncOwnRegistration(app)
      result.updated.push(app)
    }
    save(store)
    return delay(result)
  },

  async reviewPaymentsBulk(
    applicationIds: string[],
    verdict: 'CONFIRMED' | 'REJECTED',
    note?: string,
  ): Promise<BulkReviewResult> {
    const admin = currentAdmin()
    requireSelection(applicationIds)
    requireReason(verdict === 'REJECTED', note)

    const result: BulkReviewResult = { updated: [], skipped: [] }
    for (const id of applicationIds) {
      const found = resolveForDecision(admin, id, paymentReportedGuard(verdict))
      if ('skip' in found) {
        result.skipped.push(found.skip)
        continue
      }
      const app = found.app
      app.paymentStatus = verdict
      app.paymentReview = stamp(admin, note)
      result.updated.push(app)
    }
    save(store)
    return delay(result)
  },

  /* ---------------- admin accounts (super admin only) ---------------- */

  async admins(): Promise<AdminAccount[]> {
    requireSuper()
    return delay(store.admins.map(strip))
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
    const superAdmin = requireSuper()
    const username = input.username.trim().toLowerCase()
    if (username.length < 3) throw new ApiError('Username is too short.', 'ব্যবহারকারীর নাম খুব ছোট।')
    if (input.password.length < 8) {
      throw new ApiError('Password must be at least 8 characters.', 'পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে।')
    }
    if (store.admins.some((a) => a.username.toLowerCase() === username)) {
      throw new ApiError('That username is already taken.', 'এই ব্যবহারকারীর নামটি ইতিমধ্যে ব্যবহৃত হয়েছে।')
    }
    if (input.role === 'GROUP_ADMIN' && input.batches.length === 0) {
      throw new ApiError('Assign at least one batch to a group admin.', 'গ্রুপ অ্যাডমিনকে অন্তত একটি ব্যাচ দিন।')
    }
    const created: AdminUser = {
      id: uid('adm'),
      name: input.name.trim(),
      nameBn: input.nameBn?.trim() || input.name.trim(),
      username,
      password: input.password,
      phone: input.phone.trim(),
      role: input.role,
      batches: input.role === 'SUPER_ADMIN' ? [] : [...input.batches].sort((a, b) => a - b),
      active: true,
      createdAt: new Date().toISOString(),
      createdByAdminId: superAdmin.id,
    }
    store.admins.push(created)
    save(store)
    return delay(strip(created))
  },

  async updateAdmin(
    id: string,
    patch: { name?: string; nameBn?: string; phone?: string; batches?: number[]; active?: boolean },
  ): Promise<AdminAccount> {
    requireSuper()
    const admin = store.admins.find((a) => a.id === id)
    if (!admin) throw new ApiError('Admin not found', 'অ্যাডমিন পাওয়া যায়নি')
    if (admin.role === 'SUPER_ADMIN' && patch.active === false) {
      throw new ApiError('A super admin cannot be disabled.', 'সুপার অ্যাডমিন বন্ধ করা যাবে না।')
    }
    if (patch.name !== undefined) admin.name = patch.name.trim()
    if (patch.nameBn !== undefined) admin.nameBn = patch.nameBn.trim()
    if (patch.phone !== undefined) admin.phone = patch.phone.trim()
    if (patch.active !== undefined) admin.active = patch.active
    if (patch.batches !== undefined && admin.role === 'GROUP_ADMIN') {
      if (patch.batches.length === 0) {
        throw new ApiError('Assign at least one batch to a group admin.', 'গ্রুপ অ্যাডমিনকে অন্তত একটি ব্যাচ দিন।')
      }
      admin.batches = [...patch.batches].sort((a, b) => a - b)
    }
    save(store)
    return delay(strip(admin))
  },

  /** Super admin sets the password. There is no self-serve reset — by design. */
  async setPassword(id: string, password: string): Promise<void> {
    requireSuper()
    const admin = store.admins.find((a) => a.id === id)
    if (!admin) throw new ApiError('Admin not found', 'অ্যাডমিন পাওয়া যায়নি')
    if (password.length < 8) {
      throw new ApiError('Password must be at least 8 characters.', 'পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে।')
    }
    admin.password = password
    save(store)
    return delay(undefined)
  },

  async deleteAdmin(id: string): Promise<void> {
    const superAdmin = requireSuper()
    if (id === superAdmin.id) throw new ApiError('You cannot remove your own account.', 'নিজের অ্যাকাউন্ট মুছে ফেলা যাবে না।')
    const admin = store.admins.find((a) => a.id === id)
    if (admin?.role === 'SUPER_ADMIN') {
      throw new ApiError('A super admin cannot be removed here.', 'সুপার অ্যাডমিন এখান থেকে মুছে ফেলা যাবে না।')
    }
    store.admins = store.admins.filter((a) => a.id !== id)
    save(store)
    return delay(undefined)
  },
}

/**
 * Keep the demo user's own registration card in step with the coordinator's
 * decision, so approving yourself in one tab shows up on the dashboard in the other.
 */
function syncOwnRegistration(app: Application) {
  if (!store.registration || app.personId !== store.me.id) return
  if (app.memberStatus === 'APPROVED') store.registration.status = 'APPROVED'
  else if (app.memberStatus === 'REJECTED') store.registration.status = 'REJECTED'
  else store.registration.status = 'SUBMITTED'
}

export type {
  AdminRole,
  AdminUser,
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
export { TICKET_TYPES, EVENT, TOTALS, BATCHES }
