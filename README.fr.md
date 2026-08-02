<p align="center">
  <img src=".github/banner.png" alt="Moo-vie" width="100%" />
</p>

# Moo-Vie

Application de streaming pour **Android TV** et **desktop** (Linux, Windows, macOS),
depuis une seule base de code Kotlin Multiplatform. L'extraction des sources se fait
**on-device** : pas de backend, pas de compte, pas de pub.

🇬🇧 [English version](README.md)

## Captures

| Accueil | Tendances |
|:---:|:---:|
| ![Accueil](.github/screenshots/01-home.jpg) | ![Rangées tendances](.github/screenshots/02-rails.jpg) |
| **Recherche** | **Catalogue** |
| ![Recherche](.github/screenshots/05-search.jpg) | ![Catalogue](.github/screenshots/11-catalog.jpg) |
| **Fiche film** | **Fiche série** |
| ![Fiche film](.github/screenshots/03-movie.jpg) | ![Fiche série](.github/screenshots/06-tv.jpg) |
| **Fiche épisode** | **Panneau sources** |
| ![Fiche épisode](.github/screenshots/07-episode.jpg) | ![Panneau sources](.github/screenshots/04-sources.jpg) |
| **Lecteur** | **Réglages** |
| ![Lecteur](.github/screenshots/08-player.jpg) | ![Réglages](.github/screenshots/09-settings.jpg) |
| **Écran de veille** | |
| ![Écran de veille](.github/screenshots/10-screensaver.jpg) | |

> Captures prises en anglais ; l'interface est disponible en français, anglais et espagnol.

## Fonctionnalités

- **Accueil** — hero contextuel, rail *Reprendre la lecture* avec progression par épisode,
  rangées tendances et mieux notés (TMDB), badges vus.
- **Recherche** — résultats en grille, historique persistant, descente au D-pad du clavier
  vers les résultats.
- **Films & séries** — casting, saisons et épisodes avec vignettes, page de détail par
  épisode, marquage vu/non vu (épisode, saison entière ou film).
- **Lecture en un appui** — les sources chargent dès l'ouverture d'une fiche ; un seul
  bouton **Lire / Reprendre** joue la meilleure source dans ta langue (VF / VOSTFR / VO).
  Le panneau de sources reste là pour choisir un hébergeur à la main.
- **Lecteur** — reprise au timecode, choix des sous-titres et de la piste audio, vitesse de
  lecture, seek 15 s, mode scrub sur la barre de progression, touches média de la télécommande.
- **Passer intro & générique** (TheIntroDB) — passer le générique enchaîne l'épisode suivant.
- **Lecture auto de l'épisode suivant** — décompte de 10 s en fin d'épisode, annulable, qui
  bascule sur la saison suivante en fin de saison.
- **Écran de veille** — l'affiche rebondit à l'écran quand la lecture reste en pause.
- **Cache disque** — réponses TMDB et liens de sources résolus mis en cache.
- **Mises à jour intégrées** — vérification périodique des releases GitHub : bandeau sur
  l'accueil, pastille discrète pendant la lecture.

## Stack

| Couche | Techno |
|---|---|
| Langage / build | Kotlin 2.0, Kotlin Multiplatform (`androidMain` / `desktopMain` / `jvmCommon` partagé) |
| UI | Compose Multiplatform, design system partagé (`MoovieButton`, rails, dialogues) |
| Lecture | Media3 / ExoPlayer sur Android · libVLC (VLCJ) sur desktop |
| Réseau | Retrofit + OkHttp + kotlinx.serialization, DNS-over-HTTPS |
| Extraction | OkHttp + Jsoup + crypto Java (déobfuscation packer, AES) |
| Persistance | DataStore Preferences, cache disque OkHttp |
| Images | Coil 3 |
| CI | GitHub Actions : un tag `vX.Y.Z` produit l'APK signé, l'AppImage, le `.msi` et le `.dmg` |

## Installation

Récupérer le dernier build depuis les
[Releases](https://github.com/JibayMcs/MooVie/releases) :

- **Android TV** — sideload de `moovie-vX.Y.Z.apk`
- **Linux** — `moovie-vX.Y.Z-x86_64.AppImage` (`chmod +x` puis lancer — ni installation ni root)
- **Windows** — `moovie-vX.Y.Z.msi`
- **macOS** — `moovie-vX.Y.Z.dmg`

L'AppImage Linux embarque son runtime Java **et libVLC** : elle tourne sur
n'importe quelle distribution sans rien installer (vérifié sur Ubuntu 22.04/24.04,
Debian 12 et Arch) et se met à jour depuis l'app. Sous **Windows**, le `.msi`
s'installe par utilisateur — sans droits administrateur — et ajoute des raccourcis
au menu Démarrer et au bureau ; le bandeau intégré le met alors à jour en place.
Sous **macOS**, le bandeau ouvre la page de release (le `.dmg` s'installe à la
main). Windows et macOS nécessitent toujours **VLC** installé sur la machine.

Au premier lancement, coller une [clé API TMDB](https://www.themoviedb.org/settings/api)
gratuite dans **Réglages → API & Clés**. Les mises à jour suivantes se font depuis l'app.

## Build

```bash
./gradlew assembleDebug              # APK debug Android
./gradlew assembleRelease            # APK signé (nécessite keystore.properties)
./gradlew :app:run                   # app desktop
./gradlew :app:packageDistributionForCurrentOS
```

Découpage : `app/src/commonMain` (ressources), `app/src/jvmCommon` (ViewModels, repositories,
UI partagée), `app/src/androidMain`, `app/src/desktopMain`. Un émulateur Android TV
préconfiguré et ses scripts de test sont dans `emulator/`.

## Contribuer

**Les contributions sont libres et souhaitées.** Ce sont les versions desktop qui ont
le plus besoin de regards : la CI les produit pour Linux, Windows et macOS, mais
chacune atterrit sur un matériel, des pilotes et une installation libVLC que personne
ici ne peut reproduire. Tester l'une d'elles sur ta machine est un vrai coup de main.

Si quoi que ce soit se passe mal — une version qui ne démarre pas, un flux qui passe
sur Android TV mais pas sur desktop, un contrôle que la télécommande n'atteint pas —
[ouvre une issue](https://github.com/JibayMcs/MooVie/issues). Ce qui aide le plus :

- ta plateforme et sa version (distribution, build Windows/macOS, modèle de box TV)
- la version de l'app, dans **Réglages → Mises à jour**
- ce que tu attendais, et ce qui s'est produit à la place
- pour un problème de lecture : le titre, et la source choisie dans le panneau

Les pull requests sont bienvenues aussi — un nouveau catalogue de sources ou un
extracteur d'hébergeur est le plus utile, chaque lien mort coûtant un titre à
quelqu'un. `.claude/skills/add-source/` explique comment on en écrit un et, surtout,
comment *mesurer* s'il mérite sa place.

## Licence

Open source, pour un usage personnel. Moo-vie n'héberge aucun contenu : elle ne fait que
résoudre des liens déjà publiquement accessibles.
