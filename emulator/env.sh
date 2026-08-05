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
#   MOOVIE_AVD=nord3 ./start.sh      # OnePlus Nord 3 — téléphone de référence
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

# Valeurs communes, qu'un profil surcharge s'il en a besoin. Elles décrivent la
# box : RAM et stockage viennent de sa fiche produit — `df /data` a bien montré
# 4,9 Go utiles sur les 8 Go annoncés — les cœurs du quad-core Amlogic.
AVD_RAM=2048; AVD_CORES=4; AVD_HEAP=256; AVD_DATA_SIZE="8G"

# Volume **amovible**, sur tous les profils.
#
# C'est le seul moyen d'éprouver le chemin USB de la sauvegarde : sans lui,
# l'émulateur n'expose aucun support amovible et cette moitié de la
# fonctionnalité — celle qui sert justement à migrer d'un appareil à l'autre —
# ne se testait nulle part. Une carte SD et une clé USB se présentent
# identiquement à Android : un volume public monté sous /storage/<UUID>.
#
# 512 Mo suffisent : une sauvegarde pèse quelques kilo-octets, et l'image est
# créée une fois pour toutes à côté de l'AVD (dossier gitignoré).

# `tv` ou `phone` : décide de l'orientation de départ, de l'écran tactile, du
# D-pad et des capteurs. Un boîtier TV et un téléphone ne se prêtent pas le
# même matériel, et c'est précisément ce que ces bancs doivent reproduire.
AVD_FAMILY=tv

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
  # OnePlus Nord 3 5G (CPH2493), le téléphone d'un testeur. Relevé de sa fiche
  # produit, pas mesuré sur l'appareil — contrairement à `mibox`.
  #
  # **1240 px à 450 dpi font 441 dp de large**, contre 448 dp au Pixel 8 Pro :
  # sept points d'écart, et la même classe de taille (COMPACT, seuil à 600 dp).
  # Ce banc ne sert donc pas à éprouver une largeur inédite — il sert à éprouver
  # une largeur *serrée* sur un second appareil, là où une mise en page calculée
  # au dp près sur un seul téléphone finit par ne tenir que sur celui-là.
  nord3)
    AVD_FAMILY=phone
    AVD_NAME="OnePlus_Nord_3_CPH2493_API_36"
    SYSTEM_IMAGE="system-images;android-36;google_apis;x86_64"
    # Base téléphone quelconque : la géométrie est réécrite juste après, seule
    # la famille d'appareil compte ici.
    AVD_DEVICE="pixel_7"
    AVD_WIDTH=1240; AVD_HEIGHT=2772; AVD_DENSITY=450
    # RAM et stockage **ne suivent pas la fiche** (16 Go / 256 Go) : ils ne
    # changent rien à ce qu'on vient vérifier — la mise en page — et une VM de
    # 16 Go affamerait la machine hôte pour rien. La géométrie et la version
    # d'Android, elles, sont fidèles, et ce sont elles qui décident du rendu.
    AVD_RAM=4096; AVD_CORES=8; AVD_HEAP=512; AVD_DATA_SIZE="16G"
    ;;
  *)
    echo "!! Profil AVD inconnu : '$MOOVIE_AVD' (attendu : mibox, tv36, nord3)" >&2
    return 1 2>/dev/null || exit 1
    ;;
esac

# Chemin d'installation de l'image, déduit de son identifiant SDK — qui porte
# déjà le segment `system-images`, d'où l'absence de préfixe ici.
SYSTEM_IMAGE_DIR="$ANDROID_SDK_ROOT/$(echo "$SYSTEM_IMAGE" | tr ';' '/')"

export AVD_NAME SYSTEM_IMAGE SYSTEM_IMAGE_DIR AVD_DEVICE AVD_FAMILY
export AVD_WIDTH AVD_HEIGHT AVD_DENSITY AVD_RAM AVD_CORES AVD_HEAP AVD_DATA_SIZE
export AVD_SDCARD_SIZE="${AVD_SDCARD_SIZE:-512M}"

# App Moo-vie
export APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
export APK_PACKAGE="fr.moovie.tv"
export APK_ACTIVITY="fr.moovie.tv/.MainActivity"

# Ciblage device : adb refuse d'agir dès qu'il voit plus d'un appareil — un
# second émulateur, mais aussi un simple téléphone branché en USB. On fixe donc
# ANDROID_SERIAL sur l'émulateur dont l'AVD == $AVD_NAME (adb respecte cette var
# nativement, donc tous les appels $ADB ciblent le bon device).
#
# En fonction, et non en bloc joué une fois : `start.sh` en a besoin *après*
# avoir lancé l'émulateur, alors qu'au moment où il source ce fichier il n'y en
# a encore aucun à trouver.
moovie_resolve_serial() {
  [ -n "${ANDROID_SERIAL:-}" ] && return 0
  local _s _name
  for _s in $("$ADB" devices 2>/dev/null | awk '/emulator-/{print $1}'); do
    # Répond dès que la console de l'émulateur écoute, bien avant la fin du boot.
    _name="$("$ADB" -s "$_s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [ "$_name" = "$AVD_NAME" ]; then
      export ANDROID_SERIAL="$_s"
      return 0
    fi
  done
  return 1
}

# Attend que l'émulateur du profil apparaisse, puis le cible. Utilisé par
# `start.sh` juste après le lancement.
moovie_await_serial() {
  local _i
  for _i in $(seq 1 60); do
    moovie_resolve_serial && return 0
    sleep 2
  done
  echo "!! Émulateur '$AVD_NAME' introuvable après 2 min." >&2
  return 1
}

moovie_resolve_serial || true
