# Sammalani Alumni — Demo Web App

A working, clickable demo of the alumni platform for **Sammalani Secondary School, Chalitatala**
(est. 1968) and **Grand Reunion 2027**. Responsive: one codebase serves desktop web, tablet, and a
phone layout with an app-style bottom nav.

**There is no backend.** All data is generated from a fixed seed and lives in `localStorage`, so
the demo is fully interactive — you can register, add family members, submit for approval, and
review that submission as an admin — without a server. Everything talks to one file
(`src/lib/api.ts`), which is the single place you swap for the real Spring Boot service later. See §7.

**There is no payment gateway, by design.** Money changes hands offline: a member pays their batch
coordinator directly (bKash / Nagad / bank / cash), reports the transaction reference in the app,
and the coordinator confirms it by hand in the admin portal. No third-party integration is required
to run the reunion.

Verified on 2026-07-28: `npm run build` and `tsc --noEmit` both pass clean on Node 22.14.0.

---

## 1. What is in the demo

### Member site

| Route | Screen | Notes |
|---|---|---|
| `/` | Landing | Countdown, live "found so far" counter, 59-batch coverage heatmap, notices, teachers & In Memoriam |
| `/signup` | **Claim your profile** | 3 steps: SSC year → pick your name off the school register → confirm by mobile + OTP. Signing up puts you straight into the coordinator's verification queue. |
| `/login` | **Login** | Phone + 6-digit OTP. No password anywhere. |
| `/app` | **Dashboard** | Verification status, registration status, batch coverage, profile completeness ring, missing classmates with one-tap referral, notice board |
| `/app/guests` | **Add family members** | Spouse / child / parent / sibling, automatic ticket pricing by relation and age, live cost summary, **send for approval**, offline payment instructions, report a transaction reference |
| `/app/batches` | All batches | 1968–2026, coverage per batch |
| `/app/batches/:year` | Batch detail | Found / Missing / In Memoriam tabs, member search |
| `/app/profile` | Profile | Name, batch, phone, occupation, city, plus optional email / gender / date of birth / blood group. Privacy note. |

### Admin portal

Reached from the footer link on the landing page, or directly at `/admin/login`. Dark chrome, its
own session — an admin session is never a member session and vice versa.

| Route | Screen | Notes |
|---|---|---|
| `/admin/login` | Sign in | Username + password. The demo prints the seeded credentials and fills them on tap. |
| `/admin` | Overview | Pending verifications, payments to confirm, approved / rejected counts, confirmed vs outstanding money — all scoped to the batches this admin owns |
| `/admin/members` | **Member verification** | The queue. Filter by batch and status, search by name / mobile / batch, open a member to see every detail they gave, then approve or reject with a note (a reason is required to reject). Rows can also be ticked and decided in bulk — see below. |
| `/admin/payments` | **Payment confirmation** | Same queue, keyed on what the member reported paying. Confirm or reject by hand, one at a time or in bulk. |
| `/admin/accounts` | Admin accounts | **Super admin only.** Create group admins, assign them a batch range, set their password, disable or remove them. |

**One at a time, or many.** Both queues carry a checkbox per row and a select-all for the current
filter. Tick some and a bar appears at the bottom with **Approve/Confirm selected** and **Reject
selected**; either opens a confirmation listing who is about to be decided, with one note that is
written onto every one of them. A reason is still required to reject — forty people getting
"declined" with no explanation is worse than one, not better.

The bulk call is partial, not all-or-nothing: each row is decided on its own, and anything it
refuses comes back named with the reason, shown as a summary above the queue. A payment cannot be
confirmed for someone who has not reported one yet, so those rows are counted in the confirmation
sheet before you commit and listed as skipped afterwards.

**Two roles.** A **super admin** sees all 59 batches and is the only one who can create admin
accounts or set passwords. A **group admin** is a batch coordinator: they see and act on only the
batch years assigned to them, and a member outside that range is invisible to them — the scoping is
enforced in the API layer, not just hidden in the UI.

Seeded demo accounts:

| Username | Password | Role | Batches |
|---|---|---|---|
| `superadmin` | `Sammalani@2027` | Super admin | All |
| `coord.early` | `Narail@1968` | Group admin | 1968–1985 |
| `coord.mid` | `Narail@1986` | Group admin | 1986–2005 |
| `coord.late` | `Narail@2006` | Group admin | 2006–2026 |

**Bangla is the default language**, with an EN toggle in the header. Bangla uses a serif face and
Bangla numerals (২০১০, ৳১,৫০০) throughout. Root font size is 18px and every touch target is ≥48px —
these are deliberate choices for the 1968–1985 alumni, not accidents. Do not "fix" them.

