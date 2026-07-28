# Émulateur Android TV — test local de Moo-vie

Environnement **gitignoré** (l'AVD vit dans `avd/`, ~1.8 Go) pour tester l'app
sur **Android TV API 36, x86_64**, accéléré par KVM. Le SDK Android reste
partagé (`~/Android/Sdk`) ; seule la VM vit ici.

## Prérequis
- Android SDK + `cmdline-tools/latest` + `emulator` + `platform-tools`
- KVM accessible (`/dev/kvm`)
- JDK 17 (le build Gradle l'utilise)

## Utilisation

```bash
cd emulator

./setup.sh            # UNE fois : installe l'image système (si absente) + crée l'AVD
./start.sh            # démarre l'émulateur (fenêtre) et attend le boot
./start.sh --headless # sans fenêtre (utile en SSH / sans écran)

./build-install.sh    # boucle de test : build debug + install + lancement
./screenshot.sh       # capture l'écran -> shot.png

./stop.sh             # arrête l'émulateur
```

Boucle de dev : modifie le code → `./build-install.sh` → regarde l'écran (ou
`./screenshot.sh`). Au premier lancement de l'app, va dans **Réglages → API & Clés**
et colle ta clé TMDB pour peupler l'accueil.
