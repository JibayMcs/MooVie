# Reprise — refonte des fiches film/série (TV + desktop)

Note de passation. Travail **non commité**, sur `main`. Cible : Android TV et
desktop **uniquement** (`!compact`). Le tactile garde sa mise en page actuelle.

Maquettes de référence : `/home/hugo/Téléchargements/azdzdz.png` (série),
`fezfzef.png` (film), et la capture myCanal « Lioness » — c'est celle-ci qui
fait foi pour le hero.

## Fait, et vu à l'écran sur desktop

### Hero

- `DetailsHero.kt` : image à fond perdu, deux dégradés, titre + méta + actions à
  gauche, synopsis + « Création/Réalisation / Casting / Pays » à droite.
- Hauteur = **hauteur visible − barre d'onglets − amorce**, et non plus un
  pourcentage : l'image descend jusqu'en bas, la barre se pose dessous, et
  l'amorce (`AMORCE_SOUS_ONGLETS`) découvre le haut de ce que la barre ouvre.
  Plancher à 300 dp.
- **Marge propre au hero** (`margeHero`, ~9 % de la largeur) au lieu des 48 dp
  de la page : tout le contenu était tassé sur le premier tiers. La barre
  d'onglets et le contenu des onglets s'alignent dessus.
- Les deux colonnes du hero se partagent la largeur par **poids** (0,34 / 0,66)
  au lieu d'être bornées à 520 / 640 dp.
- **Plus d'affiche 2:3 par-dessus** : la maquette n'en montre pas, et le
  backdrop est déjà l'image du titre. Elle ne reste que comme filet quand le
  backdrop manque.
- Dégradés recalés : les anciens paliers (0,55 → 0xAA) noircissaient la moitié
  d'une image de 1 400 dp. Ils ne mordent plus que sur le dernier tiers.
- Fond flouté de page et voile supprimés sous le hero plein cadre.

### Bande-annonce dans le hero

Elle joue **dans le cadre du hero**, avec trois commandes en haut à droite :
agrandir, couper le son, pause (`ApercuControles`).

**Attention si tu y retouches** : le lecteur n'a qu'**un seul site d'appel**,
au niveau de la racine, et c'est son cadre qu'on déplace (`Modifier.height` +
`translationY` suivant `pageScroll`). `DetailsHero` reçoit `imageMasquee` et
laisse alors son cadre transparent. Deux appels — un dans le hero, un en plein
écran — feraient naître deux lecteurs mpv sur le même manifeste googlevideo,
qui répond 403 à la seconde demande. Un `movableContentOf` aurait dû résoudre
ça proprement : **essayé, il plante** (« Cannot insert LayoutNode … already has
a parent »), les deux sites ne s'apparient pas parce que l'un est un paramètre
différé de `DetailsHero`.

### Bouton principal

- Film : « ▶ LIRE » seul sur sa ligne, occupant la colonne de gauche (borné à
  `LARGEUR_MAX_BOUTON_PRINCIPAL`), fond `MOOVIE_ACCENT`, libellé **centré, gras,
  en capitales** (`LibellePrincipal`). Actions secondaires (Sources, cast,
  téléchargement, vu, watchlist) sur la ligne en dessous. C'est le « S'ABONNER »
  de la maquette.
- Série : **nouveau** bouton « Lire · S3E1 » / « Reprendre · S3E1 », qui lance
  l'épisode déduit de l'historique. Il n'y en avait aucun jusque-là.

### Barre d'onglets

`DetailsTabs.kt` : ÉPISODES (séries) · À VOIR AUSSI · BANDES-ANNONCES · EN
SAVOIR PLUS. **Une seule marque de sélection** : `MoovieButton(selected = …)`,
qui trace le trait du thème. Un second trait dessiné par-dessus donnait deux
effets superposés et rendait indiscernables survolé / focalisé / actif. Onglets **omis quand ils seraient
vides**. Les deux icônes du bandeau haut (bande-annonce, à propos) ont disparu
sur grand écran — elles sont devenues des onglets. Elles restent au doigt.

