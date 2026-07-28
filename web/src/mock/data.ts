/**
 * Seeded dummy dataset for the demo.
 *
 * Everything here is generated from a fixed seed, so the numbers are identical on
 * every machine and every reload — important when you are presenting live.
 *
 * When the Java backend is ready, nothing in this file is used any more.
 * Only `src/lib/api.ts` changes. See README §7.
 */

export const SCHOOL = {
  nameEn: 'Sammalani Secondary School',
  nameBn: 'সম্মিলনী মাধ্যমিক বিদ্যালয়',
  locationEn: 'Chalitatala, Narail',
  locationBn: 'চলিতাতলা, নড়াইল',
  established: 1968,
  firstBatch: 1968,
  lastBatch: 2026,
}

export const EVENT = {
  id: 'evt-reunion-2027',
  titleEn: 'Grand Reunion 2027',
  titleBn: 'মহা পুনর্মিলনী ২০২৭',
  subtitleEn: 'Batches 1968 – 2026 · One day, together again',
  subtitleBn: 'ব্যাচ ১৯৬৮ – ২০২৬ · একটি দিন, আবার একসাথে',
  date: '2027-02-12T09:00:00+06:00',
  endDate: '2027-02-12T22:00:00+06:00',
  venueEn: 'School Campus, Chalitatala, Narail',
  venueBn: 'বিদ্যালয় প্রাঙ্গণ, চলিতাতলা, নড়াইল',
}

export type TicketType = {
  id: string
  nameEn: string
  nameBn: string
  amount: number
  noteEn: string
  noteBn: string
  relation?: GuestRelation
}

export const TICKET_TYPES: TicketType[] = [
  {
    id: 'tt-alumni',
    nameEn: 'Alumni',
    nameBn: 'প্রাক্তন শিক্ষার্থী',
    amount: 1500,
    noteEn: 'Registration, lunch, T-shirt, souvenir',
    noteBn: 'রেজিস্ট্রেশন, দুপুরের খাবার, টি-শার্ট, স্মরণিকা',
  },
  {
    id: 'tt-spouse',
    nameEn: 'Spouse',
    nameBn: 'স্বামী / স্ত্রী',
    amount: 1200,
    noteEn: 'Lunch, souvenir',
    noteBn: 'দুপুরের খাবার, স্মরণিকা',
    relation: 'SPOUSE',
  },
  {
    id: 'tt-child',
    nameEn: 'Child (5–12 yrs)',
    nameBn: 'শিশু (৫–১২ বছর)',
    amount: 600,
    noteEn: 'Lunch, kids corner',
    noteBn: 'দুপুরের খাবার, শিশু কর্নার',
    relation: 'CHILD',
  },
  {
    id: 'tt-child-free',
    nameEn: 'Child (under 5)',
    nameBn: 'শিশু (৫ বছরের নিচে)',
    amount: 0,
    noteEn: 'Free — no seat allotted',
    noteBn: 'বিনামূল্যে — আলাদা আসন নেই',
    relation: 'CHILD',
  },
  {
    id: 'tt-guest',
    nameEn: 'Other family member',
    nameBn: 'পরিবারের অন্য সদস্য',
    amount: 1200,
    noteEn: 'Parent, sibling or other guest',
    noteBn: 'পিতা-মাতা, ভাই-বোন বা অন্য অতিথি',
    relation: 'OTHER',
  },
]

export type GuestRelation = 'SPOUSE' | 'CHILD' | 'PARENT' | 'SIBLING' | 'OTHER'

export type Guest = {
  id: string
  name: string
  relation: GuestRelation
  age?: number
  ticketTypeId: string
  tshirtSize?: string
}

export type Gender = 'MALE' | 'FEMALE' | 'OTHER'
export const GENDERS: Gender[] = ['MALE', 'FEMALE', 'OTHER']

export const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'] as const
export type BloodGroup = (typeof BLOOD_GROUPS)[number]

