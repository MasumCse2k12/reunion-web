# Play Store Main Listing

Copy for Play Console → **Grow → Store presence → Main store listing**, kept in
the repo so it can be reviewed in a PR instead of being typed into the browser
at submission time. Companion to `RELEASE_NOTES.md`, which holds the per-release
"What's new" text.

**Field limits** (Play truncates silently past these)

| Field | Limit | Required to submit |
|---|---|---|
| App name | 30 characters | yes |
| Short description | 80 characters | yes |
| Full description | 4000 characters | yes |

**Constraints**

- The **short description** is what shows under the app name in search results
  and above the fold on the listing page. It is the one line most people read.
- The **full description** is plain text — Play does not render Markdown. The
  `•` characters and the ALL-CAPS headings below are literal.
- Do not put "free", "best", "#1", download counts, or emoji in the app name or
  short description — Play's metadata policy rejects promotional text there.
- `bn-BD` only appears if Bengali is added as a listing language in Play
  Console. Without it, use the English text alone.

---

## en-US

### App name — 16 characters

```text
Sammilani Alumni
```

### Short description — 66 characters

```text
Find your batch and register for the Sammilani Grand Reunion 2027.
```

### Full description — 1421 characters

```text
The official app of the Sammilani Alumni Association, built for the Grand Reunion 2027 of Sammilani Secondary School.

Whether you sat for your SSC in 1968 or in 2026, this is where your batch comes back together.

REGISTER FOR THE REUNION
Sign up once with your name, mobile number and SSC batch year. The association verifies your details, and you can follow your status at every step — draft, awaiting verification, or approved.

BRING YOUR FAMILY
Add the family members and guests coming with you, and watch your total due update as you go.

FIND YOUR BATCH
Browse every batch from 1968 to 2026 and see who has already joined. Your dashboard also shows the classmates still missing from your batch — if you know one of them, share their number and help us reach them.

KEEP YOUR PROFILE COMPLETE
Add your photo, occupation, current city, date of birth and blood group. A completeness meter shows what is still left to fill in.

PAY AND TRACK
Pay with bKash, Nagad, Rocket, bank transfer or cash, and check your payment status at any time.

STAY INFORMED
Read the notice board for announcements, see the reunion event details, and browse the list of teachers who taught us.

IN BANGLA AND ENGLISH
Switch between বাংলা and English anywhere in the app.

This app is for former students of Sammilani Secondary School. Registration is reviewed by the alumni association before it is approved.

একসাথে আবার · Together Again
```

---

## bn-BD

### App name — 19 characters

```text
সম্মিলনী অ্যালামনাই
```

### Short description — 60 characters

```text
নিজের ব্যাচ খুঁজুন, গ্র্যান্ড রিইউনিয়ন ২০২৭-এ নিবন্ধন করুন।
```

### Full description — 1244 characters

```text
সম্মিলনী প্রাক্তন ছাত্র সংসদের অফিশিয়াল অ্যাপ — সম্মিলনী মাধ্যমিক বিদ্যালয়ের গ্র্যান্ড রিইউনিয়ন ২০২৭ উপলক্ষে।

আপনি ১৯৬৮ সালের এসএসসি ব্যাচ হোন বা ২০২৬ — এখানেই আপনার ব্যাচ আবার একসাথে হবে।

রিইউনিয়নে নিবন্ধন
নাম, মোবাইল নম্বর ও এসএসসি ব্যাচ দিয়ে একবার নিবন্ধন করুন। সংসদ আপনার তথ্য যাচাই করবে, আর প্রতিটি ধাপে অবস্থা দেখতে পাবেন — খসড়া, যাচাইয়ের অপেক্ষায়, বা অনুমোদিত।

পরিবারকে সাথে নিন
আপনার সাথে যাঁরা আসছেন তাঁদের যুক্ত করুন, আর মোট প্রদেয় হালনাগাদ হতে দেখুন।

ব্যাচ খুঁজুন
১৯৬৮ থেকে ২০২৬ পর্যন্ত সব ব্যাচ দেখুন, কে কে যুক্ত হয়েছেন জানুন। ড্যাশবোর্ডে দেখতে পাবেন আপনার ব্যাচের যাঁদের এখনো খুঁজে পাওয়া যায়নি — কাউকে চিনলে তাঁর নম্বর দিয়ে সাহায্য করুন।

প্রোফাইল সম্পূর্ণ করুন
ছবি, পেশা, বর্তমান ঠিকানা, জন্ম তারিখ ও রক্তের গ্রুপ যোগ করুন। কতটুকু বাকি আছে তা সম্পূর্ণতার মিটারে দেখুন।

পেমেন্ট ও হিসাব
বিকাশ, নগদ, রকেট, ব্যাংক বা নগদ টাকায় পরিশোধ করুন এবং যেকোনো সময় পেমেন্টের অবস্থা দেখুন।

খবর জানুন
নোটিশ বোর্ডে ঘোষণা পড়ুন, অনুষ্ঠানের তথ্য দেখুন, আর শিক্ষকমণ্ডলীর তালিকা ঘুরে দেখুন।

বাংলা ও ইংরেজি
অ্যাপের যেকোনো জায়গা থেকে বাংলা ও English-এর মধ্যে পরিবর্তন করুন।

এই অ্যাপটি সম্মিলনী মাধ্যমিক বিদ্যালয়ের প্রাক্তন শিক্ষার্থীদের জন্য। নিবন্ধন অনুমোদনের আগে প্রাক্তন ছাত্র সংসদ তা যাচাই করে।

একসাথে আবার · Together Again
```

---

## Still needed on the listing page

Text alone will not let you submit. Play also requires, on the same page:

- **App icon** — 512×512 PNG, 32-bit, under 1 MB. The launcher icon in
  `app/src/main/res/mipmap-*/ic_launcher.png` is the source; export at 512.
- **Feature graphic** — 1024×500 PNG or JPG. Shown at the top of the listing.
- **Phone screenshots** — 2 minimum, 8 maximum, 16:9 or 9:16, each side between
  320 px and 3840 px. Dashboard, batch list, profile and the reunion event
  screen are the four worth showing.

And, elsewhere in the console: a privacy policy URL, the Data safety form, and
the content rating questionnaire.

---

## Naming — unresolved

`RELEASE_NOTES.md` flags the same open question, and it applies here too:
`strings.xml` uses **Sammilani** while `RELEASE.md`, the
`alumni.sammalani.edu.bd` domain and the signing certificate DN use
**Sammalani**. The copy above uses **Sammilani**, matching the name that appears
under the app icon after install — Play flags a mismatch between the listing
title and the installed app name. If **Sammalani** is the correct spelling, fix
`strings.xml` first and then update this file, not the other way round.
