<p align="center">
  <img src=".github/banner.png" alt="Moo-vie" width="100%" />
</p>

# Moo-Vie

Application de streaming pour **Android TV**, **Android mobile**, **desktop**
(Linux, Windows, macOS) et ~~iOS~~, depuis une seule base de code Kotlin Multiplatform.
L'extraction des sources se fait **on-device** : pas de backend, pas de compte, pas de pub.

🇬🇧 [English version](README.md)

> [!IMPORTANT]
> Moo-vie est en développement actif. Les choses bougent d'une version à l'autre, et
> quelques aspérités sont à prévoir - les [issues](https://github.com/JibayMcs/MooVie/issues)
> sont les bienvenues.

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
| **Sous-titres** | **Écran de veille** |
| ![Réglages des sous-titres](.github/screenshots/12-subtitles.jpg) | ![Écran de veille](.github/screenshots/10-screensaver.jpg) |

> Captures prises en anglais ; l'interface est disponible en français, anglais et espagnol.

## Fonctionnalités

- **Téléphones & tablettes** - le même APK que la TV, mis en page pour le pouce :
  navigation basse, portrait partout sauf dans le lecteur, qui bascule tout seul.
  Appui, double appui et pincement pilotent la lecture ; un appui long ouvre les
  menus contextuels que la télécommande atteint en maintenant OK. Pas une seconde
  base de code : une seule source Kotlin Multiplatform, TV ou tactile tranché à
  l'exécution.
- **Profils** - un nom et une couleur chacun, choisi au lancement. Progression,
  vu/non vu, liste et disposition de l'accueil sont propres à chaque profil ; les clés
  d'API, le DNS et l'ordre des sources restent ceux de l'installation. Sans mot de
  passe ni contrôle parental : ce qui est séparé, c'est la progression, pas l'accès -
  sur une télé partagée, l'ennui quotidien est de trouver l'épisode de quelqu'un
  d'autre dans *Reprendre la lecture*, pas que les autres voient ce qu'on regarde. Le
  sélecteur ne se montre pas tant qu'il n'existe qu'un profil.
- **Accueil, rangé par toi** - les rangées sont une disposition qui t'appartient, pas
  une liste figée. Épingle n'importe quel genre du catalogue (appui long sur TV et
  téléphone, clic droit sur desktop) en choisissant où il atterrit ; déplace, masque
  ou retire n'importe quelle rangée, y compris celles d'origine, depuis
  *Réglages → Accueil*. Une rangée épinglée se termine par une carte **En voir plus**
  qui rouvre son genre exact. Hero contextuel, *Reprendre la lecture* avec progression
  par épisode, tendances et mieux notés (TMDB), badges vus.
- **Recherche** - résultats en grille, historique persistant, descente au D-pad du clavier
  vers les résultats. Tri par pertinence, popularité, note, année ou titre, filtres
  par type, note minimale et décennie ; les filtres sont retenus par profil. TMDB
  ne sait pas trier une recherche par texte : le classement porte donc sur les
  résultats rapportés, et l'écran le dit.
- **Films & séries** - casting, saisons et épisodes avec vignettes, page de détail par
  épisode, marquage vu/non vu (épisode, saison entière ou film).
- **Lecture en un appui** - les sources chargent dès l'ouverture d'une fiche ; un seul
  bouton **Lire / Reprendre** joue la meilleure source dans ta langue (VF / VOSTFR / VO).
  Le panneau de sources reste là pour choisir un hébergeur à la main. Les catalogues
  étant francophones pour la plupart, la **version originale a son propre catalogue**,
  indexé sur l'identifiant TMDB. Aucune langue ne se substitue à une autre : le VOSTFR
  porte des sous-titres français incrustés qu'on ne peut pas retirer, ce n'est donc pas
  un remplaçant de la VO.
- **Lecteur** - reprise au timecode, choix des sous-titres et de la piste audio, vitesse de
  lecture, seek 15 s, mode scrub sur la barre de progression, touches média de la télécommande.
