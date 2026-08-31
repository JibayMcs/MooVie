# Moo-vie sur iPhone et iPad

🇬🇧 [English version](ios.md) · ↩ [README](../README.fr.md)

Moo-vie tourne sur iOS depuis la même base de code Kotlin Multiplatform que les
autres plateformes : les écrans que vous voyez sont ceux d'Android, pas une
version parallèle. Ce qui change tient à ce qu'Apple impose, et cette page ne
parle que de ça — installer, régler, mettre à jour.

> [!IMPORTANT]
> **Moo-vie n'est pas sur l'App Store et n'y sera pas.** L'application extrait
> ses sources depuis des sites tiers, ce que les règles de l'App Store
> n'autorisent pas. L'installation passe donc par le *sideload*, décrit ci-dessous.
> C'est légal — Apple prévoit ce chemin pour les développeurs — mais il a ses
> contraintes, et la principale est un **certificat qui expire tous les 7 jours**.

**Il vous faut :** un iPhone ou un iPad sous **iOS 15 ou plus récent**, un
identifiant Apple (le vôtre suffit, aucun compte développeur payant n'est
nécessaire), et une connexion Wi-Fi.

---

## 1. Installer SideStore

[SideStore](https://sidestore.io) est l'outil qui installe et surtout
**re-signe** l'application. Cette seconde partie est l'essentiel : Apple exige
qu'une application installée hors store porte une signature valide, et celle
d'un identifiant Apple gratuit ne vaut que sept jours. SideStore renouvelle
cette signature tout seul tant qu'il tourne sur le même réseau que votre
ordinateur — c'est pour cela qu'on l'utilise plutôt qu'une installation manuelle.

Suivez le [guide officiel d'installation](https://docs.sidestore.io/docs/installation/).
Il demande de préparer un fichier de paire (*pairing file*) depuis un ordinateur,
puis d'installer SideStore lui-même. Comptez une vingtaine de minutes la
première fois ; ensuite vous n'y revenez plus.

> [!TIP]
> AltStore fonctionne aussi : SideStore en est un fork et lit le même format de
> source. Si vous l'utilisez déjà, gardez-le — les étapes qui suivent sont
> identiques.

## 2. Ajouter la source Moo-vie

Plutôt que de télécharger un fichier à chaque version, **ajoutez la source une
fois** : SideStore ira ensuite chercher les mises à jour tout seul.

1. Ouvrez SideStore → onglet **Sources** → **+** en haut à droite.
2. Collez cette adresse :

   ```
   https://github.com/JibayMcs/MooVie/releases/latest/download/sidestore.json
   ```

3. Validez. **Moo-vie** apparaît dans la liste des sources.
4. Ouvrez-la et appuyez sur **Installer** (*Free* / *Get*).

Cette adresse est **stable** : GitHub la fait toujours pointer vers la dernière
version publiée, et le fichier voyage avec le `.ipa` qu'il décrit. Vous n'aurez
jamais à la changer.

<details>
<summary>Installer un <code>.ipa</code> à la main (déconseillé)</summary>

Vous pouvez aussi télécharger `moovie-vX.Y.Z.ipa` depuis la page
[Releases](https://github.com/JibayMcs/MooVie/releases) et l'ouvrir dans
SideStore via **My Apps → +**.

C'est déconseillé pour une raison précise : SideStore ne saura pas qu'une
nouvelle version existe, et vous devrez répéter l'opération à chaque fois. La
source de l'étape 2 rend la mise à jour automatique.
</details>

## 3. Faire confiance au certificat

Au premier lancement, iOS peut refuser d'ouvrir l'application. C'est attendu :
il ne connaît pas encore le certificat de votre identifiant Apple.

**Réglages → Général → VPN et gestion de l'appareil → votre identifiant Apple →
Faire confiance.**

Une seule fois, sauf changement de compte.

## 4. Régler l'application

Au premier lancement, Moo-vie demande une **clé API TMDB**. Elle est gratuite et
sert à récupérer les titres, les affiches et les résumés — sans elle, l'accueil
n'a rien à afficher.

1. Créez un compte sur [themoviedb.org](https://www.themoviedb.org/signup).
2. Demandez une clé dans [Paramètres → API](https://www.themoviedb.org/settings/api)
   (choisissez *Developer*, l'usage personnel est accepté).
3. Copiez la **clé API (v3 auth)** et collez-la dans Moo-vie :
   **Réglages → API & Clés → Clé TMDB**.

L'accueil se remplit dès la clé enregistrée.

**Rien d'autre n'est obligatoire.** Les sous-titres fonctionnent tels quels dans
les versions publiées. Deux réglages valent tout de même le détour :

- **Réglages → Sous-titres** — connecter un compte OpenSubtitles relève la
  limite quotidienne de téléchargements et affiche le quota restant.
- **Réglages → DNS** — activer DNS-over-HTTPS si votre opérateur bloque les
  domaines des hébergeurs. C'est le cas de plusieurs fournisseurs français.

## 5. Les mises à jour

**C'est l'endroit où iOS diffère vraiment des autres plateformes.**

Sur Android et sur desktop, Moo-vie se met à jour toute seule : une bannière
apparaît dans l'application et l'installation se fait sur place. Sur iOS, c'est
impossible — une application iOS ne peut pas s'installer une nouvelle version
d'elle-même, c'est verrouillé par le système et non une API qui manquerait. La
section « Mise à jour » des réglages est donc **absente** ici, plutôt que
d'offrir un bouton incapable de tenir sa promesse.

À la place, **c'est SideStore qui surveille**, grâce à la source ajoutée à
l'étape 2. À chaque nouvelle release publiée sur GitHub, le fichier
`sidestore.json` est régénéré et attaché à cette release ; l'adresse
`releases/latest/download/…` pointe alors dessus sans que rien ne change de
votre côté.

Ce que vous avez à faire :

1. Ouvrez SideStore. Il rafraîchit ses sources tout seul, et vous pouvez le
   forcer en tirant la liste vers le bas depuis l'onglet **Browse**.
2. Une pastille apparaît sur **My Apps** quand une version plus récente existe.
3. Appuyez sur **Update** en face de Moo-vie.

Vos réglages, votre historique, votre liste à voir et vos téléchargements sont
conservés : c'est une mise à jour de l'application, pas une réinstallation.

> [!NOTE]
> La source annonce la version *et* le numéro de build, précisément ceux que
> l'application déclare, plus l'empreinte SHA-256 du fichier. C'est ce qui permet
> à SideStore de savoir s'il a réellement du neuf — et non de reproposer
> éternellement la même chose — et de refuser un téléchargement corrompu au lieu
> d'installer un fichier tronqué.

### Le renouvellement des 7 jours

Distinct de la mise à jour, et souvent confondu avec elle. La signature d'un
identifiant Apple gratuit expire au bout de **7 jours** ; passé ce délai,
l'application ne s'ouvre plus. Elle n'est pas cassée et vos données sont
intactes : c'est la signature qu'il faut refaire.

SideStore s'en charge tout seul, à deux conditions : qu'il tourne en arrière-plan
et qu'il puisse joindre son service de rafraîchissement. En pratique, ouvrez
SideStore de temps en temps et laissez-le sur l'onglet **My Apps** ; il
renouvelle ce qui approche de l'échéance.

> [!TIP]
> Si l'application refuse de s'ouvrir après une semaine d'absence : ouvrez
> SideStore, appuyez sur **Refresh All**, attendez la fin, puis relancez Moo-vie.
> C'est presque toujours ça.

---

## Ce qui diffère d'Android, et pourquoi

Tout n'a pas pu être porté, et les manques ci-dessous sont des choix assumés,
pas des oublis.

| | iOS | Pourquoi |
|---|---|---|
| **Mise à jour intégrée** | absente | Une app iOS ne peut pas s'installer elle-même. SideStore tient ce rôle. |
| **Diffusion vers une télé (Cast)** | absente | Le rôle d'émetteur repose sur une découverte réseau et un serveur HTTP local, écartés du portage. |
| **Télécommande / appairage** | absent | Même raison : ce sont des rôles de salon, et le téléviseur est un appareil Android. |
| **Langue de l'interface** | suit le système | iOS expose ce réglage hors de l'application — Réglages → Moo-vie → Langue préférée. Le doubler donnerait deux endroits pour une seule question. |
| **Orientation** | portrait, sauf le lecteur | Le lecteur et la bande-annonce plein écran basculent en paysage d'eux-mêmes, comme sur le téléphone Android. |

Le reste est là : accueil, catalogue, recherche, découverte, fiches, saisons et
épisodes, historique et statistiques, liste à voir, téléchargements hors ligne,
sous-titres, sauvegarde et synchronisation chiffrée, profils.

## Quand quelque chose ne va pas

**L'application se ferme dès l'ouverture.** La signature a expiré : ouvrez
SideStore et faites **Refresh All**. Si elle se ferme encore juste après une
installation, c'est un défaut de la version — ouvrez une
[issue](https://github.com/JibayMcs/MooVie/issues) avec le journal de plantage
(**Réglages → Confidentialité et sécurité → Analyse et améliorations → Données
d'analyse**, cherchez `Moo-vie`).

**L'accueil reste vide.** La clé TMDB manque ou n'est pas valide. Vérifiez-la
dans **Réglages → API & Clés** ; c'est la clé *v3 auth*, pas le jeton de lecture.

**Aucune source ne se lit.** Essayez d'activer DNS-over-HTTPS dans
**Réglages → DNS**. Plusieurs opérateurs bloquent les domaines des hébergeurs au
niveau du résolveur.

**SideStore refuse d'installer : « maximum number of apps ».** Un identifiant
Apple gratuit ne permet que **3 applications** signées à la fois. Retirez-en une
depuis **My Apps**.

## Compiler soi-même

Il faut un Mac : la chaîne de compilation Kotlin/Native pour les cibles Apple
n'existe que sur macOS. Sur Linux et Windows, Gradle configure ces cibles sans
broncher mais leurs tâches sont inexécutables — la vérification appartient donc
au runner macOS de la CI (`.github/workflows/ci-ios.yml`).

```bash
brew install xcodegen
cd iosApp && xcodegen generate      # le .xcodeproj n'est pas versionné
open Moovie.xcodeproj               # puis Cmd+R
```

Le projet Xcode se **génère** depuis `iosApp/project.yml` plutôt que d'être
versionné : un `.pbxproj` est un graphe d'objets à identifiants opaques,
illisible en revue et qui produit un conflit de fusion à chaque ajout de
fichier. La spec dit la même chose en quarante lignes qu'on peut relire.

La compilation du framework Kotlin est déclenchée par Xcode lui-même, en phase
préalable. Pour ne vérifier que le code partagé, sans passer par Xcode :

```bash
./gradlew :app:compileIosMainKotlinMetadata   # code commun + iosMain
./gradlew :app:linkDebugFrameworkIosArm64     # cible appareil, éprouve le cinterop
```
