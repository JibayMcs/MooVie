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

# Paramètres de l'AVD
export AVD_NAME="moovie_androidtv_36"
export SYSTEM_IMAGE="system-images;android-36;android-tv;x86_64"

# App Moo-vie
export APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
export APK_PACKAGE="fr.moovie.tv"
export APK_ACTIVITY="fr.moovie.tv/.MainActivity"
