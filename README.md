<p align="center">
  <img src=".github/banner.png" alt="Moo-vie" width="100%" />
</p>

# Moo-Vie

Streaming app for **Android TV** and **desktop** (Linux, Windows, macOS), built from a
single Kotlin Multiplatform codebase. Source extraction runs **on-device**: no backend,
no account, no ads.

🇫🇷 [Version française](README.fr.md)

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
| **Screensaver** | |
| ![Screensaver](.github/screenshots/10-screensaver.jpg) | |

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

## License

Open source, for personal use. Moo-vie hosts no content: it only resolves links that are
already publicly reachable.
