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
| **Search** | **Movie details** |
| ![Search](.github/screenshots/05-search.jpg) | ![Movie details](.github/screenshots/03-movie.jpg) |
| **Show details** | **Episode details** |
| ![Show details](.github/screenshots/06-tv.jpg) | ![Episode details](.github/screenshots/07-episode.jpg) |
| **Sources panel** | **Player** |
| ![Sources panel](.github/screenshots/04-sources.jpg) | ![Player](.github/screenshots/08-player.jpg) |
| **Settings** | **Screensaver** |
| ![Settings](.github/screenshots/09-settings.jpg) | ![Screensaver](.github/screenshots/10-screensaver.jpg) |

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
| CI | GitHub Actions: a `vX.Y.Z` tag builds the signed APK, `.deb`, `.msi` and `.dmg` |

## Install

Grab the latest build from [Releases](https://github.com/JibayMcs/MooVie/releases):

- **Android TV** — sideload `moovie-vX.Y.Z.apk`
- **Linux** — `moovie-vX.Y.Z.deb`
- **Windows** — `moovie-vX.Y.Z.msi`
- **macOS** — `moovie-vX.Y.Z.dmg`

Desktop playback needs **VLC** installed on the machine.

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

## License

Open source, for personal use. Moo-vie hosts no content: it only resolves links that are
already publicly reachable.
