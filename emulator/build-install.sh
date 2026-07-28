#!/usr/bin/env bash
# Build debug + (ré)install + lancement sur l'émulateur en cours.
# C'est la boucle de test : modifie le code -> ./build-install.sh -> regarde l'écran.
set -euo pipefail
source "$(dirname "$0")/env.sh"

echo ">> Build debug (gradlew assembleDebug)..."
( cd "$PROJECT_DIR" && JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}" ./gradlew assembleDebug )

[[ -f "$APK_PATH" ]] || { echo "APK introuvable: $APK_PATH" >&2; exit 1; }

echo ">> Attente d'un device..."
"$ADB" wait-for-device

echo ">> Installation ($APK_PATH)..."
"$ADB" install -r -g "$APK_PATH"

echo ">> Lancement de $APK_ACTIVITY..."
"$ADB" shell am start -n "$APK_ACTIVITY" >/dev/null \
  || "$ADB" shell monkey -p "$APK_PACKAGE" -c android.intent.category.LEANBACK_LAUNCHER 1
echo ">> Fait. $APK_PACKAGE lancé."
