import { Link } from 'react-router-dom'
import { ArrowLeft, Phone } from 'lucide-react'
import { useApp } from '../lib/store'
import { CONTACT_PHONE } from '../lib/api'
import { SCHOOL } from '../mock/data'
import { cx } from '../components/ui'
import { LangToggle, SchoolMark } from '../components/Layout'

/**
 * The privacy policy, at a public URL because Google Play requires one and will
 * not review an app without it.
 *
 * The text is held here rather than in the `DICT` in lib/store, which is keyed
 * UI chrome and would be a poor home for several hundred words of prose in two
 * languages. Every claim below was checked against the code that implements it:
 * the masking is `PhoneNumbers.mask`, the visibility split is `PersonDto.masked`
 * versus `PersonDto.from`, and what deletion does is `AccountDeletionService`.
 * If any of those change, this page is wrong until it is changed too.
 *
 * Two things it deliberately does NOT claim, because they are not true today:
 * that an SMS gateway sends the one-time code (`OtpService` logs it — the
 * gateway is a TODO), and that admin reads of contact data are logged (the
 * audit trail records changes; `AuditAction` has no READ).
 */

const UPDATED = { en: '4 September 2026', bn: '৪ সেপ্টেম্বর ২০২৬' }

type Section = { h: string; p?: string; list?: string[] }

const EN: Section[] = [
  {
    h: 'Who runs this',
    p: `The ${SCHOOL.nameEn} alumni platform is run by the reunion organising committee of the school in ${SCHOOL.locationEn}, Bangladesh. It exists to help alumni of batches ${SCHOOL.firstBatch}–${SCHOOL.lastBatch} find each other and register for the reunion. It is not a commercial service.`,
  },
  {
    h: 'What we collect',
    p: 'Two kinds of information, and they arrive differently.',
    list: [
      'The roster. Your name and SSC passing year were typed in from the school’s own registers by volunteers, before you ever used this app. Most people on the roster have never signed in.',
      'What you give us. Your mobile number, which is required because it is how you sign in. Optionally: email address, date of birth, gender, blood group, occupation, city and a profile photo.',
      'Your reunion registration, if you make one: the names, relationships and ages of guests you bring, T-shirt size, food preference and any note you write to your batch coordinator.',
      'Payment reports you submit: the method, the transaction reference and the amount. We never see or store your bKash, Nagad or bank credentials — you pay your coordinator directly and then tell us the reference number.',
    ],
  },
  {
    h: 'What we do not collect',
    p: 'The app contains no analytics, advertising or tracking software of any kind. We do not track your location, read your contacts, or collect anything about how you use the app. Nothing is sold or shared for advertising, ever.',
  },
  {
    h: 'Who can see your details',
    list: [
      'Other signed-in alumni browsing your batch see your name, batch year, occupation, city and photo, and your mobile number with the middle digits hidden (for example 017*****123). They never see your email address, date of birth, gender or blood group.',
      'Committee admins see the full record for the batches they are responsible for, because approving a registration and confirming a payment cannot be done blind.',
      'Nobody who is not signed in sees any of it. Batch pages show counts only.',
      'Changes to records are written to an append-only log recording who made them and when.',
    ],
  },
  {
    h: 'Your photo and your camera',
    p: 'The Android app asks for camera permission only at the moment you choose to take a profile photo, and only for that. You can pick an existing picture instead, or skip the photo entirely. Photos are stored on the committee’s own server, not with a third party, and deleting your photo removes the file.',
  },
  {
    h: 'How your data is protected',
    p: 'All traffic between the app and the server travels over HTTPS with a valid certificate. You sign in with a one-time code rather than a password, so there is no password of yours to leak. Admin passwords are stored as Argon2id hashes. Access is limited to committee admins, and group admins can only reach the batches assigned to them.',
  },
  {
    h: 'Deleting your account',
    p: 'You can delete your account yourself, at any time, from Profile → Delete account in the app, or on this website at /account-deletion. No email, no waiting for someone to action it.',
    list: [
      'Erased immediately: your mobile number, email, date of birth, gender, blood group, occupation, city, profile photo (the file itself, not just the link) and any additional details held on your record. Your registration is cancelled and your guest list is deleted.',
      'Kept: your name and batch year, on a hidden record that no query in the application can return. They came from the school register rather than from you, and keeping them is what lets an admin restore you if you come back.',
      'Kept: payment records — the amount, method and transaction reference. The committee has to be able to account for money it received, and a payment that vanishes is an accusation it cannot answer. These records no longer identify you once your account is gone.',
    ],
  },
  {
    h: 'If you have paid for a ticket',
    p: 'Deleting your account cancels your registration and forfeits the ticket. Because deletion also removes your mobile number, nobody will be able to call you about it — so write down your batch coordinator’s number before you delete, and quote your transaction reference when you call them. The app shows you that number on the confirmation screen.',
  },
  {
    h: 'How long we keep things',
    p: 'Your record is kept while your account exists and for as long as the reunion project runs. Payment records are kept for the committee’s accounts. Everything else goes when you delete your account.',
  },
  {
    h: 'Children',
    p: 'This platform is intended for alumni aged 18 and over. If you are under 18 and your name appears on the roster, ask a parent or guardian to contact the committee on the number below and we will remove it.',
  },
  {
    h: 'Changes to this policy',
    p: 'If what we collect or how we use it changes, this page changes with it, and the date at the top moves.',
  },
  {
    h: 'Contact',
    p: `Questions about your data, or a request to have something removed: call or message the reunion committee on ${CONTACT_PHONE}.`,
  },
]

