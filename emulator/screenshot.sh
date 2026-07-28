#!/usr/bin/env bash
# Capture l'écran de l'émulateur dans un PNG (défaut: ./shot.png).
set -euo pipefail
source "$(dirname "$0")/env.sh"
OUT="${1:-$EMU_DIR/shot.png}"
"$ADB" exec-out screencap -p > "$OUT"
echo ">> Capture: $OUT"
