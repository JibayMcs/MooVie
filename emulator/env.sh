# Environnement partagé pour l'émulateur Android TV (sourcé par les autres scripts).
# Le SDK Android reste partagé (~/Android/Sdk) ; seul l'AVD vit dans ce dossier gitignoré.

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"

# Dossier de ce script (chemin absolu, robuste bash/zsh), surchargeable via EMU_DIR.
EMU_DIR="${EMU_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)}"
export EMU_DIR
# Racine du projet = dossier parent
PROJECT_DIR="$(cd "$EMU_DIR/.." && pwd)"
export PROJECT_DIR

# L'AVD (données de la VM) est stocké ici, pas dans ~/.android/avd
export ANDROID_AVD_HOME="$EMU_DIR/avd"

# Outils
export SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
export AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
export EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
export ADB="$(command -v adb || echo "$ANDROID_SDK_ROOT/platform-tools/adb")"

# ─── Profils d'AVD ──────────────────────────────────────────────────────────
#
# `MOOVIE_AVD` choisit la machine ciblée. **`mibox` est le défaut** : c'est la
# box réellement utilisée, donc le banc de test qui compte. Un bug qui n'existe
# que sur `tv36` n'atteindra personne ; l'inverse est faux.
#
#   ./start.sh                       # la box de référence
#   MOOVIE_AVD=tv36 ./start.sh       # Android TV récent (API 36), démarre plus vite
#
# `mibox` reproduit la **Xiaomi Mi Box 4** (nom de code « oneday »), relevée sur
# l'appareil lui-même le 4 août 2026 :
#
#   ro.product.model      MIBOX4
#   ro.build.version      Android 9 — API 28 (build PI.3933)
#   ro.hardware           amlogic
#   wm size / density     1920x1080 @ 320 dpi, sans overscan ni surcharge
#
# Un profil « Mi Box 3S / Android 6 » existait avant : il visait le mauvais
# appareil, deviné d'une fiche produit au lieu d'être mesuré. Supprimé.
#
# Pas de variante 4K : la box **sort** de la 4K mais son interface tourne en
# 1080p — `dumpsys display` ne liste que des modes 1920x1080. Une variante
# 3840x2160 n'aurait reproduit aucun appareil réel.
#
# Ce que ce profil ne reproduit pas : le SoC Amlogic et son GPU (image x86 alors
# que la box est en **armeabi-v7a**, 32 bits), le décodage matériel, le HDR et
# les DRM. Pour tout ça, seule la box physique fait foi.
export MOOVIE_AVD="${MOOVIE_AVD:-mibox}"

case "$MOOVIE_AVD" in
  tv36)
    AVD_NAME="moovie_androidtv_36"
    SYSTEM_IMAGE="system-images;android-36;android-tv;x86_64"
    AVD_DEVICE="tv_1080p"
    AVD_WIDTH=1920; AVD_HEIGHT=1080; AVD_DENSITY=320
    ;;
  mibox)
    AVD_NAME="Xiaomi_Mi_Box_4_API_28"
    SYSTEM_IMAGE="system-images;android-28;android-tv;x86"
    AVD_DEVICE="tv_1080p"
    AVD_WIDTH=1920; AVD_HEIGHT=1080; AVD_DENSITY=320
    ;;
  *)
    echo "!! Profil AVD inconnu : '$MOOVIE_AVD' (attendu : tv36, mibox)" >&2
    return 1 2>/dev/null || exit 1
    ;;
esac

# Communs aux profils : ce sont les caractéristiques de la box, et le profil
# tv36 s'en accommode très bien. RAM et stockage viennent de la fiche produit —
# `df /data` a bien montré 4,9 Go utiles sur les 8 Go annoncés — les cœurs du
# quad-core Amlogic.
AVD_RAM=2048; AVD_CORES=4; AVD_HEAP=256; AVD_DATA_SIZE="8G"

# Chemin d'installation de l'image, déduit de son identifiant SDK — qui porte
# déjà le segment `system-images`, d'où l'absence de préfixe ici.
SYSTEM_IMAGE_DIR="$ANDROID_SDK_ROOT/$(echo "$SYSTEM_IMAGE" | tr ';' '/')"

export AVD_NAME SYSTEM_IMAGE SYSTEM_IMAGE_DIR AVD_DEVICE
export AVD_WIDTH AVD_HEIGHT AVD_DENSITY AVD_RAM AVD_CORES AVD_HEAP AVD_DATA_SIZE

# App Moo-vie
export APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
export APK_PACKAGE="fr.moovie.tv"
export APK_ACTIVITY="fr.moovie.tv/.MainActivity"

# Ciblage device : si plusieurs émulateurs tournent, adb refuse sans -s. On fixe
# ANDROID_SERIAL sur l'émulateur dont l'AVD == $AVD_NAME (adb respecte cette var
# nativement, donc tous les appels $ADB ciblent le bon device).
if [ -z "${ANDROID_SERIAL:-}" ]; then
  for _s in $("$ADB" devices 2>/dev/null | awk '/emulator-/{print $1}'); do
    _name="$("$ADB" -s "$_s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [ "$_name" = "$AVD_NAME" ]; then export ANDROID_SERIAL="$_s"; break; fi
  done
  unset _s _name
fi
