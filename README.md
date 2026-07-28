<p align="center">
  <img src=".github/banner.png" alt="Moo-vie" width="100%" />
</p>

# Moo-vie — application Android TV native

Application de streaming pour Android TV, **native Kotlin** (Jetpack Compose for TV
+ Media3/ExoPlayer), façon SmartTube : front pensé pour la télécommande, lecture
native, extraction des sources **on-device** — pas de backend, pas de compte,
100 % open source.

## Fonctionnalités

- **Accueil** : hero dynamique, rail « Reprendre la lecture » (progression et
  reprise au timecode), rangées tendances / mieux notés (TMDB), badges vus.
- **Recherche** : résultats en grille, historique persistant (suppression
  unitaire ou totale), flux clavier → résultats au D-pad.
- **Fiches film & série** : casting, saisons/épisodes avec vignettes, marquage
  vu/non vu (épisode, saison entière ou film), progression par épisode.
- **Lecture directe** : les sources chargent dès l'ouverture de la fiche ; un
  seul bouton **Lire/Reprendre** joue la meilleure source dans ta langue
  (VF/VOSTFR/VO). Panneau de sources détaillé en secours ou au choix manuel.
- **Lecteur** : Media3/ExoPlayer (HLS, DASH, MP4), reprise de lecture,
  sous-titres externes, sélection de pistes, touches média de la télécommande
  (MediaSession), seek 5 s / 15 s.
- **Réglages** : clé TMDB, langue de stream par défaut, langue d'interface,
  sources activables/désactivables et ordonnables par priorité.
- **Mises à jour intégrées** : l'app vérifie les releases GitHub au démarrage et
  propose l'installation en un clic depuis la TV (bannière dismissable).

## Installation

Télécharger le dernier `moovie-vX.Y.Z.apk` depuis les
[Releases](https://github.com/JibayMcs/MooVie/releases) et l'installer sur
l'appareil (sideload). Au premier lancement, renseigner une clé TMDB (gratuite,
[themoviedb.org](https://www.themoviedb.org/settings/api)) dans
**Réglages → API & Clés**. Les mises à jour suivantes se font directement
depuis l'app.

## Pourquoi natif (et pas une WebView)

Une app native n'a ni CORS ni pubs par construction : elle envoie des requêtes
HTTP directes avec les en-têtes voulus (Referer/Origin/User-Agent) et lit le
flux. Aucun proxy, aucun serveur : le boîtier ne fait tourner que l'app.

## Stack

| Couche | Techno |
|--------|--------|
| UI | Jetpack Compose for TV (`androidx.tv:tv-material`) |
| Lecture | Media3 / ExoPlayer (HLS, DASH, MP4) + MediaSession |
| Réseau | Retrofit + OkHttp + kotlinx.serialization |
| Extraction | OkHttp + Jsoup + crypto Java (déobfuscation packer, AES) |
| Réglages / données | DataStore Preferences |
| Images | Coil |
| CI / release | GitHub Actions : tag `vX.Y.Z` → APK signé en Release |

## Architecture

```
app/src/main/java/fr/moovie/tv/
├── MainActivity.kt            # Activity unique + navigation par état + bannière de mise à jour
├── MooVieApp.kt               # Application
├── ui/
│   ├── theme/                 # MooVieTheme (couleurs TV)
│   ├── components/            # MoovieButton / MoovieIconButton (design system)
│   ├── navigation/Screen.kt   # destinations
│   ├── home/                  # accueil : hero, rail reprise, rangées TMDB
│   ├── search/                # recherche + historique
│   ├── details/               # fiches film/série, lecture directe, slide-over sources
│   ├── player/                # lecteur Media3 (reprise, pistes, MediaSession)
│   ├── settings/              # réglages par catégories
│   └── update/                # bannière + ViewModel de mise à jour
└── data/
    ├── tmdb/                  # API + modèles + repository TMDB
    ├── search/                # historique de recherche
    ├── settings/              # SettingsRepository (DataStore)
    ├── sources/               # providers de sources + extracteurs d'hébergeurs
    ├── update/                # UpdateRepository (GitHub Releases)
    └── watch/                 # progression de lecture + vu/non vu
```

## Build

```bash
./gradlew assembleDebug     # build de dev
./gradlew assembleRelease   # nécessite keystore.properties (voir release.yml)
```

Un émulateur Android TV préconfiguré et des scripts de test sont fournis dans
`emulator/` (voir son README).