- **Sous-titres** (OpenSubtitles) - cherchés depuis le lecteur, classés par langue,
  cadence et release, et téléchargés uniquement sur un appui explicite, le quota
  quotidien étant serré. Ceux déjà téléchargés sont marqués, pour ne jamais payer deux
  fois. Avec un réglage de décalage **et** une correction de cadence : l'appariement
  exact repose sur une empreinte du fichier vidéo, impossible sur des flux segmentés, si
  bien que le décalage est le cas normal et non un accident.
- **Passer intro & générique** (TheIntroDB) - passer le générique enchaîne l'épisode suivant.
  Quand un segment manque, une icône de la barre de contrôle permet de le **signaler depuis
  le lecteur** : marquer le début, marquer la fin, confirmer. La couverture se fait épisode
  par épisode, les trous sont donc fréquents et chaque signalement les comble. Nécessite une
  clé TheIntroDB gratuite.
- **Lecture auto de l'épisode suivant** - décompte de 10 s en fin d'épisode, annulable, qui
  bascule sur la saison suivante en fin de saison.
- **Écran de veille** - l'affiche rebondit à l'écran quand la lecture reste en pause.
- **Sauvegarde & restauration** - exporte ta progression, ta liste, ton historique et tes
  réglages sur une clé USB - tous les profils, pas seulement celui que tu regardes - et
  retrouve-les sur un autre appareil. L'import montre le
  contenu du fichier avant d'agir (compteurs, date d'export, appareil d'origine) et laisse
  choisir entre **fusionner** - la progression la plus récente gagne, rien n'est perdu - et
  **remplacer**. Un écran de premier lancement propose la restauration plutôt que de
  déposer l'utilisateur sur un accueil vide. Sur Android, accorde l'*accès à tous les
  fichiers* quand l'écran le demande : sans lui le fichier ne peut aller que dans le
  dossier de l'app, que la désinstallation efface - la sauvegarde passerait alors d'un
  appareil à l'autre, mais pas d'une réinstallation à la suivante.
