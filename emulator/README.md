# Émulateur Android TV — test local de Moo-vie

Environnement pour tester l'app sur Android TV, accéléré par KVM. Le SDK Android
reste partagé (`~/Android/Sdk`) ; seules les VM vivent ici, dans `avd/`
(gitignoré, ~1,8 Go par profil).

## Profils

`MOOVIE_AVD` choisit la machine. Tous les scripts le respectent, et `adb` cible
automatiquement le bon émulateur même si plusieurs tournent.

| `MOOVIE_AVD` | AVD | Android | Écran |
|---|---|---|---|
| `mibox` *(défaut)* | `Xiaomi_Mi_Box_4_API_28` | 9 (API 28), x86 | 1920×1080 @ 320 dpi |
| `tv36` | `moovie_androidtv_36` | 16 (API 36), x86_64 | 1920×1080 @ 320 dpi |

`mibox` reproduit la **box de référence du projet**, une Xiaomi Mi Box 4 (nom de
code « oneday »), et c'est le profil par défaut : un bug qui n'existe que sur
`tv36` n'atteindra personne, l'inverse est faux. `tv36` reste là pour vérifier le
comportement sur un Android TV récent — et il démarre plus vite.

Les caractéristiques de `mibox` ne sont pas devinées d'une fiche produit : elles
ont été relevées sur l'appareil, en adb, le 4 août 2026.

| Relevé | Valeur |
|---|---|
| `ro.product.model` | `MIBOX4` |
| `ro.product.device` / `board` | `oneday` |
| Android | 9 — API 28, build `PI.3933` |
| `ro.hardware` | `amlogic` |
| ABI | `armeabi-v7a` **uniquement** (32 bits) |
| `wm size` / `wm density` | 1920×1080 @ 320 dpi, aucun overscan, aucune surcharge |
| RAM / cœurs / stockage | 2 Go · 4 · 8 Go (4,9 Go utiles sur `/data`) |

**Ce que ce profil ne prouve pas** : le SoC Amlogic et son GPU (l'image est en
x86 alors que la box n'exécute que de l'`armeabi-v7a`), le décodage vidéo
matériel, le HDR, les DRM Widevine et la télécommande physique. Pour tout ça,
seule la box fait foi — `adb connect <ip>:5555` après avoir activé le débogage
réseau dans ses options développeur.

**Pas de variante 4K** : la box *sort* de la 4K, mais son interface tourne en
1080p — `dumpsys display` ne liste que des modes 1920×1080. Un profil 3840×2160
ne reproduirait aucun appareil réel, et à densité proportionnelle il donnerait de
toute façon la même largeur logique de 960 dp, donc le même agencement.

## Prérequis
- Android SDK + `cmdline-tools/latest` + `emulator` + `platform-tools`
- KVM accessible (`/dev/kvm`)
- JDK 17 (le build Gradle l'utilise)

## Utilisation

```bash
cd emulator

./setup.sh            # UNE fois PAR PROFIL : image système (si absente) + AVD
./start.sh            # démarre l'émulateur (fenêtre) et attend le boot
./start.sh --headless # sans fenêtre (utile en SSH / sans écran)

./build-install.sh    # boucle de test : build debug + install + lancement
./screenshot.sh       # capture l'écran -> shot.png

./stop.sh             # arrête l'émulateur
```

Sans rien préciser, ces commandes ciblent `mibox`. Pour l'autre profil, préfixer :

```bash
MOOVIE_AVD=tv36 ./setup.sh     # UNE fois
MOOVIE_AVD=tv36 ./start.sh --headless
MOOVIE_AVD=tv36 ./build-install.sh
```

Pour changer de défaut le temps d'une session, sans toucher au dépôt :

```bash
export MOOVIE_AVD=tv36
```

Les deux profils démarrent en quelques dizaines de secondes. `mibox` est un poil
plus lent (image x86 32 bits contre x86_64), sans que ça change la boucle de dev.

Boucle de dev : modifie le code → `./build-install.sh` → regarde l'écran (ou
`./screenshot.sh`). Au premier lancement de l'app, va dans **Réglages → API & Clés**
et colle ta clé TMDB pour peupler l'accueil.
