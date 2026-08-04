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
# `MOOVIE_AVD` choisit la machine ciblée. `tv36` reste le défaut : c'est le banc
# de test quotidien, le plus rapide et le plus proche d'un Android TV récent.
#
#   MOOVIE_AVD=mibox ./start.sh      # Android 6, plancher réel de minSdk
#   MOOVIE_AVD=mibox4k ./setup.sh    # même Android, écran 4K
#
# Les deux profils `mibox` reproduisent la **Xiaomi Mi Box 3S** : Android 6.0
# (API 23), 2 Go de RAM, 4 cœurs, 8 Go de stockage. Ils existent parce que
# `minSdk = 23` n'était testé sur aucune machine — la seule en service tourne
# en API 36, seize versions plus haut.
#
# Ce qu'ils ne reproduisent pas : le SoC Amlogic et son GPU Mali (image x86,
# rendu par le GPU de l'hôte), le décodage matériel, le HDR10 et les DRM. Pour
# tout ça, seule la box physique fait foi.
export MOOVIE_AVD="${MOOVIE_AVD:-tv36}"

case "$MOOVIE_AVD" in
  tv36)
    AVD_NAME="moovie_androidtv_36"
    SYSTEM_IMAGE="system-images;android-36;android-tv;x86_64"
    AVD_DEVICE="tv_1080p"
    AVD_WIDTH=1920; AVD_HEIGHT=1080; AVD_DENSITY=320
    ;;
  mibox)
    AVD_NAME="Xiaomi_Mi_Box_3S_API_23"
    SYSTEM_IMAGE="system-images;android-23;android-tv;x86"
    AVD_DEVICE="tv_1080p"
    AVD_WIDTH=1920; AVD_HEIGHT=1080; AVD_DENSITY=320
    ;;
  mibox4k)
    # Même Android, écran 4K : sert à contrôler la mise en page et le choix des
    # ressources en xxxhdpi. La box sort bien de la 4K, mais ce profil ne dit
    # rien de son décodage vidéo — il ne fait qu'agrandir l'écran virtuel.
    AVD_NAME="Xiaomi_Mi_Box_3S_4K_API_23"
    SYSTEM_IMAGE="system-images;android-23;android-tv;x86"
    AVD_DEVICE="tv_4k"
    AVD_WIDTH=3840; AVD_HEIGHT=2160; AVD_DENSITY=640
    ;;
  *)
    echo "!! Profil AVD inconnu : '$MOOVIE_AVD' (attendu : tv36, mibox, mibox4k)" >&2
    return 1 2>/dev/null || exit 1
    ;;
esac

# Communs aux profils : ce sont les caractéristiques de la box, et le profil
# tv36 s'en accommode très bien.
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
