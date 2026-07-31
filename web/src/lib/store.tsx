import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, type Person } from './api'

/* ==================================================================== *
 *  i18n — Bangla first, English second. Elders read Bangla.
 * ==================================================================== */

export type Lang = 'bn' | 'en'

const DICT = {
  bn: {
    'app.name': 'সম্মিলনী প্রাক্তন শিক্ষার্থী',
    'app.tagline': 'এক বিদ্যালয়, এক পরিবার',
    'nav.home': 'হোম',
    'nav.dashboard': 'ড্যাশবোর্ড',
    'nav.batches': 'ব্যাচসমূহ',
    'nav.guests': 'পরিবার',
    'nav.profile': 'প্রোফাইল',
    'nav.login': 'লগইন',
    'nav.logout': 'লগআউট',
    'nav.signup': 'নিবন্ধন করুন',

    'cta.findMe': 'আমার নাম খুঁজুন',
    'cta.login': 'লগইন করুন',
    'cta.continue': 'পরবর্তী',
    'cta.back': 'পিছনে',
    'cta.save': 'সংরক্ষণ করুন',
    'cta.cancel': 'বাতিল',
    'cta.add': 'যুক্ত করুন',
    'cta.remove': 'সরান',
    'cta.submit': 'অনুমোদনের জন্য পাঠান',
    'cta.viewAll': 'সব দেখুন',
    'cta.loadMore': 'আরও দেখুন',
    'cta.approve': 'অনুমোদন',
    'cta.reject': 'বাতিল',
    'cta.confirm': 'নিশ্চিত করুন',
    'cta.edit': 'সম্পাদনা',
    'cta.close': 'বন্ধ করুন',

    'landing.est': 'প্রতিষ্ঠিত ১৯৬৮',
    'landing.heroTitle': 'মহা পুনর্মিলনী ২০২৭',
    'landing.heroSub': 'ব্যাচ ১৯৬৮ থেকে ২০২৬ — সবাই আমন্ত্রিত',
    'landing.countdown': 'বাকি আছে',
    'landing.days': 'দিন',
    'landing.hours': 'ঘণ্টা',
    'landing.mins': 'মিনিট',
    'landing.secs': 'সেকেন্ড',
    'landing.foundSoFar': 'এ পর্যন্ত খুঁজে পাওয়া গেছে',
    'landing.ofTotal': 'জন প্রাক্তন শিক্ষার্থীর মধ্যে',
    'landing.batchCoverage': 'ব্যাচভিত্তিক অগ্রগতি',
    'landing.coverageNote': 'গাঢ় রঙ = বেশি শিক্ষার্থী খুঁজে পাওয়া গেছে। পুরনো ব্যাচগুলোতে আপনার সাহায্য দরকার।',
    'landing.registered': 'নিবন্ধিত',
    'landing.teachers': 'শিক্ষক',
    'landing.batches': 'ব্যাচ',

    'auth.loginTitle': 'লগইন করুন',
    'auth.loginSub': 'শুধু মোবাইল নম্বর দিন — কোনো পাসওয়ার্ড লাগবে না',
    'auth.phone': 'মোবাইল নম্বর',
    'auth.phoneHelp': 'উদাহরণ: ০১৭১২৩৪৫৬৭৮',
    'auth.sendCode': 'কোড পাঠান',
    'auth.otpTitle': 'কোডটি লিখুন',
    'auth.otpSub': 'আপনার মোবাইলে ৬ সংখ্যার কোড পাঠানো হয়েছে',
    'auth.verify': 'যাচাই করুন',
    'auth.resend': 'আবার কোড পাঠান',
    'auth.noAccount': 'প্রথমবার আসছেন?',
    'auth.needHelp': 'সাহায্য দরকার? কল করুন',

    'signup.title': 'আপনার নাম খুঁজে নিন',
    'signup.sub': 'আমাদের কাছে বিদ্যালয়ের পুরনো রেজিস্টার আছে। নতুন করে ফর্ম পূরণ করতে হবে না।',
    'signup.step1': 'আপনি কোন সালে এসএসসি পাস করেছেন?',
    'signup.step2': 'নিচের তালিকায় আপনার নাম আছে কি?',
    'signup.step3': 'মোবাইল নম্বর দিয়ে নিশ্চিত করুন',
    'signup.yearPlaceholder': 'যেমন: ১৯৭৪',
    'signup.searchName': 'নাম দিয়ে খুঁজুন',
    'signup.notFound': 'তালিকায় আমার নাম নেই',
    'signup.thisIsMe': 'এটাই আমি',
    'signup.foundCount': 'জন খুঁজে পাওয়া গেছে',
    'signup.newName': 'আপনার পুরো নাম',
    'signup.welcome': 'স্বাগতম!',
    'signup.welcomeSub': 'আপনার প্রোফাইল তৈরি হয়েছে।',

    'dash.greeting': 'আসসালামু আলাইকুম',
    'dash.yourBatch': 'আপনার ব্যাচ',
    'dash.eventCard': 'পুনর্মিলনী ২০২৭',
    'dash.notRegistered': 'আপনি এখনো নিবন্ধন করেননি',
    'dash.registerNow': 'এখনই নিবন্ধন করুন',
    'dash.registered': 'অনুমোদিত',
    'dash.awaitingReview': 'যাচাইয়ের অপেক্ষায়',
    'dash.rejected': 'বাতিল হয়েছে',
    'dash.draft': 'খসড়া — এখনো পাঠানো হয়নি',
    'dash.totalDue': 'মোট প্রদেয়',
    'dash.attending': 'আপনার সাথে যাচ্ছেন',
    'dash.people': 'জন',
    'dash.profileComplete': 'প্রোফাইল সম্পূর্ণতা',
    'dash.missingTitle': 'এদের খুঁজে পাওয়া যাচ্ছে না',
    'dash.missingSub': 'আপনি কি এদের কাউকে চেনেন? নম্বর দিয়ে সাহায্য করুন।',
    'dash.iKnowThem': 'আমি চিনি',
    'dash.notices': 'নোটিশ বোর্ড',
    'dash.quickAdd': 'বন্ধুকে যুক্ত করুন',

    'guests.title': 'পরিবারের সদস্য যুক্ত করুন',
    'guests.sub': 'স্ত্রী/স্বামী, সন্তান বা পরিবারের অন্য সদস্যদের নিয়ে আসতে পারেন',
    'guests.addMember': 'সদস্য যুক্ত করুন',
    'guests.name': 'নাম',
    'guests.relation': 'সম্পর্ক',
    'guests.age': 'বয়স',
    'guests.food': 'খাবারের পছন্দ',
    'guests.tshirt': 'টি-শার্ট মাপ',
    'guests.none': 'এখনো কাউকে যুক্ত করা হয়নি',
    'guests.yourTicket': 'আপনার টিকিট',
    'guests.summary': 'খরচের হিসাব',
    'guests.total': 'সর্বমোট',
    'guests.free': 'বিনামূল্যে',
    'guests.rel.SPOUSE': 'স্ত্রী / স্বামী',
    'guests.rel.CHILD': 'সন্তান',
    'guests.rel.PARENT': 'পিতা / মাতা',
    'guests.rel.SIBLING': 'ভাই / বোন',
    'guests.rel.OTHER': 'অন্যান্য',
    'guests.food.REGULAR': 'সাধারণ',
    'guests.food.NO_BEEF': 'গরুর মাংস ছাড়া',
    'guests.food.VEG': 'নিরামিষ',
    'guests.submitted': 'অনুমোদনের জন্য পাঠানো হয়েছে',
    'guests.submittedSub': 'আপনার ব্যাচ সমন্বয়কারী তথ্য যাচাই করে অনুমোদন দেবেন।',
    'guests.approved': 'নিবন্ধন অনুমোদিত হয়েছে',
    'guests.approvedSub': 'আপনার আসন নিশ্চিত হয়েছে। অনুষ্ঠানের দিন এই পৃষ্ঠাটি দেখাবেন।',
    'guests.rejected': 'নিবন্ধন অনুমোদিত হয়নি',
    'guests.howToPay': 'কীভাবে টাকা দেবেন',
    'guests.howToPaySub': 'অ্যাপে কোনো অনলাইন পেমেন্ট নেই। নিচের সমন্বয়কারীকে সরাসরি টাকা দিন।',
    'guests.coordinator': 'আপনার ব্যাচ সমন্বয়কারী',
    'guests.reportPayment': 'টাকা দিয়েছি — তথ্য জানান',
    'guests.payMethod': 'কীভাবে দিয়েছেন',
    'guests.payRef': 'লেনদেন / স্লিপ নম্বর',
    'guests.payAmount': 'পরিমাণ',
    'guests.payReported': 'পেমেন্টের তথ্য জমা হয়েছে — যাচাইয়ের অপেক্ষায়',
    'guests.payConfirmed': 'পেমেন্ট নিশ্চিত হয়েছে',
    'guests.payRejected': 'পেমেন্ট নিশ্চিত করা যায়নি',
    'guests.note': 'সমন্বয়কারীর জন্য কিছু লিখতে চান? (ঐচ্ছিক)',
    'pay.BKASH': 'বিকাশ',
    'pay.NAGAD': 'নগদ',
    'pay.ROCKET': 'রকেট',
    'pay.BANK': 'ব্যাংক',
    'pay.CASH': 'নগদ টাকা',
    'pay.OTHER': 'অন্যান্য',

    'batches.title': 'সকল ব্যাচ',
    'batches.sub': '১৯৬৮ থেকে ২০২৬ — মোট ৫৯টি ব্যাচ',
    'batches.found': 'পাওয়া গেছে',
    'batches.missing': 'বাকি',
    'batches.members': 'জন শিক্ষার্থী',
    'batches.deceased': 'মরহুম',

    'profile.title': 'আমার প্রোফাইল',
    'profile.name': 'পুরো নাম',
    'profile.batch': 'ব্যাচ (এসএসসি সাল)',
    'profile.occupation': 'পেশা',
    'profile.city': 'বর্তমান ঠিকানা',
    'profile.email': 'ইমেইল',
    'profile.gender': 'লিঙ্গ',
    'profile.dob': 'জন্ম তারিখ',
    'profile.blood': 'রক্তের গ্রুপ',
    'profile.optional': 'ঐচ্ছিক',
    'profile.moreDetails': 'অতিরিক্ত তথ্য (ঐচ্ছিক)',
    'profile.moreDetailsSub': 'এগুলো না দিলেও চলবে। রক্তের গ্রুপ জরুরি প্রয়োজনে কাজে লাগে।',
    'profile.saved': 'সংরক্ষিত হয়েছে',
    'gender.MALE': 'পুরুষ',
    'gender.FEMALE': 'নারী',
    'gender.OTHER': 'অন্যান্য',
    'common.notSet': 'দেওয়া হয়নি',

    /* ---- admin portal ---- */
    'admin.portal': 'অ্যাডমিন পোর্টাল',
    'admin.login': 'অ্যাডমিন লগইন',
    'admin.loginSub': 'কমিটির সদস্যদের জন্য — ব্যবহারকারীর নাম ও পাসওয়ার্ড দিন',
    'admin.username': 'ব্যবহারকারীর নাম',
    'admin.password': 'পাসওয়ার্ড',
    'admin.signIn': 'প্রবেশ করুন',
    'admin.signOut': 'বের হন',
    'admin.backToSite': 'সাইটে ফিরে যান',
    'admin.overview': 'সারসংক্ষেপ',
    'admin.members': 'সদস্য যাচাই',
    'admin.payments': 'পেমেন্ট',
    'admin.accounts': 'অ্যাডমিন অ্যাকাউন্ট',
    'admin.roleSUPER_ADMIN': 'সুপার অ্যাডমিন',
    'admin.roleGROUP_ADMIN': 'গ্রুপ অ্যাডমিন',
    'admin.scopeAll': 'সব ব্যাচ',
    'admin.scopeBatches': 'ব্যাচ',
    'admin.pendingMembers': 'যাচাইয়ের অপেক্ষায়',
    'admin.pendingPayments': 'পেমেন্ট যাচাই বাকি',
    'admin.approvedMembers': 'অনুমোদিত',
    'admin.rejectedMembers': 'বাতিল',
    'admin.confirmedAmount': 'নিশ্চিত আদায়',
    'admin.outstandingAmount': 'বকেয়া',
    'admin.search': 'নাম, মোবাইল বা ব্যাচ দিয়ে খুঁজুন',
    'admin.filterBatch': 'ব্যাচ',
    'admin.filterStatus': 'অবস্থা',
    'admin.all': 'সব',
    'admin.noResults': 'কোনো আবেদন নেই',
    'admin.details': 'বিস্তারিত',
    'admin.submittedOn': 'জমা দেওয়ার তারিখ',
    'admin.familyMembers': 'পরিবারের সদস্য',
    'admin.reviewNote': 'মন্তব্য',
    'admin.rejectReason': 'বাতিলের কারণ লিখুন',
    'admin.reviewedBy': 'যাচাই করেছেন',
    'admin.memberNote': 'সদস্যের বার্তা',
    'admin.paymentReported': 'সদস্য যা জানিয়েছেন',
    'admin.noPaymentYet': 'সদস্য এখনো পেমেন্টের তথ্য দেননি',
    'admin.confirmPayment': 'পেমেন্ট নিশ্চিত করুন',
    'admin.selectAll': 'সব নির্বাচন করুন',
    'admin.selectLoaded': 'দেখানো সবগুলো নির্বাচন',
    'admin.selectRow': 'এই আবেদনটি নির্বাচন করুন',
    'admin.selectedCount': 'টি নির্বাচিত',
    'admin.clearSelection': 'নির্বাচন বাদ দিন',
    'admin.approveSelected': 'নির্বাচিতগুলো অনুমোদন',
    'admin.confirmSelected': 'নির্বাচিতগুলো নিশ্চিত করুন',
    'admin.rejectSelected': 'নির্বাচিতগুলো বাতিল',
    'admin.bulkApproveTitle': 'একসাথে অনুমোদন',
    'admin.bulkConfirmTitle': 'একসাথে পেমেন্ট নিশ্চিত',
    'admin.bulkRejectTitle': 'একসাথে বাতিল',
    'admin.bulkNoteHint': 'এই মন্তব্যটি নির্বাচিত প্রতিটি আবেদনে লেখা হবে।',
    'admin.bulkWarnUnpaid': 'টি আবেদনে এখনো পেমেন্টের তথ্য নেই — সেগুলো বাদ পড়বে।',
    'admin.bulkDone': 'টি সম্পন্ন হয়েছে',
    'admin.bulkSkipped': 'টি করা যায়নি',
    'admin.andMore': 'জন আরও',
    'admin.newAdmin': 'নতুন অ্যাডমিন',
    'admin.editAdmin': 'অ্যাডমিন সম্পাদনা',
    'admin.setPassword': 'পাসওয়ার্ড দিন',
    'admin.assignBatches': 'ব্যাচ নির্ধারণ করুন',
    'admin.batchFrom': 'শুরু',
    'admin.batchTo': 'শেষ',
    'admin.active': 'সক্রিয়',
    'admin.disabled': 'নিষ্ক্রিয়',
    'admin.superOnly': 'শুধু সুপার অ্যাডমিন এই পৃষ্ঠাটি দেখতে পারেন।',
    'admin.contact': 'মোবাইল',

    'common.loading': 'একটু অপেক্ষা করুন…',
    'common.taka': '৳',
  },

  en: {
    'app.name': 'Sammalani Alumni',
    'app.tagline': 'One school, one family',
    'nav.home': 'Home',
    'nav.dashboard': 'Dashboard',
    'nav.batches': 'Batches',
    'nav.guests': 'Family',
    'nav.profile': 'Profile',
    'nav.login': 'Log in',
    'nav.logout': 'Log out',
    'nav.signup': 'Register',

    'cta.findMe': 'Find my name',
    'cta.login': 'Log in',
    'cta.continue': 'Continue',
    'cta.back': 'Back',
    'cta.save': 'Save',
    'cta.cancel': 'Cancel',
    'cta.add': 'Add',
    'cta.remove': 'Remove',
    'cta.submit': 'Send for approval',
    'cta.viewAll': 'View all',
    'cta.loadMore': 'Load more',
    'cta.approve': 'Approve',
    'cta.reject': 'Reject',
    'cta.confirm': 'Confirm',
    'cta.edit': 'Edit',
    'cta.close': 'Close',

    'landing.est': 'Established 1968',
    'landing.heroTitle': 'Grand Reunion 2027',
    'landing.heroSub': 'Batches 1968 to 2026 — everyone is invited',
    'landing.countdown': 'Time remaining',
    'landing.days': 'days',
    'landing.hours': 'hours',
    'landing.mins': 'mins',
    'landing.secs': 'secs',
    'landing.foundSoFar': 'Alumni found so far',
    'landing.ofTotal': 'of an estimated',
    'landing.batchCoverage': 'Coverage by batch',
    'landing.coverageNote': 'Darker = more alumni found. The earliest batches need your help.',
    'landing.registered': 'Registered',
    'landing.teachers': 'Teachers',
    'landing.batches': 'Batches',

    'auth.loginTitle': 'Log in',
    'auth.loginSub': 'Just your mobile number — no password needed',
    'auth.phone': 'Mobile number',
    'auth.phoneHelp': 'Example: 01712345678',
    'auth.sendCode': 'Send code',
    'auth.otpTitle': 'Enter the code',
    'auth.otpSub': 'We sent a 6-digit code to your mobile',
    'auth.verify': 'Verify',
    'auth.resend': 'Send the code again',
    'auth.noAccount': 'First time here?',
    'auth.needHelp': 'Need help? Call',

    'signup.title': 'Find your name',
    'signup.sub': 'We have the school’s old registers. You do not need to fill in a long form.',
    'signup.step1': 'Which year did you pass SSC?',
    'signup.step2': 'Is your name in this list?',
    'signup.step3': 'Confirm with your mobile number',
    'signup.yearPlaceholder': 'e.g. 1974',
    'signup.searchName': 'Search by name',
    'signup.notFound': 'My name is not in the list',
    'signup.thisIsMe': 'This is me',
    'signup.foundCount': 'found',
    'signup.newName': 'Your full name',
    'signup.welcome': 'Welcome!',
    'signup.welcomeSub': 'Your profile is ready.',

    'dash.greeting': 'Assalamu Alaikum',
    'dash.yourBatch': 'Your batch',
    'dash.eventCard': 'Reunion 2027',
    'dash.notRegistered': 'You have not registered yet',
    'dash.registerNow': 'Register now',
    'dash.registered': 'Approved',
    'dash.awaitingReview': 'Awaiting verification',
    'dash.rejected': 'Not approved',
    'dash.draft': 'Draft — not sent yet',
    'dash.totalDue': 'Total due',
    'dash.attending': 'Coming with you',
    'dash.people': 'people',
    'dash.profileComplete': 'Profile completeness',
    'dash.missingTitle': 'Still missing from your batch',
    'dash.missingSub': 'Do you know any of them? Share their number and help us find them.',
    'dash.iKnowThem': 'I know them',
    'dash.notices': 'Notice board',
    'dash.quickAdd': 'Add a classmate',

    'guests.title': 'Add your family',
    'guests.sub': 'Bring your spouse, children or other family members with you',
    'guests.addMember': 'Add a member',
    'guests.name': 'Name',
    'guests.relation': 'Relation',
    'guests.age': 'Age',
    'guests.food': 'Food preference',
    'guests.tshirt': 'T-shirt size',
    'guests.none': 'No family members added yet',
    'guests.yourTicket': 'Your ticket',
    'guests.summary': 'Cost summary',
    'guests.total': 'Total',
    'guests.free': 'Free',
    'guests.rel.SPOUSE': 'Spouse',
    'guests.rel.CHILD': 'Child',
    'guests.rel.PARENT': 'Parent',
    'guests.rel.SIBLING': 'Sibling',
    'guests.rel.OTHER': 'Other',
    'guests.food.REGULAR': 'Regular',
    'guests.food.NO_BEEF': 'No beef',
    'guests.food.VEG': 'Vegetarian',
    'guests.submitted': 'Sent for approval',
    'guests.submittedSub': 'Your batch coordinator will verify your details and approve it.',
    'guests.approved': 'Registration approved',
    'guests.approvedSub': 'Your seat is confirmed. Show this page on the day.',
    'guests.rejected': 'Registration not approved',
    'guests.howToPay': 'How to pay',
    'guests.howToPaySub': 'There is no online payment in the app. Pay the coordinator below directly.',
    'guests.coordinator': 'Your batch coordinator',
    'guests.reportPayment': 'I have paid — tell the coordinator',
    'guests.payMethod': 'How did you pay',
    'guests.payRef': 'Transaction / slip number',
    'guests.payAmount': 'Amount',
    'guests.payReported': 'Payment details sent — awaiting confirmation',
    'guests.payConfirmed': 'Payment confirmed',
    'guests.payRejected': 'Payment could not be confirmed',
    'guests.note': 'Anything the coordinator should know? (optional)',
    'pay.BKASH': 'bKash',
    'pay.NAGAD': 'Nagad',
    'pay.ROCKET': 'Rocket',
    'pay.BANK': 'Bank',
    'pay.CASH': 'Cash',
    'pay.OTHER': 'Others',

    'batches.title': 'All batches',
    'batches.sub': '1968 to 2026 — 59 batches in total',
    'batches.found': 'found',
    'batches.missing': 'missing',
    'batches.members': 'students',
    'batches.deceased': 'Late',

    'profile.title': 'My profile',
    'profile.name': 'Full name',
    'profile.batch': 'Batch (SSC year)',
    'profile.occupation': 'Occupation',
    'profile.city': 'Present address',
    'profile.email': 'Email',
    'profile.gender': 'Gender',
    'profile.dob': 'Date of birth',
    'profile.blood': 'Blood group',
    'profile.optional': 'Optional',
    'profile.moreDetails': 'More details (optional)',
    'profile.moreDetailsSub': 'None of these are required. Blood group helps in an emergency.',
    'profile.saved': 'Saved',
    'gender.MALE': 'Male',
    'gender.FEMALE': 'Female',
    'gender.OTHER': 'Other',
    'common.notSet': 'Not given',

    /* ---- admin portal ---- */
    'admin.portal': 'Admin portal',
    'admin.login': 'Admin sign in',
    'admin.loginSub': 'For committee members — username and password',
    'admin.username': 'Username',
    'admin.password': 'Password',
    'admin.signIn': 'Sign in',
    'admin.signOut': 'Sign out',
    'admin.backToSite': 'Back to the site',
    'admin.overview': 'Overview',
    'admin.members': 'Member verification',
    'admin.payments': 'Payments',
    'admin.accounts': 'Admin accounts',
    'admin.roleSUPER_ADMIN': 'Super admin',
    'admin.roleGROUP_ADMIN': 'Group admin',
    'admin.scopeAll': 'All batches',
    'admin.scopeBatches': 'Batches',
    'admin.pendingMembers': 'Awaiting verification',
    'admin.pendingPayments': 'Payments to confirm',
    'admin.approvedMembers': 'Approved',
    'admin.rejectedMembers': 'Rejected',
    'admin.confirmedAmount': 'Confirmed collection',
    'admin.outstandingAmount': 'Outstanding',
    'admin.search': 'Search by name, mobile or batch',
    'admin.filterBatch': 'Batch',
    'admin.filterStatus': 'Status',
    'admin.all': 'All',
    'admin.noResults': 'Nothing to review',
    'admin.details': 'Details',
    'admin.submittedOn': 'Submitted',
    'admin.familyMembers': 'Family members',
    'admin.reviewNote': 'Note',
    'admin.rejectReason': 'Write the reason for rejecting',
    'admin.reviewedBy': 'Reviewed by',
    'admin.memberNote': 'Message from the member',
    'admin.paymentReported': 'What the member reported',
    'admin.noPaymentYet': 'The member has not reported a payment yet',
    'admin.confirmPayment': 'Confirm payment',
    'admin.selectAll': 'Select all',
    'admin.selectLoaded': 'Select all shown',
    'admin.selectRow': 'Select this application',
    'admin.selectedCount': 'selected',
    'admin.clearSelection': 'Clear selection',
    'admin.approveSelected': 'Approve selected',
    'admin.confirmSelected': 'Confirm selected',
    'admin.rejectSelected': 'Reject selected',
    'admin.bulkApproveTitle': 'Approve together',
    'admin.bulkConfirmTitle': 'Confirm payments together',
    'admin.bulkRejectTitle': 'Reject together',
    'admin.bulkNoteHint': 'This note is written onto every selected application.',
    'admin.bulkWarnUnpaid': 'of them have not reported a payment yet — those will be skipped.',
    'admin.bulkDone': 'done',
    'admin.bulkSkipped': 'could not be decided',
    'admin.andMore': 'more',
    'admin.newAdmin': 'New admin',
    'admin.editAdmin': 'Edit admin',
    'admin.setPassword': 'Set password',
    'admin.assignBatches': 'Assign batches',
    'admin.batchFrom': 'From',
    'admin.batchTo': 'To',
    'admin.active': 'Active',
    'admin.disabled': 'Disabled',
    'admin.superOnly': 'Only a super admin can open this page.',
    'admin.contact': 'Mobile',

    'common.loading': 'One moment…',
    'common.taka': '৳',
  },
} as const

