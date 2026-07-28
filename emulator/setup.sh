#!/usr/bin/env bash
# Prépare l'émulateur : installe l'image système Android TV si absente, crée l'AVD.
# À lancer UNE fois. L'image système reste dans le SDK partagé (~/Android/Sdk).
set -euo pipefail
source "$(dirname "$0")/env.sh"

mkdir -p "$ANDROID_AVD_HOME"

# 1) Image système (no-op si déjà installée)
if [[ ! -d "$ANDROID_SDK_ROOT/system-images/android-36/android-tv/x86_64" ]]; then
  echo ">> Installation de l'image système $SYSTEM_IMAGE..."
  yes | "$SDKMANAGER" "$SYSTEM_IMAGE"
else
  echo ">> Image système déjà présente."
fi

# 2) AVD (créé s'il n'existe pas)
if "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  echo ">> AVD '$AVD_NAME' déjà existant."
else
  echo ">> Création de l'AVD '$AVD_NAME'..."
  echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" \
    --device "tv_1080p" --force \
    || echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" --force
fi

# Clavier hôte transmis directement à l'émulateur (flèches = D-pad, Entrée =
# sélection, saisie de texte possible). Sans ça, le clavier physique peut ne pas
# piloter la navigation TV selon l'environnement.
CFG="$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini"
if [ -f "$CFG" ]; then
  if grep -q "^hw.keyboard=" "$CFG"; then
    sed -i 's/^hw\.keyboard=.*/hw.keyboard=yes/' "$CFG"
  else
    echo "hw.keyboard=yes" >> "$CFG"
  fi
fi
echo ">> Prêt. Lance ./start.sh puis ./build-install.sh"
