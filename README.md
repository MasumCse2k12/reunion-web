# Sammalani Alumni

Alumni platform for **Sammalani Secondary School, Chalitatala, Narail** (established 1968) and the
**Grand Reunion 2027**. Batches 1968–2026 — 59 of them.

Live: **https://sammalani-alumni-web.vercel.app**

It is not scoped to the one event. The reunion is the reason people show up; the platform is meant to
outlive it as the school's permanent network — alumni, teachers, notices, programmes.

---

## Read this before you judge the code

**There is no backend yet.** Every screen talks to `web/src/lib/api.ts`, which resolves from a
seeded fixture set and persists to `localStorage`. That one file is the seam: when the Spring Boot
service lands, its method bodies become `fetch` calls and no component changes. Full detail in
[`web/README.md` §7](web/README.md).

**There is no payment gateway, deliberately.** A member sends their registration to their batch
coordinator, pays them offline (bKash / Nagad / bank / cash), and reports a transaction reference.
The coordinator confirms it by hand in the admin portal. No third-party integration is needed to run
the reunion, and none is planned.

**The admin login is a stage prop.** `adminApi.login` compares a plaintext password held in
`localStorage`. It demonstrates the workflow; it protects nothing. The real service must hash with
Argon2id server-side and re-check batch scope on every endpoint. Do not deploy this as if it were
access control.

---

## Layout

```
.
├── Jenkinsfile          # build → deploy → smoke test. Setup guide is in its header comment.
├── docs/
│   ├── 00-SYSTEM-DESIGN.md   # architecture, data model, REST contract, rollout
│   └── 03-TECH-STACK.md      # stack choices and why
└── web/                 # the app — React 19 · Vite 8 · TypeScript · Tailwind 4
    ├── src/lib/api.ts        # ← the swap point: all fake data enters here
    ├── src/mock/data.ts      # seeded fixtures — delete when the backend lands
    └── README.md             # the detailed guide: screens, demo script, hosting, backend swap
```

---

## Setup

### 1. Node 22

Vite 8 will not start on anything below `20.19` / `22.12`, and Debian and Ubuntu still ship Node 18.
Check first:

```bash
node -v
```

If that prints anything older, install Node 22 without disturbing your other projects:

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
exec $SHELL
nvm install 22 && nvm use 22
```

`web/.nvmrc` pins 22, so `nvm use` inside `web/` picks it up on its own after this.

### 2. Install and run

```bash
git clone https://github.com/MasumCse2k12/reunion-web.git
cd reunion-web/web
npm ci
npm run dev
```

Open **http://localhost:5173**. Nothing else is required — no database, no API keys, no `.env`.

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload, on `0.0.0.0` so your phone can reach it |
| `npm run typecheck` | `tsc --noEmit` — run this before you push |
| `npm run build` | Production bundle into `web/dist/` |
| `npm run preview` | Serve `dist/` exactly as a host would |

### 3. Sign in

Everything below is fictional and shipped on purpose, so the demo works with no server.

**As a member** — phone + OTP, and **the OTP is always `123456`**; the screen shows it with a
tap-to-fill button. Or use **"ডেমো লগইন (কোড ছাড়া)"** on `/login` to skip OTP entirely, which is what
you want when presenting to a room.

**As an admin** — go to `/admin/login` (also linked in the landing-page footer). The seeded accounts
are printed on that screen and fill in on tap:

| Username | Password | Role | Batches |
|---|---|---|---|
| `superadmin` | `Sammalani@2027` | Super admin | All 59 |
| `coord.early` | `Narail@1968` | Group admin | 1968–1985 |
| `coord.mid` | `Narail@1986` | Group admin | 1986–2005 |
| `coord.late` | `Narail@2006` | Group admin | 2006–2026 |

A super admin sees every batch and is the only one who can create admin accounts or set passwords. A
group admin is a batch coordinator: they see and act on only their assigned years. **These four
accounts must not survive into a real deployment** — they exist so the portal is explorable without
a database.

State lives in `localStorage`. The yellow demo banner has a **Reset demo** button that wipes it.

---

## Seeing the whole loop

The point of the product is the handoff between a member and their coordinator. Two tabs shows it:

1. Tab one — demo-login, go to **Family**, add a spouse and a child aged 8, then **Send for approval**.
2. Tab two — `/admin/login` as `coord.late` (the demo member is SSC 2010). The submission is sitting
   in **Member verification**. Approve it, then confirm the payment.
3. Back to tab one — the status has changed.

Sign in as `coord.early` instead and that same submission is invisible: 2010 is outside 1968–1985.
The scoping is enforced in `api.ts`, not hidden in the UI.

---

## Deploying

**By hand**, to the linked Vercel project:

```bash
cd web
npm run build
npx vercel --prod
```

**Continuously**, via the `Jenkinsfile` at this root: it checks out this repository, runs
`npm ci` → `typecheck` → `build`, deploys to Vercel, then smoke-tests the deployed URL and fails the
build on anything but a 200. `main` goes to production; every other branch gets a preview URL.

Setup — plugins, the single Vercel credential, both job types, the GitHub webhook, and what to do
when the agent has no Docker — is written out in the comment header of
[`Jenkinsfile`](Jenkinsfile). It lives there rather than here so the instructions cannot drift from
the pipeline they describe.

Netlify and Cloudflare Pages both work too; see [`web/README.md` §5](web/README.md). **GitHub Pages
is the one to avoid** — it has no SPA rewrite, so every route 404s on refresh.

---

## Design constraints worth knowing before you add a feature

These are not style preferences. They come from who the users are.

- **Bangla is the default**, English is the toggle. Bangla gets a serif face and Bangla numerals
  (২০১০, ৳১,৫০০) throughout.
- **Root font size is 18px and every touch target is ≥48px.** This is for the 1968–1985 alumni. Do
  not "fix" it.
- **Required profile fields stay capped at three** — name, batch, phone. Email, gender, date of
  birth and blood group are all optional and must stay that way.
- **Never expose an alumni phone number publicly.** The backend must filter it server-side; do not
  rely on the client to hide it.
- **Never auto-merge two people who look like duplicates.**

The binding constraint on this project is not engineering capacity. It is getting data out of
elderly alumni who will not fill in a form. Judge a proposed feature by whether it collects another
name from an old batch.
