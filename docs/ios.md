# Moo-vie on iPhone and iPad

🇫🇷 [Version française](ios.fr.md) · ↩ [README](../README.md)

Moo-vie runs on iOS from the same Kotlin Multiplatform codebase as every other
platform: the screens you see are Android's, not a parallel version. What differs
is what Apple imposes, and this page is only about that — installing,
configuring, updating.

> [!IMPORTANT]
> Installation goes through **SideStore**, not the App Store. Take one thing away
> before you start: a free Apple ID's signature **expires after 7 days**, and it
> has to be renewed for the app to keep opening. SideStore does that on its own —
> see [The 7-day renewal](#the-7-day-renewal).

**You need:** an iPhone or iPad on **iOS 15 or later**, an Apple ID (your own is
enough — no paid developer account required), and Wi-Fi.

---

## 1. Install SideStore

[SideStore](https://sidestore.io) is the tool that installs and, above all,
**re-signs** the app. That second part is the point: Apple requires an app
installed outside the store to carry a valid signature, and a free Apple ID's
signature is only good for seven days. SideStore renews it on its own as long as
it runs on the same network as your computer — which is why we use it rather
than a one-off manual install.

Follow the [official guide](https://docs.sidestore.io/docs/installation/prerequisites),
which lists the prerequisites and then walks through the install. Budget twenty
minutes the first time; you won't come back to it.

Two steps in that guide stop everyone who skips them:

- **The pairing file**, produced from a computer. It is what lets SideStore talk
  to your device.
- **The local VPN app**, installed from the App Store — the one the guide names
  under prerequisites. Without it SideStore installs nothing, and the reason is
  not obvious: it needs to reach a service **on your own device**, and iOS only
  allows that through a local network loopback. This VPN carries no traffic
  outside, it exists only for that.

> [!NOTE]
> The name of that companion app has changed across SideStore versions. Trust the
> one given in the prerequisites linked above rather than a name written here,
> which would age badly.

> [!TIP]
> AltStore works too: SideStore is a fork of it and reads the same source format.
> If you already use it, keep it — the steps below are identical.

## 2. Add the Moo-vie source

Rather than downloading a file for every release, **add the source once**:
SideStore will then find updates by itself.

1. Open SideStore → **Sources** tab → **+** in the top right.
2. Paste this address:

   ```
   https://github.com/JibayMcs/MooVie/releases/latest/download/sidestore.json
   ```

3. Confirm. **Moo-vie** appears in the source list.
4. Open it and tap **Install** (*Free* / *Get*).

That address is **stable**: GitHub always resolves it to the latest published
release, and the file travels with the `.ipa` it describes. You will never have
to change it.

<details>
<summary>Installing an <code>.ipa</code> by hand (not recommended)</summary>

You can also download `moovie-vX.Y.Z.ipa` from the
[Releases](https://github.com/JibayMcs/MooVie/releases) page and open it in
SideStore via **My Apps → +**.

This is discouraged for one specific reason: SideStore won't know a newer version
exists, and you'll have to repeat the operation every time. The source from
step 2 is what makes updating automatic.
</details>

## 3. Trust the certificate

On first launch, iOS may refuse to open the app. That's expected: it doesn't yet
know your Apple ID's certificate.

**Settings → General → VPN & Device Management → your Apple ID → Trust.**

Once only, unless you change accounts.

## 4. Configure the app

On first launch, Moo-vie asks for a **TMDB API key**. It's free, and it's what
fetches titles, posters and synopses — without it the home screen has nothing to
show.

1. Create an account on [themoviedb.org](https://www.themoviedb.org/signup).
2. Request a key under [Settings → API](https://www.themoviedb.org/settings/api)
   (pick *Developer*; personal use is accepted).
3. Copy the **API Key (v3 auth)** and paste it into Moo-vie:
   **Settings → API & Keys → TMDB key**.

The home screen fills in as soon as the key is saved.

**Nothing else is required.** Subtitles work as-is in published builds. Two
settings are still worth a look:

- **Settings → Subtitles** — connecting an OpenSubtitles account raises the daily
  download limit and shows your remaining quota.
- **Settings → DNS** — enable DNS-over-HTTPS if your ISP blocks host domains.
  Several French providers do.

## 5. Updates

**This is where iOS genuinely differs from the other platforms.**

On Android and desktop, Moo-vie updates itself: a banner appears in the app and
the install happens in place. On iOS that is impossible — an iOS app cannot
install a new version of itself, and that is locked by the system rather than a
missing API. The "Update" section of the settings is therefore **absent** here,
instead of offering a button that could not keep its promise.

Instead, **SideStore does the watching**, through the source you added in step 2.
Every time a release is published on GitHub, `sidestore.json` is regenerated and
attached to that release; the `releases/latest/download/…` address then points at
it with nothing to change on your side.

What you have to do:

1. Open SideStore. It refreshes its sources on its own, and you can force it by
   pulling the list down from the **Browse** tab.
2. A badge appears on **My Apps** when a newer version exists.
3. Tap **Update** next to Moo-vie.

Your settings, history, watchlist and downloads are kept: this is an app update,
not a reinstall.

> [!NOTE]
> The source declares the version *and* the build number — exactly the ones the
> app itself reports — plus the file's SHA-256. That is what lets SideStore know
> whether there is really something new, rather than offering the same update
> forever, and refuse a corrupted download instead of installing a truncated file.

### The 7-day renewal

Distinct from updating, and often confused with it. A free Apple ID's signature
expires after **7 days**; past that, the app won't open. It isn't broken and your
data is intact — the signature needs redoing.

SideStore handles it on its own, under two conditions: that it runs in the
background, and that it can reach its refresh service. In practice, open
SideStore now and then and leave it on the **My Apps** tab; it renews whatever is
close to expiring.

> [!TIP]
> If the app refuses to open after a week away: open SideStore, tap **Refresh
> All**, wait for it to finish, then launch Moo-vie. That's almost always it.

---

## What differs from Android, and why

Not everything could be ported, and the gaps below are deliberate choices rather
than oversights.

| | iOS | Why |
|---|---|---|
| **In-app updates** | absent | An iOS app cannot install itself. SideStore holds that role. |
| **Casting to a TV** | absent | The sender role rests on network discovery and a local HTTP server, both left out of the port. |
| **Remote / pairing** | absent | Same reason: these are living-room roles, and the TV is an Android device. |
| **Interface language** | follows the system | iOS exposes this setting outside the app — Settings → Moo-vie → Preferred Language. Duplicating it would give two places to answer one question. |
| **Orientation** | portrait, except the player | The player and the full-screen trailer switch to landscape by themselves, as on the Android phone. |

Everything else is there: home, catalogue, search, discovery, title pages,
seasons and episodes, history and statistics, watchlist, offline downloads,
subtitles, encrypted backup and sync, profiles.

## When something goes wrong

**The app closes as soon as it opens.** The signature has expired: open SideStore
and hit **Refresh All**. If it still closes right after a fresh install, that's a
defect in the build — please open an
[issue](https://github.com/JibayMcs/MooVie/issues) with the crash log
(**Settings → Privacy & Security → Analytics & Improvements → Analytics Data**,
look for `Moo-vie`).

**The home screen stays empty.** The TMDB key is missing or invalid. Check it
under **Settings → API & Keys**; it is the *v3 auth* key, not the read access
token.

**No source plays.** Try enabling DNS-over-HTTPS under **Settings → DNS**.
Several ISPs block host domains at the resolver.

**Tapping Install does nothing.** Almost always the local VPN app is missing or
not enabled — see step 1. SideStore needs it to reach a service on your own
device; without it, it does not fail outright, it simply never gets there.

**SideStore refuses to install: "maximum number of apps".** A free Apple ID only
allows **3** signed apps at a time. Remove one from **My Apps**.

## Building it yourself

You need a Mac: the Kotlin/Native toolchain for Apple targets only exists on
macOS. On Linux and Windows, Gradle configures those targets without complaining
but their tasks cannot run — verification therefore belongs to the CI's macOS
runner (`.github/workflows/ci-ios.yml`).

```bash
brew install xcodegen
cd iosApp && xcodegen generate      # the .xcodeproj is not checked in
open Moovie.xcodeproj               # then Cmd+R
```

The Xcode project is **generated** from `iosApp/project.yml` rather than
committed: a `.pbxproj` is a graph of objects with opaque identifiers, unreadable
in review and producing a merge conflict on every added file. The spec says the
same thing in forty lines you can actually read.

The Kotlin framework is compiled by Xcode itself, as a pre-build phase. To check
only the shared code, without going through Xcode:

```bash
./gradlew :app:compileIosMainKotlinMetadata   # common code + iosMain
./gradlew :app:linkDebugFrameworkIosArm64     # device target, exercises cinterop
```
