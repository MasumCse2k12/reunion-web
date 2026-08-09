# Release Notes

Play Console "What's new" text, kept per release so it can be reviewed in a PR
instead of being written from scratch in the browser at upload time.

**Constraints**

- Hard limit **500 characters per language**. Play Console silently truncates
  past that — check the count before pasting.
- Only the first ~2 lines are visible before "Read more". Put the substance in
  the opening sentence; treat the bullets as optional reading.
- `bn-BD` only appears if Bengali is added as a listing language in Play
  Console. Without it, use the English text alone.
- Notes go in as **plain text**. The `•` characters are literal; Play does not
  render Markdown, so no `*` or `-` bullets.

---

## versionCode 3 — versionName 1.0.0 (first release)

Codes 1 and 2 were uploaded but rejected before rollout (targetSdk 34, then a
shadowed artifact left in the draft). Play retires a version code permanently
once it has been uploaded, so the first release users actually see is code 3 —
the "What's new" text below is unchanged from the original code 1 draft.

### en-US — 367 characters

```text
Welcome to the official app of the Sammilani Alumni Association — built for the Grand Reunion 2027.

• Sign up and verify your SSC batch
• Find classmates across batches from 1968 to 2026
• Update your profile and photo
• Reserve your reunion ticket and register guests
• Track your payment status
• Browse event details and our teachers

একসাথে আবার · Together Again
```

### bn-BD — 351 characters

```text
সম্মিলনী প্রাক্তন ছাত্র সংসদের অফিশিয়াল অ্যাপ — গ্র্যান্ড রিইউনিয়ন ২০২৭ উপলক্ষে।

• রেজিস্ট্রেশন করুন ও নিজের ব্যাচ যাচাই করুন
• ১৯৬৮ থেকে ২০২৬ ব্যাচের বন্ধুদের খুঁজে নিন
• প্রোফাইল ও ছবি হালনাগাদ করুন
• রিইউনিয়নের টিকিট ও অতিথি রেজিস্ট্রেশন করুন
• পেমেন্টের অবস্থা দেখুন
• অনুষ্ঠানের তথ্য ও শিক্ষকমণ্ডলীর তালিকা দেখুন

একসাথে আবার · Together Again
```

---

## Open question: Sammilani or Sammalani?

The two spellings are both live in this repo:

| Spelling | Where |
|---|---|
| **Sammilani** | `app/src/main/res/values/strings.xml` — `app_name`, splash screen |
| **Sammalani** | `RELEASE.md`, the `alumni.sammalani.edu.bd` domain, the signing certificate DN |

The notes above use **Sammilani**, matching the name users see under the app
icon. The Play Store listing title should agree with the installed app name —
if `Sammalani` is the correct form, fix `strings.xml` rather than the notes.

---

## Writing notes for future releases

Add a new `## versionCode N` section at the top; keep old ones for history.
Describe what changed for *users*, not what changed in the code — "you can now
pay with bKash" rather than "integrated payment gateway". Skip refactors,
dependency bumps and build fixes entirely; they read as noise on a store page.