### Demo affordances

- **OTP is always `123456`** — and the screen shows it, with a tap-to-fill button.
- **"ডেমো লগইন (কোড ছাড়া)"** on the login page skips OTP entirely. Use this when presenting so you
  never fumble in front of an audience.
- The signed-in user is **Md Masum Billah, SSC 2010**.
- A yellow banner marks the whole thing as a demo, with a **Reset demo** button that wipes state.

---

## 2. Prerequisites

Your machine currently has **Node v18.19.1**, which will not run this — Vite 8 requires
`^20.19.0 || >=22.12.0`. Install a current Node without disturbing your other projects:

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
exec $SHELL
nvm install 22          # or 24
nvm use 22
node -v                 # expect v22.x or v24.x
```

An `.nvmrc` is included, so `nvm use` picks the right version inside this folder from then on.

---

## 3. Run locally

```bash
cd web
npm install
npm run dev
```

Open **http://localhost:5173**.

Other scripts:

```bash
npm run build       # production build → dist/
npm run preview     # serve the built dist/ locally on :4173
npm run typecheck   # tsc --noEmit
```

---

## 4. See it on your phone (before you host anything)

`npm run dev` already binds to all interfaces (`--host`). On the same Wi-Fi:

```bash
hostname -I | awk '{print $1}'      # e.g. 192.168.0.105
```

Open `http://192.168.0.105:5173` on your phone. Add it to your home screen — the manifest is set up,
so it launches full-screen with no browser chrome and looks like a native app. **That home-screen
install is worth showing to the committee**; most people cannot tell it from a Play Store app, and
it costs you nothing.

---

## 5. Host it free, temporarily

The app is a pure static SPA — no server, no database, no environment variables. Every option below
is free and needs no credit card.

### Option A — Netlify Drop (fastest; ~2 minutes, no CLI, no account required to start)

```bash
npm run build
```

Go to **https://app.netlify.com/drop** and drag the `web/dist` folder onto the page. You get a live
URL like `https://curious-halva-8f2a1c.netlify.app` immediately. Claim it with a free account to
rename it to something you can say out loud on the phone, e.g. `sammalani-alumni.netlify.app`.

This is the right choice for the alumni presentation. Use it.

### Option B — Vercel (nicer for repeat deploys)

```bash
npm i -g vercel
cd web
vercel            # first run: answer the prompts, accept defaults
vercel --prod
```

`vercel.json` is included with the SPA rewrite already configured.

### Option C — Cloudflare Pages (best if you want to attach a real domain now)

```bash
npm i -g wrangler
npm run build
wrangler pages deploy dist --project-name sammalani-alumni
```

Free tier includes unlimited bandwidth and a free `*.pages.dev` subdomain.

### Option D — Jenkins, continuously (once you stop deploying by hand)