export type Person = {
  id: string
  name: string
  nameBn: string
  batchYear: number
  status: 'SEEDED' | 'CLAIMED'
  phone?: string
  /** All four below are optional by design — required fields stay capped at name + batch + phone. */
  email?: string
  gender?: Gender
  dob?: string
  bloodGroup?: BloodGroup
  occupation?: string
  city?: string
  deceased?: boolean
}

/**
 * A member's own registration. There is no payment gateway: `SUBMITTED` means the
 * member has sent it to their batch coordinator, who approves or rejects by hand.
 */
export type Registration = {
  id: string
  personId: string
  ticketTypeId: string
  guests: Guest[]
  tshirtSize: string
  foodPref: 'REGULAR' | 'NO_BEEF' | 'VEG'
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'
  amountDue: number
  createdAt: string
  submittedAt?: string
  applicationId?: string
}

/* ------------------------------------------------------------------ *
 * Deterministic PRNG so the demo shows identical numbers every time
 * ------------------------------------------------------------------ */
function mulberry32(seed: number) {
  return function () {
    seed |= 0
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

type NamePair = [string, string]

const MALE: NamePair[] = [
  ['Abdul Karim', 'আব্দুল করিম'],
  ['Rafiqul Islam', 'রফিকুল ইসলাম'],
  ['Shahidul Haque', 'শহীদুল হক'],
  ['Nurul Amin', 'নুরুল আমিন'],
  ['Mizanur Rahman', 'মিজানুর রহমান'],
  ['Anwar Hossain', 'আনোয়ার হোসেন'],
  ['Kamrul Hasan', 'কামরুল হাসান'],
  ['Golam Mostafa', 'গোলাম মোস্তফা'],
  ['Habibur Rahman', 'হাবিবুর রহমান'],
  ['Jahangir Alam', 'জাহাঙ্গীর আলম'],
  ['Shafiqul Islam', 'শফিকুল ইসলাম'],
  ['Aminul Islam', 'আমিনুল ইসলাম'],
  ['Moshiur Rahman', 'মশিউর রহমান'],
  ['Delwar Hossain', 'দেলোয়ার হোসেন'],
  ['Sirajul Islam', 'সিরাজুল ইসলাম'],
  ['Ashraful Alam', 'আশরাফুল আলম'],
  ['Mahbubur Rahman', 'মাহবুবুর রহমান'],
  ['Nazrul Islam', 'নজরুল ইসলাম'],
  ['Faridul Haque', 'ফরিদুল হক'],
  ['Saiful Islam', 'সাইফুল ইসলাম'],
  ['Rezaul Karim', 'রেজাউল করিম'],
  ['Abdus Salam', 'আব্দুস সালাম'],
  ['Lutfor Rahman', 'লুৎফর রহমান'],
  ['Bazlur Rashid', 'বজলুর রশিদ'],
  ['Masum Billah', 'মাসুম বিল্লাহ'],
  ['Tanvir Ahmed', 'তানভীর আহমেদ'],
  ['Sabbir Hossain', 'সাব্বির হোসেন'],
  ['Rakibul Hasan', 'রাকিবুল হাসান'],
  ['Imran Khan', 'ইমরান খান'],
  ['Arifur Rahman', 'আরিফুর রহমান'],
  ['Mehedi Hasan', 'মেহেদী হাসান'],
  ['Nayeem Islam', 'নাইম ইসলাম'],
  ['Sohel Rana', 'সোহেল রানা'],
  ['Ashiqur Rahman', 'আশিকুর রহমান'],
  ['Jubayer Ahmed', 'জুবায়ের আহমেদ'],
]

const FEMALE: NamePair[] = [
  ['Rokeya Begum', 'রোকেয়া বেগম'],
  ['Hasina Khatun', 'হাসিনা খাতুন'],
  ['Nasrin Sultana', 'নাসরিন সুলতানা'],
  ['Shirin Akter', 'শিরিন আক্তার'],
  ['Rahima Khatun', 'রহিমা খাতুন'],
  ['Fatema Begum', 'ফাতেমা বেগম'],
  ['Salma Akter', 'সালমা আক্তার'],
  ['Ruma Parvin', 'রুমা পারভীন'],
  ['Shahnaz Parvin', 'শাহনাজ পারভীন'],
  ['Marzia Khatun', 'মারজিয়া খাতুন'],
  ['Sadia Afrin', 'সাদিয়া আফরিন'],
  ['Nusrat Jahan', 'নুসরাত জাহান'],
  ['Sumaiya Islam', 'সুমাইয়া ইসলাম'],
  ['Jannatul Ferdous', 'জান্নাতুল ফেরদৌস'],
  ['Mim Akter', 'মীম আক্তার'],
  ['Taslima Akter', 'তাসলিমা আক্তার'],
  ['Anjuman Ara', 'আনজুমান আরা'],
  ['Momtaz Begum', 'মমতাজ বেগম'],
]

const OCCUPATIONS_OLD = [
  'Retired Headmaster',
  'Retired Govt. Officer',
  'Farmer',
  'Businessman',
  'Retired Bank Officer',
  'Homemaker',
  'Retired Army',
  'Pharmacist',
  'Retired Teacher',
  'Imam',
]
const OCCUPATIONS_MID = [
  'School Teacher',
  'Bank Officer',
  'Businessman',
  'Doctor',
  'Advocate',
  'Govt. Service',
  'Engineer',
  'NGO Worker',
  'Contractor',
  'Homemaker',
  'Journalist',
  'Police Service',
]
const OCCUPATIONS_NEW = [
  'Software Engineer',
  'University Student',
  'Doctor',
  'Banker',
  'Freelancer',
  'Teacher',
  'Civil Engineer',
  'Garments Professional',
  'Entrepreneur',
  'BCS Cadre',
  'Pharmacist',
  'Nurse',
]

const CITIES_LOCAL = ['Narail', 'Lohagara', 'Kalia', 'Jashore', 'Chalitatala', 'Khulna']
const CITIES_BD = ['Dhaka', 'Chattogram', 'Khulna', 'Rajshahi', 'Sylhet', 'Barishal', 'Narail']
const CITIES_ABROAD = ['Dubai, UAE', 'Riyadh, KSA', 'Kuala Lumpur', 'London, UK', 'Toronto, Canada', 'New York, USA', 'Rome, Italy', 'Singapore']

/** Older batches are less well covered — this is the real-world shape, and the point of the demo. */
function coverageFor(year: number): number {
  if (year <= 1975) return 0.14
  if (year <= 1985) return 0.2
  if (year <= 1995) return 0.31
  if (year <= 2005) return 0.44
  if (year <= 2015) return 0.62
  return 0.71
}

function rosterSizeFor(year: number): number {
  // The school grew over time: ~35 students in 1968, ~130 by 2026
  const t = (year - 1968) / (2026 - 1968)
  return Math.round(34 + t * 96)
}

export type Batch = {
  year: number
  rosterCount: number
  claimedCount: number
}

export const BATCHES: Batch[] = (() => {
  const out: Batch[] = []
  for (let y = SCHOOL.firstBatch; y <= SCHOOL.lastBatch; y++) {
    const rnd = mulberry32(y * 7919)
    const roster = rosterSizeFor(y)
    const jitter = 0.85 + rnd() * 0.3
    out.push({
      year: y,
      rosterCount: roster,
      claimedCount: Math.min(roster, Math.round(roster * coverageFor(y) * jitter)),
    })
  }
  return out
})()

export const TOTALS = {
  roster: BATCHES.reduce((s, b) => s + b.rosterCount, 0),
  claimed: BATCHES.reduce((s, b) => s + b.claimedCount, 0),
  batches: BATCHES.length,
  registeredForEvent: 1042,
  teachers: 47,
}

/** Members are generated on demand, per batch — never all 5,000 at once. */
export function membersOfBatch(year: number): Person[] {
  const batch = BATCHES.find((b) => b.year === year)
  if (!batch) return []
  const rnd = mulberry32(year * 104729)
  const people: Person[] = []

  const occPool = year <= 1985 ? OCCUPATIONS_OLD : year <= 2008 ? OCCUPATIONS_MID : OCCUPATIONS_NEW
  const femaleShare = year < 1980 ? 0.12 : year < 1995 ? 0.28 : 0.44

  for (let i = 0; i < batch.rosterCount; i++) {
    const isFemale = rnd() < femaleShare
    const pool = isFemale ? FEMALE : MALE
    const [en, bn] = pool[Math.floor(rnd() * pool.length)]
    const prefixEn = isFemale ? 'Mst. ' : 'Md. '
    const prefixBn = isFemale ? 'মোছাঃ ' : 'মোঃ '
    const useprefix = rnd() < (year < 2000 ? 0.75 : 0.4)

    const claimed = i < batch.claimedCount
    // Deceased is a real and significant fact for the oldest batches — handled with dignity.
    const deceased = !claimed && year <= 1980 && rnd() < 0.22

    const cityPool = rnd() < 0.42 ? CITIES_LOCAL : rnd() < 0.8 ? CITIES_BD : CITIES_ABROAD

    people.push({
      id: `p-${year}-${i}`,
      name: (useprefix ? prefixEn : '') + en,
      nameBn: (useprefix ? prefixBn : '') + bn,
      batchYear: year,
      status: claimed ? 'CLAIMED' : 'SEEDED',
      phone: claimed ? `01${[7, 8, 9, 6, 5][Math.floor(rnd() * 5)]}${Math.floor(rnd() * 90000000 + 10000000)}` : undefined,
      gender: isFemale ? 'FEMALE' : 'MALE',
      occupation: claimed ? occPool[Math.floor(rnd() * occPool.length)] : undefined,
      city: claimed ? cityPool[Math.floor(rnd() * cityPool.length)] : undefined,
      deceased,
    })
  }
  return people
}

/** The signed-in demo user. */
export const DEMO_USER: Person = {
  id: 'p-demo-masum',
  name: 'Md Masum Billah',
  nameBn: 'মোঃ মাসুম বিল্লাহ',
  batchYear: 2010,
  status: 'CLAIMED',
  phone: '01712345678',
  occupation: 'Senior Software Engineer',
  city: 'Dhaka',
}

export const NOTICES = [
  {
    id: 'n1',
    titleEn: 'Reunion date confirmed — 12 February 2027',
    titleBn: 'পুনর্মিলনীর তারিখ চূড়ান্ত — ১২ ফেব্রুয়ারি ২০২৭',
    bodyEn: 'The organizing committee has confirmed Friday, 12 February 2027 at the school campus. Registration is now open.',
    bodyBn: 'আয়োজক কমিটি শুক্রবার, ১২ ফেব্রুয়ারি ২০২৭ তারিখ বিদ্যালয় প্রাঙ্গণে অনুষ্ঠানের সিদ্ধান্ত চূড়ান্ত করেছে। রেজিস্ট্রেশন শুরু হয়েছে।',
    date: '2026-07-14',
    pinned: true,
  },
  {
    id: 'n2',
    titleEn: 'Help us find the 1968–1980 batches',
    titleBn: '১৯৬৮–১৯৮০ ব্যাচের সন্ধান করুন',
    bodyEn: 'Only 1 in 6 of our earliest alumni have been found. If you know anyone from these batches — or their family — please add them.',
    bodyBn: 'আমাদের প্রথম দিকের ব্যাচগুলোর মাত্র ৬ জনে ১ জনকে খুঁজে পাওয়া গেছে। আপনি কাউকে চিনলে অনুগ্রহ করে যুক্ত করুন।',
    date: '2026-07-02',
    pinned: false,
  },
  {
    id: 'n3',
    titleEn: 'How registration is approved',
    titleBn: 'নিবন্ধন কীভাবে অনুমোদন হয়',
    bodyEn:
      'There is no online payment. Send your registration from the app, pay your batch coordinator directly, and they will verify your details and confirm your payment by hand.',
    bodyBn:
      'অনলাইনে কোনো পেমেন্ট নেই। অ্যাপ থেকে নিবন্ধন পাঠান, আপনার ব্যাচ সমন্বয়কারীকে সরাসরি টাকা দিন — তিনি আপনার তথ্য যাচাই করে পেমেন্ট নিশ্চিত করবেন।',
    date: '2026-06-20',
    pinned: false,
  },
]

/**
 * REAL PEOPLE — the school's current teaching staff, unlike everything else in this file.
 * Names and designations only; no contact details, no personal data.
 * Correct any spelling with the school office before you make the demo link public.
 */
export const TEACHERS = [
  { id: 't1', name: 'Nasrin Nahar', nameBn: 'নাসরিন নাহার', roleEn: 'Headmaster', roleBn: 'প্রধান শিক্ষক', memorial: false },
  { id: 't2', name: 'Dipesh Saha', nameBn: 'দীপেশ সাহা', roleEn: 'Assistant Teacher', roleBn: 'সহকারী শিক্ষক', memorial: false },
  { id: 't3', name: 'Shakera Khatun', nameBn: 'শাকেরা খাতুন', roleEn: 'Assistant Teacher', roleBn: 'সহকারী শিক্ষক', memorial: false },
  { id: 't4', name: 'Sanjoy Sarker', nameBn: 'সঞ্জয় সরকার', roleEn: 'Assistant Teacher', roleBn: 'সহকারী শিক্ষক', memorial: false },
  {
    id: 't5',
    name: 'Mst. Shamsun Naher',
    nameBn: 'মোছাঃ শামসুন নাহার',
    roleEn: 'Assistant Teacher (Bangla)',
    roleBn: 'সহকারী শিক্ষক (বাংলা)',
    memorial: false,
  },
]

export const TSHIRT_SIZES = ['S', 'M', 'L', 'XL', '2XL', '3XL']

/* ==================================================================== *
 *  ADMIN PORTAL
 *
 *  Two roles only:
 *    SUPER_ADMIN — sees every batch, and is the only one who can create
 *                  admin accounts or set their passwords.
 *    GROUP_ADMIN — the batch coordinator. Sees only the batch years
 *                  assigned to them, and reviews just those members.
 *
 *  There is no payment gateway anywhere in this product. A member pays
 *  their coordinator directly (bKash / Nagad / bank / cash), the member
 *  reports what they paid, and the coordinator confirms it by hand.
 * ==================================================================== */

export type AdminRole = 'SUPER_ADMIN' | 'GROUP_ADMIN'

export type AdminUser = {
  id: string
  name: string
  nameBn: string
  username: string
  /**
   * Demo only. The real service stores an Argon2id hash server-side and never
   * ships a password to the browser — see docs/00-SYSTEM-DESIGN.md §6.
   */
  password: string
  phone: string
  role: AdminRole
  /** Batch years this admin may review. Ignored (and empty) for a super admin. */
  batches: number[]
  active: boolean
  createdAt: string
  createdByAdminId?: string
}

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type PaymentStatus = 'UNPAID' | 'REPORTED' | 'CONFIRMED' | 'REJECTED'
export type PaymentMethod = 'BKASH' | 'NAGAD' | 'ROCKET' | 'BANK' | 'CASH'

export const PAYMENT_METHODS: PaymentMethod[] = ['BKASH', 'NAGAD', 'ROCKET', 'BANK', 'CASH']

export type Review = {
  adminId: string
  adminName: string
  at: string
  note?: string
}

export type PaymentReport = {
  method: PaymentMethod
  /** Transaction id, bank slip number, or whatever the coordinator can match against. */
  reference: string
  amount: number
  paidToAdminId?: string
  reportedAt: string
}

/** One member's submission — identity claim and payment travel together. */
export type Application = {
  id: string
  personId: string
  name: string
  nameBn: string
  batchYear: number
  phone: string
  email?: string
  gender?: Gender
  dob?: string
  bloodGroup?: BloodGroup
  occupation?: string
  city?: string
  guests: Guest[]
  amountDue: number
  submittedAt: string
  memberNote?: string
  memberStatus: ReviewStatus
  memberReview?: Review
  paymentStatus: PaymentStatus
  payment?: PaymentReport
  paymentReview?: Review
}

function yearRange(from: number, to: number): number[] {
  const out: number[] = []
  for (let y = from; y <= to; y++) out.push(y)
  return out
}

/**
 * Seeded admin accounts. The credentials are printed on the admin login screen —
 * this is a demo build and there is nothing real behind them.
 */
export const ADMIN_USERS: AdminUser[] = [
  {
    id: 'adm-super',
    name: 'Convener — Reunion Committee',
    nameBn: 'আহ্বায়ক — পুনর্মিলনী কমিটি',
    username: 'superadmin',
    password: 'Sammalani@2027',
    phone: '01712345600',
    role: 'SUPER_ADMIN',
    batches: [],
    active: true,
    createdAt: '2026-05-01T10:00:00+06:00',
  },
  {
    id: 'adm-early',
    name: 'Md. Rafiqul Islam',
    nameBn: 'মোঃ রফিকুল ইসলাম',
    username: 'coord.early',
    password: 'Narail@1968',
    phone: '01712345601',
    role: 'GROUP_ADMIN',
    batches: yearRange(1968, 1985),
    active: true,
    createdAt: '2026-05-04T10:00:00+06:00',
    createdByAdminId: 'adm-super',
  },
  {
    id: 'adm-mid',
    name: 'Mst. Nasrin Sultana',
    nameBn: 'মোছাঃ নাসরিন সুলতানা',
    username: 'coord.mid',
    password: 'Narail@1986',
    phone: '01712345602',
    role: 'GROUP_ADMIN',
    batches: yearRange(1986, 2005),
    active: true,
    createdAt: '2026-05-04T10:05:00+06:00',
    createdByAdminId: 'adm-super',
  },
  {
    id: 'adm-late',
    name: 'Md. Tanvir Ahmed',
    nameBn: 'মোঃ তানভীর আহমেদ',
    username: 'coord.late',
    password: 'Narail@2006',
    phone: '01712345603',
    role: 'GROUP_ADMIN',
    batches: yearRange(2006, 2026),
    active: true,
    createdAt: '2026-05-04T10:10:00+06:00',
    createdByAdminId: 'adm-super',
  },
]

/** Takes only role + batches, so it works on a password-stripped account too. */
type AdminScope = Pick<AdminUser, 'role' | 'batches'>

export function adminCoversBatch(admin: AdminScope, year: number): boolean {
  return admin.role === 'SUPER_ADMIN' || admin.batches.includes(year)
}

/** The span an admin is responsible for — null means every batch. */
export function adminScopeYears(admin: AdminScope): { from: number; to: number } | null {
  if (admin.role === 'SUPER_ADMIN' || admin.batches.length === 0) return null
  const sorted = [...admin.batches].sort((a, b) => a - b)
  return { from: sorted[0], to: sorted[sorted.length - 1] }
}

const GUEST_NAMES = ['Rina Akter', 'Sumon Ahmed', 'Tanha Islam', 'Arif Hasan', 'Nadia Sultana', 'Rifat Karim']

/**
 * A realistic review queue, generated from the same seed as the roster so the
 * numbers never shift between reloads or machines.
 */
export function seedApplications(): Application[] {
  const years = [1971, 1976, 1982, 1987, 1991, 1996, 2001, 2005, 2009, 2013, 2017, 2020, 2023, 2026]
  const out: Application[] = []

  years.forEach((year, yi) => {
    const rnd = mulberry32(year * 31337)
    const roster = membersOfBatch(year).filter((p) => !p.deceased)
    const count = 1 + Math.floor(rnd() * 2)

    for (let i = 0; i < count; i++) {
      const person = roster[Math.floor(rnd() * roster.length)]
      if (!person) continue

      const guestCount = Math.floor(rnd() * 4)
      const guests: Guest[] = []
      for (let g = 0; g < guestCount; g++) {
        const isChild = g > 0
        const age = isChild ? 3 + Math.floor(rnd() * 12) : undefined
        guests.push({
          id: `sg-${year}-${i}-${g}`,
          name: GUEST_NAMES[Math.floor(rnd() * GUEST_NAMES.length)],
          relation: isChild ? 'CHILD' : 'SPOUSE',
          age,
          ticketTypeId: isChild ? (age! < 5 ? 'tt-child-free' : age! <= 12 ? 'tt-child' : 'tt-guest') : 'tt-spouse',
          tshirtSize: isChild ? undefined : TSHIRT_SIZES[Math.floor(rnd() * TSHIRT_SIZES.length)],
        })
      }

      const amountDue =
        (TICKET_TYPES.find((tt) => tt.id === 'tt-alumni')?.amount ?? 0) +
        guests.reduce((s, g) => s + (TICKET_TYPES.find((tt) => tt.id === g.ticketTypeId)?.amount ?? 0), 0)

      // Most of the queue is waiting — that is the whole point of the screen.
      const roll = rnd()
      const memberStatus: ReviewStatus = roll < 0.66 ? 'PENDING' : roll < 0.9 ? 'APPROVED' : 'REJECTED'

      const payRoll = rnd()
      let paymentStatus: PaymentStatus = 'UNPAID'
      if (memberStatus === 'REJECTED') paymentStatus = 'UNPAID'
      else if (payRoll < 0.45) paymentStatus = 'REPORTED'
      else if (payRoll < 0.72) paymentStatus = memberStatus === 'APPROVED' ? 'CONFIRMED' : 'REPORTED'

      const day = String(6 + ((yi * 3 + i) % 22)).padStart(2, '0')
      const submittedAt = `2026-07-${day}T${String(9 + (i % 9)).padStart(2, '0')}:20:00+06:00`

      const coordinator = ADMIN_USERS.find((a) => a.role === 'GROUP_ADMIN' && a.batches.includes(year))

      out.push({
        id: `app-${year}-${i}`,
        personId: person.id,
        name: person.name,
        nameBn: person.nameBn,
        batchYear: year,
        phone: person.phone ?? `01${[7, 8, 9][Math.floor(rnd() * 3)]}${Math.floor(rnd() * 90000000 + 10000000)}`,
        email: rnd() < 0.4 ? `${person.name.toLowerCase().replace(/[^a-z]+/g, '.')}@example.com` : undefined,
        gender: person.gender,
        bloodGroup: rnd() < 0.5 ? BLOOD_GROUPS[Math.floor(rnd() * BLOOD_GROUPS.length)] : undefined,
        occupation: person.occupation,
        city: person.city,
        guests,
        amountDue,
        submittedAt,
        memberNote:
          rnd() < 0.25
            ? 'My name is spelled differently on the old register. My father was a teacher here.'
            : undefined,
        memberStatus,
        memberReview:
          memberStatus === 'PENDING'
            ? undefined
            : {
                adminId: coordinator?.id ?? 'adm-super',
                adminName: coordinator?.name ?? 'Convener — Reunion Committee',
                at: `2026-07-${day}T18:00:00+06:00`,
                note: memberStatus === 'REJECTED' ? 'Could not confirm this person against the batch register.' : undefined,
              },
        paymentStatus,
        payment:
          paymentStatus === 'UNPAID'
            ? undefined
            : {
                method: PAYMENT_METHODS[Math.floor(rnd() * PAYMENT_METHODS.length)],
                reference: `TRX${Math.floor(rnd() * 900000 + 100000)}`,
                amount: amountDue,
                paidToAdminId: coordinator?.id,
                reportedAt: submittedAt,
              },
        paymentReview:
          paymentStatus === 'CONFIRMED'
            ? {
                adminId: coordinator?.id ?? 'adm-super',
                adminName: coordinator?.name ?? 'Convener — Reunion Committee',
                at: `2026-07-${day}T19:00:00+06:00`,
              }
            : undefined,
      })
    }
  })

  return out.sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
}
