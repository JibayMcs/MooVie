# Sources dashboard

Watches which catalogues and hosters are still playable, keeps a history, and
posts to Discord **when something flips**.

```bash
cp tools/dashboard/config.example.json tools/dashboard/config.json
# put your Discord webhook in "webhookUrl", then:
node tools/dashboard/server.js
# → http://127.0.0.1:7788
```

No dependencies, no `npm install`: plain Node ≥ 18.

## What it is, and what it deliberately is not

It knows **nothing** about sources. It shells out to the project's own probes —
real application code — and reads the JSON they drop:

```
./gradlew :app:desktopTest --tests '*HosterHealthProbeTest' \
  --tests '*QuickCoverageProbeTest' -Dmoovie.probe=1 -Dmoovie.report=<dir>
```

Rewriting the scrapers in Node would mean monitoring a *second* program, one
that drifts from the app at the first domain rotation and reassures you while
the real thing is broken.

## Why it runs on your machine, never in CI

A GitHub runner's IP is not your living room. Several hosters block datacenter
ranges — vidzy answers a flat 403, wiflix replies `Bot shield active.` — so a
reading taken there would declare half the catalogue dead while it works fine at
home. `tools/check-sources.sh` already carries this rule; the dashboard inherits
it.

## What triggers an alert

**The flip, never the state.** "vidzy is dead" is not news if it was dead
yesterday; "vidzy went down last night" is. Consequences:

- the **first reading never alerts** — with nothing to compare against it cannot
  establish a flip, and alerting on state would fire eighteen notifications for
  hosters that have been dead for months;
- a name **missing** from either reading is ignored. Catalogues do not return the
  same hosters on every pass, and treating absence as death would alert on their
  moods. Same lesson as the sync tombstones: an absence is not a decision;
- **comebacks are reported too** — that is the signal to drop a name from
  `UNSUPPORTED_HOSTERS`.

## Files

| Path | |
|---|---|
| `server.js` | scheduler, history, alerting, static server |
| `public/index.html` | the page — flips first, then hosters, then catalogues |
| `config.json` | **gitignored, holds the webhook** — a secret, like the keystore |
| `config.example.json` | the publishable version |
| `tools/reports/history.jsonl` | one snapshot per line, gitignored |

## Keeping it alive across reboots

`runOnStart` plus the interval covers a session. For a real daemon on Linux,
a user unit avoids running anything as root:

```ini
# ~/.config/systemd/user/moovie-sources.service
[Service]
WorkingDirectory=%h/Programming/Android/Moo-vie
ExecStart=/usr/bin/node tools/dashboard/server.js
Restart=on-failure

[Install]
WantedBy=default.target
```

```bash
systemctl --user enable --now moovie-sources
loginctl enable-linger "$USER"   # survives logout
```

## Cost

Each reading resolves a few dozen links against real hosters and takes 3–4
minutes. Every 6 hours is four readings a day — enough to catch a breakage the
same day, discreet enough not to look like scraping. Do not lower it much
without a reason.