const BN: Section[] = [
  {
    h: 'কারা পরিচালনা করে',
    p: `${SCHOOL.nameBn} প্রাক্তন শিক্ষার্থী প্ল্যাটফর্মটি পরিচালনা করে ${SCHOOL.locationBn}-এ অবস্থিত বিদ্যালয়ের পুনর্মিলনী আয়োজক কমিটি। ${SCHOOL.firstBatch}–${SCHOOL.lastBatch} ব্যাচের প্রাক্তন শিক্ষার্থীরা যাতে একে অপরকে খুঁজে পান এবং পুনর্মিলনীতে নিবন্ধন করতে পারেন, সেজন্যই এটি। এটি কোনো বাণিজ্যিক সেবা নয়।`,
  },
  {
    h: 'আমরা কী সংগ্রহ করি',
    p: 'দুই ধরনের তথ্য, এবং সেগুলো আসে ভিন্ন ভিন্ন উপায়ে।',
    list: [
      'তালিকা। আপনার নাম ও এসএসসি পাসের বছর স্বেচ্ছাসেবকরা বিদ্যালয়ের নিজস্ব রেজিস্টার থেকে তুলেছেন — আপনি এই অ্যাপ ব্যবহার করার অনেক আগেই। তালিকার বেশিরভাগ মানুষ কখনো লগইনই করেননি।',
      'আপনি যা দেন। আপনার মোবাইল নম্বর, যা আবশ্যক — কারণ এটি দিয়েই আপনি লগইন করেন। ঐচ্ছিকভাবে: ইমেইল, জন্ম তারিখ, লিঙ্গ, রক্তের গ্রুপ, পেশা, শহর এবং একটি প্রোফাইল ছবি।',
      'আপনার পুনর্মিলনী নিবন্ধন, যদি করেন: সঙ্গে আসা অতিথিদের নাম, সম্পর্ক ও বয়স, টি-শার্টের মাপ, খাবারের পছন্দ এবং সমন্বয়কারীকে লেখা যেকোনো নোট।',
      'আপনার পাঠানো পেমেন্ট রিপোর্ট: মাধ্যম, লেনদেন রেফারেন্স ও পরিমাণ। আপনার বিকাশ, নগদ বা ব্যাংকের গোপন তথ্য আমরা কখনো দেখি না বা সংরক্ষণ করি না — আপনি সরাসরি সমন্বয়কারীকে টাকা দেন, তারপর শুধু রেফারেন্স নম্বরটি আমাদের জানান।',
    ],
  },
  {
    h: 'আমরা যা সংগ্রহ করি না',
    p: 'এই অ্যাপে কোনো ধরনের অ্যানালিটিক্স, বিজ্ঞাপন বা ট্র্যাকিং সফটওয়্যার নেই। আমরা আপনার অবস্থান ট্র্যাক করি না, আপনার কন্ট্যাক্ট পড়ি না, এবং আপনি অ্যাপটি কীভাবে ব্যবহার করেন তার কোনো তথ্য সংগ্রহ করি না। কোনো তথ্য কখনোই বিক্রি বা বিজ্ঞাপনের জন্য শেয়ার করা হয় না।',
  },
  {
    h: 'কারা আপনার তথ্য দেখতে পান',
    list: [
      'লগইন করা অন্য প্রাক্তন শিক্ষার্থীরা আপনার ব্যাচ দেখলে আপনার নাম, ব্যাচ, পেশা, শহর ও ছবি দেখতে পান, এবং মোবাইল নম্বরের মাঝের অঙ্কগুলো ঢাকা অবস্থায় (যেমন ০১৭*****১২৩)। আপনার ইমেইল, জন্ম তারিখ, লিঙ্গ বা রক্তের গ্রুপ তাঁরা কখনো দেখেন না।',
      'কমিটির অ্যাডমিনরা তাঁদের দায়িত্বে থাকা ব্যাচের পূর্ণ তথ্য দেখেন — না দেখে নিবন্ধন অনুমোদন বা পেমেন্ট নিশ্চিত করা সম্ভব নয়।',
      'যিনি লগইন করেননি, তিনি কিছুই দেখেন না। ব্যাচের পাতায় শুধু সংখ্যা দেখা যায়।',
      'তথ্যে কোনো পরিবর্তন হলে কে এবং কখন করেছেন তা একটি স্থায়ী লগে লেখা থাকে।',
    ],
  },
  {
    h: 'আপনার ছবি ও ক্যামেরা',
    p: 'অ্যান্ড্রয়েড অ্যাপটি ক্যামেরার অনুমতি চায় কেবল যখন আপনি নিজে প্রোফাইল ছবি তুলতে চান, এবং শুধু সেই কাজেই। আপনি চাইলে আগের কোনো ছবি বেছে নিতে পারেন, বা ছবি না দিয়েও চালিয়ে যেতে পারেন। ছবি কমিটির নিজস্ব সার্ভারে রাখা হয়, কোনো তৃতীয় পক্ষের কাছে নয়, এবং ছবি মুছে ফেললে ফাইলটিও মুছে যায়।',
  },
  {
    h: 'আপনার তথ্য কীভাবে সুরক্ষিত',
    p: 'অ্যাপ ও সার্ভারের মধ্যে সব যোগাযোগ বৈধ সার্টিফিকেটসহ HTTPS-এ হয়। আপনি পাসওয়ার্ড নয়, একবার ব্যবহারযোগ্য কোড দিয়ে লগইন করেন — তাই ফাঁস হওয়ার মতো কোনো পাসওয়ার্ডই নেই। অ্যাডমিন পাসওয়ার্ড Argon2id হ্যাশ হিসেবে সংরক্ষিত। প্রবেশাধিকার কেবল কমিটির অ্যাডমিনদের, এবং গ্রুপ অ্যাডমিনরা কেবল তাঁদের নির্ধারিত ব্যাচেই পৌঁছাতে পারেন।',
  },
  {
    h: 'অ্যাকাউন্ট মুছে ফেলা',
    p: 'আপনি নিজেই যেকোনো সময় অ্যাকাউন্ট মুছে ফেলতে পারেন — অ্যাপে প্রোফাইল → অ্যাকাউন্ট মুছুন থেকে, অথবা এই ওয়েবসাইটে /account-deletion ঠিকানায়। ইমেইল করার বা কারো অপেক্ষায় থাকার দরকার নেই।',
    list: [
      'সঙ্গে সঙ্গে মুছে যায়: আপনার মোবাইল নম্বর, ইমেইল, জন্ম তারিখ, লিঙ্গ, রক্তের গ্রুপ, পেশা, শহর, প্রোফাইল ছবি (শুধু লিঙ্ক নয়, ফাইলটিও) এবং আপনার রেকর্ডে থাকা অন্যান্য তথ্য। আপনার নিবন্ধন বাতিল হয় এবং অতিথির তালিকা মুছে যায়।',
      'থেকে যায়: আপনার নাম ও ব্যাচ, একটি লুকানো রেকর্ডে — যা অ্যাপ্লিকেশনের কোনো অনুসন্ধানেই আর আসে না। এগুলো আপনার দেওয়া নয়, বিদ্যালয়ের রেজিস্টার থেকে নেওয়া, এবং আপনি ফিরে এলে অ্যাডমিন যাতে আপনাকে ফিরিয়ে আনতে পারেন সেজন্যই রাখা।',
      'থেকে যায়: পেমেন্টের রেকর্ড — পরিমাণ, মাধ্যম ও লেনদেন রেফারেন্স। কমিটিকে প্রাপ্ত অর্থের হিসাব দিতে হয়, আর হারিয়ে যাওয়া পেমেন্টের জবাব তারা দিতে পারবে না। অ্যাকাউন্ট মুছে গেলে এই রেকর্ড আর আপনাকে শনাক্ত করে না।',
    ],
  },
  {
    h: 'টিকিটের টাকা দিয়ে থাকলে',
    p: 'অ্যাকাউন্ট মুছলে আপনার নিবন্ধন বাতিল হবে এবং টিকিটটি আর থাকবে না। যেহেতু মুছে ফেলার সঙ্গে আপনার মোবাইল নম্বরও চলে যায়, কেউ আপনাকে এ বিষয়ে ফোন করতে পারবে না — তাই মুছে ফেলার আগে আপনার ব্যাচ সমন্বয়কারীর নম্বরটি লিখে রাখুন এবং ফোন করার সময় লেনদেন রেফারেন্সটি বলুন। নিশ্চিতকরণের পর্দায় অ্যাপ আপনাকে সেই নম্বরটি দেখাবে।',
  },
  {
    h: 'কতদিন রাখা হয়',
    p: 'আপনার অ্যাকাউন্ট যতদিন আছে এবং পুনর্মিলনী প্রকল্প যতদিন চলে, ততদিন আপনার রেকর্ড রাখা হয়। পেমেন্টের রেকর্ড কমিটির হিসাবের জন্য রাখা হয়। বাকি সবকিছু অ্যাকাউন্ট মুছে ফেলার সঙ্গেই চলে যায়।',
  },
  {
    h: 'শিশু-কিশোর',
    p: 'এই প্ল্যাটফর্মটি ১৮ বছর ও তদূর্ধ্ব প্রাক্তন শিক্ষার্থীদের জন্য। আপনার বয়স ১৮-র কম এবং তালিকায় আপনার নাম থাকলে, অভিভাবককে নিচের নম্বরে যোগাযোগ করতে বলুন — আমরা নামটি সরিয়ে দেব।',
  },
  {
    h: 'নীতিমালার পরিবর্তন',
    p: 'আমরা কী সংগ্রহ করি বা কীভাবে ব্যবহার করি তা বদলালে এই পাতাটিও বদলাবে, এবং উপরের তারিখটি এগিয়ে যাবে।',
  },
  {
    h: 'যোগাযোগ',
    p: `আপনার তথ্য নিয়ে প্রশ্ন, বা কিছু সরিয়ে ফেলার অনুরোধ: পুনর্মিলনী কমিটিকে ${CONTACT_PHONE} নম্বরে ফোন বা মেসেজ করুন।`,
  },
]