export type TKey = keyof (typeof DICT)['en']

/* ==================================================================== *
 *  App context
 * ==================================================================== */

type Ctx = {
  lang: Lang
  setLang: (l: Lang) => void
  t: (k: TKey) => string
  /** Convert digits to Bangla numerals when the UI is in Bangla. Groups thousands. */
  n: (v: number | string) => string
  /** Years — same digit conversion, but never grouped (2010, not 2,010). */
  yr: (v: number | string) => string
  money: (v: number) => string
  user: Person | null
  setUser: (p: Person | null) => void
  ready: boolean
  logout: () => Promise<void>
}

const AppCtx = createContext<Ctx | null>(null)

const BN_DIGITS = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯']

export function AppProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => (localStorage.getItem('sammalani.lang') as Lang) || 'bn')
  const [user, setUser] = useState<Person | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const session = api.getSession()
    if (session) {
      api.me()
        .then((p) => setUser(p))
        .catch(() => {/* token invalid / network error — user stays null → redirect to login */})
        .finally(() => setReady(true))
    } else {
      setReady(true)
    }
  }, [])

  useEffect(() => {
    document.documentElement.lang = lang
  }, [lang])

  const setLang = useCallback((l: Lang) => {
    setLangState(l)
    localStorage.setItem('sammalani.lang', l)
  }, [])

  const t = useCallback((k: TKey) => DICT[lang][k] ?? DICT.en[k] ?? k, [lang])

  const n = useCallback(
    (v: number | string) => {
      const s = typeof v === 'number' ? v.toLocaleString('en-US') : v
      return lang === 'bn' ? s.replace(/\d/g, (d) => BN_DIGITS[+d]) : s
    },
    [lang],
  )

  const yr = useCallback(
    (v: number | string) => {
      const s = String(v)
      return lang === 'bn' ? s.replace(/\d/g, (d) => BN_DIGITS[+d]) : s
    },
    [lang],
  )

  const money = useCallback((v: number) => `৳${n(v)}`, [n])

  const logout = useCallback(async () => {
    await api.logout()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ lang, setLang, t, n, yr, money, user, setUser, ready, logout }),
    [lang, setLang, t, n, yr, money, user, ready, logout],
  )

  return <AppCtx.Provider value={value}>{children}</AppCtx.Provider>
}

export function useApp() {
  const c = useContext(AppCtx)
  if (!c) throw new Error('useApp must be used inside AppProvider')
  return c
}
