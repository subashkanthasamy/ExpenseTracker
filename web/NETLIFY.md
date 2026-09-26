# Hosting the web client on Netlify

Live at **https://expense-tracker-daily-shared.netlify.app**, on the free tier, alongside the
Firebase Hosting target. This is the full guide; `README.md` has the short version.

---

## Why the CLI, and not a git-connected build

Netlify's usual model — connect the GitHub repo, let it build on every push — **cannot work for
this repo**, for two independent reasons:

1. **The shared module is not in the repo.** `web/package.json` depends on
   `"expensetracker-shared": "file:../shared/build/dist/js/productionLibrary"`, and
   `shared/build/` is gitignored (`.gitignore:79`). A fresh clone does not contain that package,
   so `npm install` fails before Vite ever runs.
2. **Building it there would need the Android SDK.** `settings.gradle.kts` includes `:app`, and
   `local.properties` (which carries `sdk.dir`) is gitignored. Configure-on-demand is not
   enabled, so Gradle configures the Android module on any invocation and AGP fails looking for
   an SDK that Netlify's image does not have.

So Gradle runs on your machine and Netlify receives a finished `dist`. Two things fall out of
that, both good:

- **Direct deploys consume no build minutes.** The free tier's 300 min/month is irrelevant here.
- **Netlify holds no secrets and no environment variables.** Vite inlines `import.meta.env.VITE_*`
  at build time, so the Firebase config travels inside the bundle.

The cost is that deploying is a deliberate command rather than a consequence of pushing.

> **Do not run `netlify init`.** It configures continuous deployment from the GitHub remote —
> precisely the thing that cannot work. It succeeds cleanly and then fails on every push. Use
> `sites:create` + `link` instead, as below.

---

## One-time setup

### 1. Install the CLI

```bash
npm i -g netlify-cli
netlify --version
```

Roughly 200 MB. To avoid a global install, prefix every `netlify` command below with `npx`.

### 2. Log in

```bash
netlify login
```

Opens a browser; click **Authorize**. The token lands in `~/.config/netlify/config.json`, so this
is once per machine. Note this is Netlify's own OAuth — unrelated to the Firebase CLI login, which
is blocked by Workspace policy on this account.

### 3. Create the site and link the folder

```bash
cd web
netlify sites:create --name expense-tracker-daily-shared
netlify status          # confirm the folder is linked
```

The name must be globally unique across `netlify.app`; it becomes `<name>.netlify.app`. If
`netlify status` reports the directory is not linked:

```bash
netlify link --name expense-tracker-daily-shared
```

That writes `web/.netlify/state.json`, which is gitignored.

### 4. Authorise the domain in Firebase

**Firebase console → Authentication → Settings → Authorized domains → Add domain →**
`expense-tracker-daily-shared.netlify.app`

Skip this and Google sign-in fails with `auth/unauthorized-domain` while email/password keeps
working — so it presents as "Google sign-in is broken", not "a domain is missing". Each host needs
its own entry; the Firebase Hosting domains are separate.

Nothing else needs configuring. No Firestore rules change: the rules are origin-agnostic and
already govern this client.

---

## Deploying

```bash
cd web && npm run deploy
```

Three steps in order: Gradle rebuilds the Kotlin/JS package, Vite builds `dist`, the CLI uploads
it. Expect:

```
> Task :shared:jsBrowserProductionLibraryDistribution
✓ built in 800ms
✔ Deploy is live!

Unique deploy URL: https://<hash>--expense-tracker-daily-shared.netlify.app
Website URL:       https://expense-tracker-daily-shared.netlify.app
```

**Website URL** is production. For a preview that leaves production alone:

```bash
npm run deploy:draft    # prints only the unique deploy URL
```

The Gradle step is inside the script on purpose. Running `vite build` alone would happily ship a
bundle whose domain logic is older than the repo, and nothing would look wrong.

---

## Verifying a deploy

**HTTP status codes cannot prove a file is absent here.** The SPA fallback `/*  /index.html  200`
matches any path that is not a real file, so a missing asset, a typo'd URL and a deliberately
removed source map all return `200` with the app shell. Check the *body*:

```bash
SITE=https://expense-tracker-daily-shared.netlify.app

# Deep links resolve — this is the _redirects test
curl -sI "$SITE/expenses" | head -1                                    # want: HTTP/2 200

# No source map published — first byte '{' is a real map, '<' is the shell
curl -s "$SITE/assets/index--EARHsQB.js.map" | head -c 1               # want: <

# The bundle does not point at one either
curl -s "$SITE/assets/index--EARHsQB.js" | grep -c sourceMappingURL    # want: 0

# Headers from public/_headers reached the CDN
curl -sI "$SITE/" | grep -iE 'x-frame-options|x-content-type|referrer-policy'

# The build carries real Firebase config (not the missing-config throw path)
curl -s "$SITE/assets/index--EARHsQB.js" | grep -c 'expense-tracker-8005e'   # want: 1
```

Then in a browser: sign in with Google — that is what step 4 protects and it will work on
localhost regardless — and confirm the expense list populates and the dashboard total agrees
with it.

Worth doing once, and only possible with a second account: **sign in as a household member
rather than the owner and confirm the expense list is populated rather than empty.** An empty
list is the signature of a rejected Firestore query, and the owner's view cannot reveal it.

---

## What is configured, and where

| File | Purpose |
|---|---|
| `web/public/_redirects` | SPA fallback. `BrowserRouter` means `/expenses` has no file behind it; without this a reload 404s. In `public/` so Vite copies it into `dist/`. |
| `web/public/_headers` | `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`. **No CSP** — see below. |
| `web/vite.config.ts` | `sourcemap: false`, plus `preserveSymlinks` and `optimizeDeps.exclude` that the linked Kotlin/JS package needs. |
| `web/package.json` | `deploy` and `deploy:draft`. |
| `web/.gitignore` | `.netlify/` — the CLI's local state. |

**No `Content-Security-Policy`, deliberately.** A correct one has to admit the Firebase Auth and
Firestore hosts, the Firestore streaming connection and Google Fonts. Getting it slightly wrong
breaks sign-in *silently*, which is a poor trade for a personal deployment. Worth adding as its
own task with the network panel open, not as a guess.

**Source maps are off.** Vite embeds `sourcesContent`, so a map is the complete readable
TypeScript of this client — 4.5 MB of it — served to anyone who opens devtools. Deploys are 1.1 MB
instead of 5.3 MB as a side effect. The cost is that production stack traces show minified names;
set `sourcemap: true` temporarily when you need to debug something live.

**The CLI also writes `web/.netlify/netlify.toml`.** It contains absolute paths from the machine
that ran `netlify link` and a `command = "npm run build"` it inferred. It is gitignored and inert
for CLI deploys — but if you ever do connect git, that is the command Netlify would try to run,
and it would fail for the reasons at the top of this file.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| `404` on `/expenses` but `/` works | `_redirects` did not reach `dist/`. It must be in `web/public/`, not `web/`. |
| `auth/unauthorized-domain` on Google sign-in | Step 4 not done for this exact domain. |
| `Firebase config missing: …` thrown on load | Built without `web/.env`. Rebuild; Vite reads it only at build time. |
| Blank page, console `Unexpected token '<'` | An asset 404'd and the SPA fallback returned HTML where JS was expected. Usually a stale `index.html` referencing a hash that is no longer deployed — redeploy. |
| Domain logic behaves like an older build | `vite build` was run without the Gradle step. Use `npm run deploy`. |
| `name already exists` on `sites:create` | Site names are global across `netlify.app`. Pick another. |
| `netlify login` blocked | Build locally and drag `web/dist` onto https://app.netlify.com/drop. `_redirects` still applies — it is inside `dist`. |

---

## Free tier, rollbacks, custom domains

The free tier gives 100 GB bandwidth a month and free HTTPS via Let's Encrypt. This app is
~267 KB compressed per cold load, so bandwidth is not a realistic constraint. Build minutes do
not apply, since Netlify never builds.

**Rolling back** is instant and does not need a rebuild: Netlify keeps every deploy. In the site's
**Deploys** tab, open a previous one and choose *Publish deploy*.

**A custom domain** is free to attach (Domain management → Add domain) and provisions a
certificate automatically. If you add one, add it to Firebase's authorized domains too — the same
step 4, for the new hostname.