export default function Privacy() {
  const { lang } = useApp()
  const bn = lang === 'bn'
  const sections = bn ? BN : EN

  return (
    <div className={cx('flex min-h-dvh flex-col bg-paper', bn && 'font-bn')}>
      <header className="sticky top-0 z-30 border-b border-paper-2 bg-paper/90 backdrop-blur">
        <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-3">
          <Link to="/" className="grid size-10 shrink-0 place-items-center rounded-xl hover:bg-paper-2">
            <ArrowLeft className="size-6 text-ink-600" />
          </Link>
          <div className="min-w-0 flex-1">
            <SchoolMark />
          </div>
          <LangToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">
        <h1 className="text-3xl font-extrabold text-ink-900">
          {bn ? 'গোপনীয়তা নীতিমালা' : 'Privacy Policy'}
        </h1>
        <p className="mt-1.5 text-sm text-ink-400">
          {bn ? 'সর্বশেষ হালনাগাদ' : 'Last updated'}: {bn ? UPDATED.bn : UPDATED.en}
        </p>

        <div className="mt-8 space-y-8">
          {sections.map((section) => (
            <section key={section.h}>
              <h2 className="text-lg font-bold text-ink-900">{section.h}</h2>
              {section.p && <p className="mt-2 leading-relaxed text-ink-700">{section.p}</p>}
              {section.list && (
                <ul className="mt-3 space-y-2.5">
                  {section.list.map((item) => (
                    <li key={item} className="flex gap-2.5 leading-relaxed text-ink-700">
                      <span aria-hidden className="mt-2 size-1.5 shrink-0 rounded-full bg-brand-300" />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ))}
        </div>

        <div className="mt-10 flex flex-col items-start gap-3 rounded-2xl border border-paper-2 bg-white p-5">
          <p className="font-semibold text-ink-900">
            {bn ? 'আপনার অ্যাকাউন্ট মুছে ফেলতে চান?' : 'Want to delete your account?'}
          </p>
          <Link
            to="/account-deletion"
            className="rounded-xl border-2 border-red-200 px-4 py-2.5 font-semibold text-red-600 hover:bg-red-50"
          >
            {bn ? 'অ্যাকাউন্ট মুছে ফেলুন' : 'Delete your account'}
          </Link>
        </div>
      </main>

      <footer className="border-t border-paper-2 bg-paper-2/60">
        <div className="mx-auto max-w-3xl px-4 py-6 text-center">
          <a
            href={`tel:+880${CONTACT_PHONE.replace(/^0/, '')}`}
            className="inline-flex items-center gap-2 font-semibold text-brand-700"
          >
            <Phone className="size-5" />
            {CONTACT_PHONE}
          </a>
        </div>
      </footer>
    </div>
  )
}
