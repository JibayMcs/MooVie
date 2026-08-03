<p align="center">
  <img src=".github/banner.png" alt="Moo-vie" width="100%" />
</p>

# Moo-Vie

Streaming app for **Android TV** and **desktop** (Linux, Windows, macOS), built from a
single Kotlin Multiplatform codebase. Source extraction runs **on-device**: no backend,
no account, no ads.

🇫🇷 [Version française](README.fr.md)

> [!IMPORTANT]
> Moo-vie is under active development. Things move between releases, and some rough
> edges are expected — [issues](https://github.com/JibayMcs/MooVie/issues) are welcome.

## Screenshots

| Home | Trending |
|:---:|:---:|
| ![Home](.github/screenshots/01-home.jpg) | ![Trending rails](.github/screenshots/02-rails.jpg) |
| **Search** | **Catalogue** |
| ![Search](.github/screenshots/05-search.jpg) | ![Catalogue](.github/screenshots/11-catalog.jpg) |
| **Movie details** | **Show details** |
| ![Movie details](.github/screenshots/03-movie.jpg) | ![Show details](.github/screenshots/06-tv.jpg) |
| **Episode details** | **Sources panel** |
| ![Episode details](.github/screenshots/07-episode.jpg) | ![Sources panel](.github/screenshots/04-sources.jpg) |
| **Player** | **Settings** |
| ![Player](.github/screenshots/08-player.jpg) | ![Settings](.github/screenshots/09-settings.jpg) |
| **Subtitles** | **Screensaver** |
| ![Subtitles settings](.github/screenshots/12-subtitles.jpg) | ![Screensaver](.github/screenshots/10-screensaver.jpg) |

> UI available in French, English and Spanish.

## Features

- **Home** — contextual hero, *Continue watching* rail with per-episode progress,
  trending and top-rated rails (TMDB), watched badges.
- **Search** — result grid, persistent history, D-pad flow from keyboard to results.
- **Movies & shows** — cast, seasons and episodes with stills, per-episode details page,
  watched/unwatched marking (episode, whole season or movie).
- **One-press playback** — sources load as soon as a title opens; a single
  **Play / Resume** picks the best source in your language (VF / VOSTFR / VO). A sources
  panel is there for manual host selection.
- **Player** — resume at timecode, subtitle and audio track selection, playback speed,
  15 s seek, scrub mode on the progress bar, remote media keys.
- **Subtitles** (OpenSubtitles) — searched from the player, ranked by language, frame
  rate and release, and downloaded only on an explicit press since the daily allowance is
  small. Already-downloaded ones are marked so you never spend twice. Comes with an offset
  *and* a frame-rate correction: exact matching relies on hashing the video file, which
  segmented streams make impossible, so drift is the normal case rather than an accident.
- **Skip intro & credits** (TheIntroDB) — skipping credits rolls into the next episode.
- **Auto-play next episode** — 10 s countdown at the end of an episode, cancellable,
  rolling over to the next season.
- **Screensaver** — the poster bounces around the screen while playback stays paused.
- **Backup & restore** — export your progress, watchlist, history and settings to a USB
  stick, and pick them up on another device. The import previews the file before acting
  (counts, export date, source device) and lets you **merge** — most recent progress wins,
  nothing is lost — or **replace**. A first-launch screen offers the restore instead of
  dropping you on an empty home.
- **Offline-friendly** — TMDB responses and resolved source links are cached on disk.
- **In-app updates** — periodic check against GitHub Releases; a banner on the home
  screen, a discreet chip during playback.

## Roadmap

- **Android phones & tablets** — work in progress. The UI is built for a 10-foot
  screen and a D-pad today; the port is about touch layouts and portrait, not a
  second codebase.
- **Light profiles** — separate progress, watchlist and watched state per profile
  ("Living room" / "Kids"), picked at launch. Backups will carry them.
- **Contribute back to TheIntroDB** — report an intro or credits timestamp from
  the player when it is missing, to fill in the database the *Skip intro* buttons
  read from.
- **No iOS support planned.** The constraints are heavy — sideloading, App Store
  policy, a separate player stack — and I have no Apple device to test on.
  Shipping something I cannot run myself would be worse than not shipping it.

## Stack

| Layer | Tech |
|---|---|
| Language / build | Kotlin 2.0, Kotlin Multiplatform (`androidMain` / `desktopMain` / shared `jvmCommon`) |
| UI | Compose Multiplatform, shared design system (`MoovieButton`, rails, dialogs) |
| Playback | Media3 / ExoPlayer on Android · libVLC (VLCJ) on desktop |
| Network | Retrofit + OkHttp + kotlinx.serialization, DNS-over-HTTPS |
| Extraction | OkHttp + Jsoup + Java crypto (packer deobfuscation, AES) |
| Storage | DataStore Preferences, OkHttp disk cache |
| Images | Coil 3 |
| CI | GitHub Actions: a `vX.Y.Z` tag builds the signed APK, AppImage, `.msi` and `.dmg` |

## Install

Grab the latest build from [Releases](https://github.com/JibayMcs/MooVie/releases):

- **Android TV** — sideload `moovie-vX.Y.Z.apk`
- **Linux** — `moovie-vX.Y.Z-x86_64.AppImage` (`chmod +x`, then run — no install, no root)
- **Windows** — `moovie-vX.Y.Z.msi`
- **macOS** — `moovie-vX.Y.Z.dmg`

The Linux AppImage bundles its own Java runtime **and libVLC**, so it runs on any
distribution with nothing to install (tested on Ubuntu 22.04/24.04, Debian 12 and
Arch), and updates itself from inside the app. On **Windows**, the `.msi` installs
per user — no admin rights — and adds Start-menu and desktop shortcuts; the in-app
banner then updates it in place. On **macOS**, the banner opens the release page
(the `.dmg` is installed by hand). Windows and macOS still need **VLC** installed
on the machine.

On first launch, paste a free [TMDB API key](https://www.themoviedb.org/settings/api)
under **Settings → API & Keys**. Later updates are handled from inside the app.

## Build

```bash
./gradlew assembleDebug              # Android debug APK
./gradlew assembleRelease            # signed APK (needs keystore.properties)
./gradlew :app:run                   # desktop app
./gradlew :app:packageDistributionForCurrentOS
```

Layout: `app/src/commonMain` (resources), `app/src/jvmCommon` (ViewModels, repositories,
shared UI), `app/src/androidMain`, `app/src/desktopMain`. A preconfigured Android TV
emulator and its test scripts live in `emulator/`.

**Subtitles work out of the box in the published builds** — the APK, AppImage, MSI and
DMG from [Releases](https://github.com/JibayMcs/MooVie/releases) all ship with what they
need. Nothing to create, nothing to paste. Optionally, connecting an OpenSubtitles
account in the settings raises the daily download limit and shows the quota left.

The rest of this paragraph only concerns people **building from source**. Subtitles rely
on an OpenSubtitles *consumer key*, which identifies the application; OpenSubtitles
mandates one key per app and bans accounts that ask their users to supply their own. That
key is injected at build time and is deliberately absent from this repository, so a build
from source simply turns subtitles off — everything else works. To enable them while
developing, create your own consumer on
[opensubtitles.com](https://www.opensubtitles.com/consumers) and drop the key in a
gitignored `opensubtitles.properties` at the root:

```properties
apiKey=YOUR_CONSUMER_KEY
```

`OPENSUBTITLES_API_KEY` in the environment works too, which is what CI uses.

## Contributing

**Contributions are open and wanted.** The desktop builds are the ones that need
eyes most: they are produced by CI for Linux, Windows and macOS, but each of those
lands on hardware, drivers and a libVLC install that nobody here can reproduce.
Testing one of them on your own machine is genuinely useful work.

If anything misbehaves — a build that won't start, a stream that plays on Android TV
but not on desktop, a control the remote can't reach — please
[open an issue](https://github.com/JibayMcs/MooVie/issues). What helps most:

- your platform and version (distribution, Windows/macOS build, TV box model)
- the app version, from **Settings → Updates**
- what you expected, and what happened instead
- for a playback problem: the title, and which source you picked in the panel

Pull requests are welcome too — a new source provider or host extractor is the most
valuable kind, since every dead link costs someone a title. `.claude/skills/add-source/`
documents how one is written and, more importantly, how to *measure* whether it earns
its place.

## Credits

Moo-vie stands on work done by others.

**Data & services**

- **[TMDB](https://www.themoviedb.org)** — every title, synopsis, poster, backdrop,
  cast and rating in the app comes from The Movie Database. It is what makes the
  catalogue a catalogue rather than a list of file names, and the app is useless
  without an API key of your own.
  *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- **[TheIntroDB](https://theintrodb.org)** — community-sourced intro and credits
  timestamps, behind the *Skip intro* / *Skip credits* buttons and the roll into
  the next episode.
- **[Cloudflare](https://1.1.1.1) and [Quad9](https://quad9.net)** — the DNS-over-HTTPS
  resolvers you can pick from, which is what keeps source lookups working on
  networks where those domains are blocked at the DNS level.

**Open source**

- [Kotlin](https://kotlinlang.org), [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html),
  [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) and
  [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — JetBrains
- [Media3 / ExoPlayer](https://developer.android.com/media/media3) and
  [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) — Google / AndroidX
- [VLC / libVLC](https://www.videolan.org) — VideoLAN, via
  [vlcj](https://github.com/caprica/vlcj) by Caprica Software, which is what plays
  video on the desktop builds
- [OkHttp and Retrofit](https://square.github.io/okhttp/) — Square
- [jsoup](https://jsoup.org) — parses the pages sources are extracted from
- [Coil](https://coil-kt.github.io/coil/) — image loading and disk cache

## License

Open source, for personal use. Moo-vie hosts no content: it only resolves links that are
already publicly reachable.