The repository root has a `Jenkinsfile` that checks out
[`MasumCse2k12/reunion-web`](https://github.com/MasumCse2k12/reunion-web), runs
`npm ci` → `typecheck` → `build`, deploys to Vercel, then smoke-tests the deployed URL and fails the
build on anything but a 200. `main` deploys to production; any other branch gets a preview URL.

Full setup — plugins, the one credential, both job types, webhooks, and what to do when the agent
has no Docker — is written out in the comment header of that file. Read it there rather than here,
so the instructions cannot drift from the pipeline they describe.

### SPA routing

`public/_redirects` (Netlify + Cloudflare) and `vercel.json` (Vercel) are both included, so deep
links like `/app/guests` work on refresh. **Without these you get a 404 on reload** — a classic and
embarrassing demo failure.

> **GitHub Pages is the one to avoid.** It serves from a subpath and has no SPA rewrite, so
> `BrowserRouter` routes 404 on refresh. If you must use it, switch to `HashRouter` in `src/App.tsx`.

### A custom domain, cheaply

If you want `sammalani.org` on the demo, all three hosts accept a custom domain on the free tier —
you only pay the registrar (~৳1,200/year). Worth doing **before** the presentation: a real domain
does more for credibility with the committee than any feature you could add in the same hour.
Register it in the alumni association's name, not your personal account.

---

## 6. Presenting to the alumni committee

A five-minute sequence that lands. Reset the demo first, and put your phone on the projector rather
than a laptop — everyone in that room will use this on a phone.

1. **Landing page.** Let the countdown and the number sit for a second. Then scroll to the batch
   heatmap and point at the gold squares: *"these are our 1968–1985 batches — this is the actual
   problem, and this screen is how we make everyone see it."*
2. **Claim flow** (`Find my name`). Pick 1974. Scroll the register of 35 real-looking names. Tap one.
   *"He doesn't fill in a form. He finds his own name and taps yes. Sixty seconds, no password."*
3. **Dashboard.** Show "missing from your batch" and tap **আমি চিনি**. *"This is how we reach the
   people we can't reach. Every person who joins brings us three more names."*
4. **Add family.** Add a spouse, then a child aged 8. Watch the total update live. *"Nobody has to
   ask the committee what a child's ticket costs."*
5. **Send for approval** → the coordinator's name and number appear. *"There is no card, no gateway,
   no ৳-per-transaction to anybody. He pays you the way he already pays everyone — bKash or cash —
   and types the TrxID here."*
6. **Open `/admin/login` in a second tab** and sign in as `coord.late`. The submission you just made
   is sitting in the queue. Approve it, then confirm the payment. Switch back to the first tab.
   *"That is the whole control. A coordinator only ever sees his own batches. Nothing is automatic."*
7. Close on: *"Everything you just saw is the front. The data is fake. To make it real we need a
   server — about ৳3,000–4,000 a month — and the school's old admission registers."*

That last sentence is the entire purpose of the demo. Say it plainly.

---

## 7. Swapping in the real backend

`src/lib/api.ts` is the only file that knows data is fake. Every screen calls `api.*` and nothing
else. The method signatures and return shapes already match the REST contract in
`../docs/00-SYSTEM-DESIGN.md` §5.

When the Spring Boot service is ready:

1. Add `.env`:
   ```
   VITE_API_BASE_URL=https://api.sammalani.org/api/v1
   ```
2. Replace each method body in `src/lib/api.ts` with a real `fetch`:
   ```ts
   const BASE = import.meta.env.VITE_API_BASE_URL
   async function http<T>(path: string, init?: RequestInit): Promise<T> {
     const res = await fetch(BASE + path, {
       ...init,
       headers: { 'content-type': 'application/json', ...authHeader(), ...init?.headers },
     })
     if (!res.ok) throw new ApiError(...)
     return res.json()
   }

   async requestOtp(phone: string) {
     return http<{ challengeId: string }>('/auth/otp/request', {
       method: 'POST',
       body: JSON.stringify({ phone }),
     })
   }
   ```
3. Delete `src/mock/` and the `DEMO_OTP` / `demoLogin` / `resetDemo` escape hatches.

**No component changes.** That is the point of the seam, and it is why the demo is not throwaway
work.

Three things the mock deliberately does *not* model, so don't be surprised later: real OTP delivery
latency and failure; server-side visibility filtering on contact details (the backend must never
send a phone number the viewer isn't allowed to see — do not rely on the client to hide it); and
**real admin authentication**.

On that last one, be blunt with yourself: `adminApi.login` compares a plaintext password held in
`localStorage`. It is a stage prop. The real service must hash with Argon2id server-side, never ship
a password hash to the browser, issue a short-lived admin token distinct from the member token, and
re-check batch scope on **every** admin endpoint — `adminCoversBatch` is enforced in `api.ts` here
precisely so the shape of that check is already written down, but a client-side check protects
nobody.

---

## 8. Project layout

```
web/
├── index.html
├── public/
│   ├── _redirects              # SPA fallback (Netlify + Cloudflare)
│   ├── favicon.svg
│   └── manifest.webmanifest    # installable PWA
├── src/
│   ├── main.tsx  App.tsx       # router + protected routes
│   ├── index.css               # Tailwind v4 theme: brand green, heritage gold, 18px root
│   ├── lib/
│   │   ├── api.ts              # ← THE SWAP POINT — `api` (members) + `adminApi` (portal)
│   │   ├── store.tsx           # i18n (bn/en), Bangla numerals, member auth context
│   │   └── adminStore.tsx      # admin session context — deliberately separate
│   ├── mock/data.ts            # seeded dummy dataset — delete when the backend lands
│   ├── components/             # ui.tsx, Layout.tsx, AuthShell.tsx
│   └── pages/
│       ├── …                   # Landing, Login, Signup, Dashboard, Guests, Batches, BatchDetail, Profile
│       └── admin/              # AdminLogin, AdminLayout, AdminOverview, ReviewQueue, AdminMembers,
│                               # AdminPayments, AdminAccounts
├── netlify.toml  vercel.json
└── package.json
```

## 9. Stack

React 19 · Vite 8 · TypeScript 5.9 · Tailwind CSS 4 · react-router 7 · lucide-react.
Production bundle: **~331 KB JS (101 KB gzipped)**, 41 KB CSS (7.7 KB gzipped) — deliberately small,
because a good number of your users are on 3G in Narail.