- **Synchro entre appareils** - la progression, la liste et l'historique se retrouvent sur
  chaque appareil, sans compte et sans serveur à nous. Tu fournis l'espace de stockage
  (Backblaze B2 aujourd'hui : une clé à créer et à coller, comme celle de TMDB) et chaque
  appareil y publie **son propre fichier** - personne n'écrase celui d'un autre. La fusion
  garde la décision la plus récente, y compris les suppressions : démarquer un épisode ici
  ne le fait pas ressusciter là-bas. Le contenu peut être chiffré par une phrase secrète
  que tu es seul à connaître ; sans elle, l'hébergeur ne voit que des octets. Une horloge
  mal réglée n'est pas un problème : l'écart avec le serveur est mesuré et corrigé.
- **Téléchargement hors ligne** - garde un film ou un épisode sur l'appareil et regarde-le
  sans réseau, sur TV, téléphone et desktop. Depuis le panneau des sources par un appui
  long, ou directement depuis le lecteur, où le bouton porte l'avancement en anneau. La
  playlist et ses segments sont réécrits en chemins locaux : la lecture ne rappelle jamais
  l'hébergeur, et un lien de source expire quand ce que tu as téléchargé reste.
- **Saisie depuis le téléphone** (Android TV) - taper une clé d'API à la télécommande,
  lettre par lettre sur un clavier en grille, est le pire moment de l'application. La TV
  affiche un QR code, ton téléphone ouvre une page servie par l'app elle-même sur le réseau
  local, et tu tapes au clavier tactile : clés TMDB et TheIntroDB, compte OpenSubtitles,
  identifiants de synchro et phrase secrète. Le serveur ne vit que pendant que le code est
  affiché, et son adresse porte un jeton tiré au hasard. Les valeurs en place sont
  pré-remplies : on relit ou on corrige une clé sans la retaper, et vider un champ
  l'efface. La page sert aussi de **télécommande** - croix directionnelle, OK, retour,
  lecture/pause, et un champ qui tape le texte sur la TV, ce qui rend une recherche
  supportable. Elle pilote Moo-vie, pas le téléviseur : elle ne peut ni l'allumer, ni
  changer de source.
- **Cache disque** - réponses TMDB et liens de sources résolus mis en cache.
- **Mises à jour intégrées** - vérification périodique des releases GitHub : bandeau sur
  l'accueil, pastille discrète pendant la lecture.

## Feuille de route

- **Cast vers une télé (Google Cast)** - à l'étude, pour les utilisateurs mobiles
  qui n'ont pas d'Android TV. Ce n'est pas qu'une affaire de SDK : un Chromecast
  va chercher le flux lui-même, or presque aucune source ne répond sans un
  `Referer` et un `User-Agent` que le récepteur ne sait pas envoyer. La forme
  viable est un petit proxy HTTP sur le téléphone qui les réattache - ce qui
  garde au passage le DNS-over-HTTPS dans la boucle, là où un Chromecast
  passerait par le résolveur de la box et se heurterait au blocage même que
  l'app existe pour contourner. Une question décide si tout cela tient debout,
  et elle n'est pas tranchée : donc pas de date.
- **Pas de support iOS prévu.** Les contraintes sont lourdes - installation hors
  store, règles de l'App Store, pile de lecture à refaire - et je n'ai aucun
  appareil Apple pour tester. Publier quelque chose que je ne peux pas faire
  tourner moi-même serait pire que de ne pas le publier.

## Stack

| Couche | Techno |
|---|---|
| Langage / build | Kotlin 2.0, Kotlin Multiplatform (`androidMain` / `desktopMain` / `jvmCommon` partagé) |
| UI | Compose Multiplatform, design system partagé (`MoovieButton`, rails, dialogues) |
| Lecture | Media3 / ExoPlayer sur Android · libmpv sur desktop |
| Réseau | Retrofit + OkHttp + kotlinx.serialization, DNS-over-HTTPS |
| Extraction | OkHttp + Jsoup + crypto Java (déobfuscation packer, AES) |
| Persistance | DataStore Preferences, cache disque OkHttp |
| Images | Coil 3 |
| CI | GitHub Actions : un tag `vX.Y.Z` produit l'APK signé, l'AppImage, le `.msi` et le `.dmg` |

## Installation

Récupérer le dernier build depuis les
[Releases](https://github.com/JibayMcs/MooVie/releases) :

- **Android TV et mobile** - sideload de `moovie-vX.Y.Z.apk` (un seul APK pour les deux)
- **Linux** - `moovie-vX.Y.Z-x86_64.AppImage` (`chmod +x` puis lancer - ni installation ni root)
- **Windows** - `moovie-vX.Y.Z.msi`
- **macOS** - `moovie-vX.Y.Z.dmg`

Les trois paquets desktop embarquent leur runtime Java **et leur lecteur vidéo**
(libmpv) : plus rien à installer à côté, sur aucune plateforme. L'AppImage Linux
tourne sur n'importe quelle distribution (vérifié sur Ubuntu 22.04/24.04, Debian 12
et Arch) et se met à jour depuis l'app. Sous **Windows**, le `.msi` s'installe par
utilisateur - sans droits administrateur - et ajoute des raccourcis au menu Démarrer
et au bureau ; le bandeau intégré le met alors à jour en place. Sous **macOS**, le
bandeau ouvre la page de release (le `.dmg` s'installe à la main).

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

**Les sous-titres fonctionnent tels quels dans les versions publiées** - l'APK,
l'AppImage, le MSI et le DMG des [Releases](https://github.com/JibayMcs/MooVie/releases)
embarquent ce qu'il faut. Rien à créer, rien à coller. En option, connecter un compte
OpenSubtitles dans les réglages relève la limite quotidienne de téléchargements et
affiche le quota restant.

Le paragraphe qui suit ne concerne que ceux qui **compilent depuis les sources**. Les
sous-titres reposent sur une *clé de consumer* OpenSubtitles, qui identifie
l'application ; OpenSubtitles impose une clé unique par application et bannit les comptes
qui demandent la leur à leurs utilisateurs. Cette clé est injectée à la compilation et
volontairement absente de ce dépôt : une compilation depuis les sources désactive donc
simplement les sous-titres - le reste fonctionne. Pour les activer en développement, crée
ton propre consumer sur [opensubtitles.com](https://www.opensubtitles.com/consumers) et
dépose la clé dans un fichier `opensubtitles.properties` gitignoré, à la racine :

```properties
apiKey=TA_CLE_DE_CONSUMER
```

La variable d'environnement `OPENSUBTITLES_API_KEY` marche aussi, c'est ce qu'utilise la CI.

## Contribuer

**Les contributions sont libres et souhaitées.** Ce sont les versions desktop qui ont
le plus besoin de regards : la CI les produit pour Linux, Windows et macOS, mais
chacune atterrit sur un matériel, des pilotes et une pile audio que personne ici ne
peut reproduire. Tester l'une d'elles sur ta machine est un vrai coup de main.

Si quoi que ce soit se passe mal - une version qui ne démarre pas, un flux qui passe
sur Android TV mais pas sur desktop, un contrôle que la télécommande n'atteint pas -
[ouvre une issue](https://github.com/JibayMcs/MooVie/issues). Ce qui aide le plus :

- ta plateforme et sa version (distribution, build Windows/macOS, modèle de box TV)
- la version de l'app, dans **Réglages → Mises à jour**
- ce que tu attendais, et ce qui s'est produit à la place
- pour un problème de lecture : le titre, et la source choisie dans le panneau

Les pull requests sont bienvenues aussi - un nouveau catalogue de sources ou un
extracteur d'hébergeur est le plus utile, chaque lien mort coûtant un titre à
quelqu'un. `.claude/skills/add-source/` explique comment on en écrit un et, surtout,
comment *mesurer* s'il mérite sa place.

## Crédits

Moo-vie repose sur le travail d'autres personnes.

**Données & services**

- **[TMDB](https://www.themoviedb.org)** - tous les titres, synopsis, affiches,
  images de fond, castings et notes de l'app viennent de The Movie Database. C'est
  ce qui fait du catalogue un catalogue plutôt qu'une liste de noms de fichiers, et
  l'app ne sert à rien sans ta propre clé d'API.
  *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- **[TheIntroDB](https://theintrodb.org)** - horodatages d'intro et de générique
  alimentés par la communauté, derrière les boutons *Passer l'intro* / *Passer le
  générique* et l'enchaînement de l'épisode suivant.
- **[Cloudflare](https://1.1.1.1) et [Quad9](https://quad9.net)** - les résolveurs
  DNS-over-HTTPS proposés au choix, ce qui permet aux sources de rester joignables
  sur les réseaux où ces domaines sont bloqués au niveau DNS.

**Open source**

- [Kotlin](https://kotlinlang.org), [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html),
  [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) et
  [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) - JetBrains
- [Media3 / ExoPlayer](https://developer.android.com/media/media3) et
  [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) - Google / AndroidX
- [mpv / libmpv](https://mpv.io) - le projet mpv, qui assure la lecture vidéo sur
  les versions desktop, et [FFmpeg](https://ffmpeg.org) dont il tire son décodage
- [OkHttp et Retrofit](https://square.github.io/okhttp/) - Square
- [jsoup](https://jsoup.org) - analyse les pages dont les sources sont extraites
- [Coil](https://coil-kt.github.io/coil/) - chargement des images et cache disque

## Licence

Open source, pour un usage personnel. Moo-vie n'héberge aucun contenu : elle ne fait que
résoudre des liens déjà publiquement accessibles.