- `À VOIR AUSSI` : `DetailsViewModel.recommendations`, alimenté par
  `TmdbRepository.recommendations`. Câblé desktop + Android.
- `BANDES-ANNONCES` : une seule vignette, celle qu'on sait jouable (choix
  d'Hugo). Les autres vidéos TMDB sont déclarées, pas résolues.
- `EN SAVOIR PLUS` : `MovieInfoPanel` / `TvInfoPanel` + le casting, qui n'a plus
  de place à demeure sous un hero plein cadre.

### Fiche série

Choix d'Hugo : **la page défile en entier**. `SeriesPanes` ne sert plus qu'au
tactile ; sur grand écran c'est hero → onglets → liste d'épisodes dans un même
`verticalScroll`. Les épisodes sont composés d'un coup (≤ 25 par saison) : une
liste paresseuse n'est pas mesurable dans un défilement vertical.

Conséquence assumée : le focus n'atterrit plus sur l'épisode à reprendre mais
sur le bouton « Reprendre · S3E1 » du hero, qui fait le même geste.

`SeriesPanes` est devenu **tactile seulement** : son second cas (deux volets,
en-tête fixe) n'était plus atteignable, et son paramètre `compact` non plus.

Le replacement du focus à l'arrivée est calé sur `state.titleKey()` et non sur
`state` : changer de saison change l'état, reprenait le focus, et
`primaryModifier` ramenait la page tout en haut — on quittait la liste qu'on
venait d'ouvrir.

## Le tactile ne bouge pas

Tout ce qui précède est sous `!compact`. Les chemins tactiles ont été relus un
par un : en-tête, rangée d'actions unique, `SeriesPanes`, casting en queue de
liste, icônes en haut à droite, marges — inchangés. Le seul ajout qui aurait
débordé (le bouton Cast dans les actions de série) est explicitement borné à
`!compact`.

## À vérifier / à finir

1. **Le retour au haut de page au changement de saison** est corrigé à la
   cause, mais je n'ai pas pu le vérifier au clic : pas d'outil de saisie
   (`xdotool`) sur cette machine, les captures se font à la volée.
2. **Android TV : non testé.** C'est le point qui manque. Ce qui a changé et qui
   touche au D-pad : le focus d'arrivée sur la fiche série, la descente vers la
   barre d'onglets, la disparition des deux icônes en haut à droite, et les
   commandes de la bande-annonce dans le coin du hero (qui ont un
   `onPreviewKeyEvent` Bas → bouton principal, comme la rangée qu'elles
   remplacent). À faire :
   `cd emulator && MOOVIE_AVD=tv36 ./start.sh --headless`, `./build-install.sh`,
   `./nav.sh <touche>`, `./screenshot.sh`. L'émulateur demande sa propre clé
   TMDB.
3. **iOS non câblé** : `recommendations` / `onOpenTitle` gardent leurs valeurs
   par défaut dans `iosMain/Screens.kt`. Sans effet — iOS est tactile, la barre
   d'onglets n'y est pas rendue — mais à faire si la fiche tactile évolue.
4. **`aside` de `DetailsHero` reste inutilisé.** La maquette a un bouton « Plus
   d'infos » en bas à droite ; j'ai préféré l'onglet, pour ne pas offrir deux
   chemins vers le même panneau. À trancher.
5. **Crochet de dev** `MOOVIE_TEST_DETAILS=movie:550` / `tv:1396` (desktop,
   `Main.kt`) : à garder ou retirer avant PR. Note : avec le démon Gradle la
   variable n'est pas toujours reprise — lancer avec `--no-daemon`.

## Vérification

```bash
MOOVIE_TEST_DETAILS=movie:550 ./gradlew --no-daemon :app:run   # film
MOOVIE_TEST_DETAILS=tv:1396   ./gradlew --no-daemon :app:run   # série
./gradlew :app:compileKotlinDesktop
ANDROID_HOME=/home/hugo/Android/Sdk ./gradlew :app:compileDebugKotlinAndroid
```
