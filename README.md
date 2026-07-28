# Moo-vie — application Android TV native

Application de streaming pour Android TV, **native Kotlin** (Jetpack Compose for TV
+ Media3/ExoPlayer), façon SmartTube : front TV, lecture native, extraction des
sources **on-device** (pas de backend).

## Pourquoi natif (et pas une WebView)

Une app native n'a ni CORS ni pubs par construction : elle envoie des requêtes
HTTP directes avec les en-têtes voulus (Referer/Origin/User-Agent) et lit le flux.
Cela supprime le besoin des proxies CORS de la stack serveur actuelle. Le boîtier
ne fait tourner que l'app — pas de Node/Python/MySQL/Redis.

## Stack

| Couche | Techno |
|--------|--------|
| UI | Jetpack Compose for TV (`androidx.tv:tv-material`) |
| Lecture | Media3 / ExoPlayer (HLS, DASH, MP4) |
| Réseau | Retrofit + OkHttp + kotlinx.serialization |
| Extraction | OkHttp + Jsoup + crypto Java (portage des handlers Python/Node) |
| Réglages | DataStore Preferences |
| Images | Coil |

## Architecture

```
app/src/main/java/fr/moovie/tv/
├── MainActivity.kt            # Activity unique + navigation par état
├── MooVieApp.kt               # Application
├── ui/
│   ├── theme/                 # MooVieTheme (couleurs TV)
│   ├── navigation/Screen.kt   # destinations
│   ├── home/                  # accueil : rangées TMDB (Home + ViewModel)
│   ├── details/               # fiche film/série (stub)
│   ├── player/                # lecteur Media3
│   └── settings/              # réglages par catégories (clé TMDB, langue…)
└── data/
    ├── tmdb/                  # API + modèles + repository TMDB
    ├── settings/             # SettingsRepository (DataStore)
    └── sources/              # SourceExtractor + registre + VoeExtractor (exemple)
```

## Portée V1

- **Fait (scaffold)** : navigation, accueil TMDB, réglages (clé API/langue),
  lecteur Media3, squelette d'extraction avec un extracteur d'exemple (VOE).
- **À faire** : fiche détaillée (TMDB + saisons/épisodes), providers de sources
  (fstream, coflix, animesama…), extracteurs d'hébergeurs (uqload, doodstream…),
  navigation D-pad fine, sous-titres externes, sélection de piste VF/VO/VOSTFR.
- **Hors V1** : DRM Widevine (broadcasters publics), MPEG-TS, comptes/social,
  watchparty, VIP/paiement.

## Build

```bash
./gradlew assembleDebug
```

Configurer sa clé TMDB au premier lancement dans **Réglages → API & Clés**.
