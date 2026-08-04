#!/usr/bin/env bash
# Prépare un émulateur : installe l'image système si absente, crée l'AVD, écrit
# sa configuration matérielle. À lancer UNE fois par profil.
# L'image système reste dans le SDK partagé (~/Android/Sdk).
#
#   ./setup.sh                    # profil par défaut (mibox) — Mi Box 4, Android 9
#   MOOVIE_AVD=tv36 ./setup.sh    # Android TV API 36
set -euo pipefail
source "$(dirname "$0")/env.sh"

echo ">> Profil '$MOOVIE_AVD' → AVD '$AVD_NAME' (${AVD_WIDTH}x${AVD_HEIGHT} @ ${AVD_DENSITY} dpi)"
mkdir -p "$ANDROID_AVD_HOME"

# 1) Image système (no-op si déjà installée)
if [[ ! -d "$SYSTEM_IMAGE_DIR" ]]; then
  echo ">> Installation de l'image système $SYSTEM_IMAGE..."
  # `yes` reçoit un SIGPIPE dès que sdkmanager rend la main : sous `pipefail`,
  # le pipeline sort en 141 alors que l'installation a parfaitement réussi, et
  # `set -e` coupait le script avant même la création de l'AVD. On juge donc sur
  # le résultat — l'image est là, ou elle ne l'est pas.
  yes | "$SDKMANAGER" "$SYSTEM_IMAGE" || true
  if [[ ! -d "$SYSTEM_IMAGE_DIR" ]]; then
    echo "!! Installation de $SYSTEM_IMAGE échouée." >&2
    exit 1
  fi
else
  echo ">> Image système déjà présente."
fi

# 2) AVD (créé s'il n'existe pas)
if "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: $AVD_NAME"; then
  echo ">> AVD '$AVD_NAME' déjà existant."
else
  echo ">> Création de l'AVD '$AVD_NAME'..."
  # Le repli sans --device couvre les profils absents du SDK installé (tv_4k
  # n'existe pas partout) : la résolution est de toute façon réécrite plus bas.
  echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" \
    --device "$AVD_DEVICE" --force \
    || echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" --force
fi

# 3) Configuration matérielle.
#
# Écrite ici plutôt que laissée au profil de départ : `--device` ne fixe ni la
# RAM, ni le nombre de cœurs, ni la densité — or c'est précisément la densité
# qui décide de ce qui rentre à l'écran. Un AVD dont on ne la maîtrise pas ne
# prouve rien sur la mise en page.
CFG="$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini"
if [[ ! -f "$CFG" ]]; then
  echo "!! config.ini introuvable ($CFG)" >&2
  exit 1
fi

set_cfg() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$CFG"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$CFG"
  else
    echo "${key}=${value}" >> "$CFG"
  fi
}

set_cfg "avd.ini.displayname"       "$AVD_NAME"
set_cfg "hw.lcd.width"              "$AVD_WIDTH"
set_cfg "hw.lcd.height"             "$AVD_HEIGHT"
set_cfg "hw.lcd.density"            "$AVD_DENSITY"
set_cfg "hw.initialOrientation"     "landscape"
set_cfg "hw.ramSize"                "$AVD_RAM"
set_cfg "hw.cpu.ncore"              "$AVD_CORES"
set_cfg "vm.heapSize"               "$AVD_HEAP"
set_cfg "disk.dataPartition.size"   "$AVD_DATA_SIZE"
# Clavier hôte transmis à l'émulateur (flèches = D-pad, Entrée = sélection,
# saisie de texte possible). Sans lui, le clavier physique peut ne pas piloter
# la navigation TV selon l'environnement.
set_cfg "hw.keyboard"               "yes"
set_cfg "hw.dPad"                   "yes"
set_cfg "hw.mainKeys"               "yes"
set_cfg "hw.screen"                 "no-touch"
set_cfg "hw.gpu.enabled"            "yes"
set_cfg "hw.gpu.mode"               "auto"
# Capteurs et périphériques qu'un boîtier TV n'a pas : les laisser actifs
# n'apporte rien et alourdit le démarrage.
set_cfg "hw.camera.back"            "none"
set_cfg "hw.camera.front"           "none"
set_cfg "hw.audioInput"             "no"
set_cfg "hw.gps"                    "no"
set_cfg "hw.accelerometer"          "no"
set_cfg "hw.gyroscope"              "no"
set_cfg "hw.sensors.proximity"      "no"
set_cfg "hw.sensors.magnetic_field" "no"
set_cfg "hw.sensors.orientation"    "no"
set_cfg "hw.battery"                "no"
set_cfg "hw.sdCard"                 "no"
set_cfg "runtime.network.latency"   "none"
set_cfg "runtime.network.speed"     "full"

echo ">> Prêt. Lance MOOVIE_AVD=$MOOVIE_AVD ./start.sh puis ./build-install.sh"
