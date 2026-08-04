# Émulateur Android TV — test local de Moo-vie

Environnement pour tester l'app sur Android TV, accéléré par KVM. Le SDK Android
reste partagé (`~/Android/Sdk`) ; seules les VM vivent ici, dans `avd/`
(gitignoré, ~1,8 Go par profil).

## Profils

`MOOVIE_AVD` choisit la machine. Tous les scripts le respectent, et `adb` cible
automatiquement le bon émulateur même si plusieurs tournent.

| `MOOVIE_AVD` | AVD | Android | Écran |
|---|---|---|---|
| `tv36` *(défaut)* | `moovie_androidtv_36` | 16 (API 36), x86_64 | 1920×1080 @ 320 dpi |
| `mibox` | `Xiaomi_Mi_Box_3S_API_23` | 6.0 (API 23), x86 | 1920×1080 @ 320 dpi |
| `mibox4k` | `Xiaomi_Mi_Box_3S_4K_API_23` | 6.0 (API 23), x86 | 3840×2160 @ 640 dpi |

Les deux profils `mibox` reproduisent une **Xiaomi Mi Box 3S** : Android 6.0,
2 Go de RAM, 4 cœurs, 8 Go de stockage. Ils existent parce que `minSdk = 23`
n'était vérifié sur aucune machine — `tv36` tourne seize versions plus haut.

**Ce qu'ils ne prouvent pas** : le SoC Amlogic et son GPU Mali (image x86, rendu
par le GPU de l'hôte), le décodage vidéo matériel, le HDR10, les DRM Widevine L1
et la télécommande physique. Pour tout ça, seule la box fait foi.

**`mibox4k` ne teste pas la mise en page** : 3840/640 et 1920/320 donnent la même
largeur logique de 960 dp, donc exactement le même agencement. Il sert à vérifier
le choix des ressources `xxxhdpi` et le coût du rendu, pas ce qui rentre à l'écran.

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

Sur un autre profil, préfixer la commande :

```bash
MOOVIE_AVD=mibox ./setup.sh     # UNE fois
MOOVIE_AVD=mibox ./start.sh --headless
MOOVIE_AVD=mibox ./build-install.sh
```

Android 6 met **environ deux minutes** à démarrer, contre quelques dizaines de
secondes pour `tv36` : lance-le en tâche de fond plutôt que de l'attendre.

Boucle de dev : modifie le code → `./build-install.sh` → regarde l'écran (ou
`./screenshot.sh`). Au premier lancement de l'app, va dans **Réglages → API & Clés**
et colle ta clé TMDB pour peupler l'accueil.
