#!/usr/bin/env bash
# Arrête l'émulateur Android TV.
set -euo pipefail
source "$(dirname "$0")/env.sh"
PIDFILE="$EMU_DIR/emulator.pid"

echo ">> Arrêt de l'émulateur..."
"$ADB" emu kill 2>/dev/null || true
if [[ -f "$PIDFILE" ]]; then
  kill "$(cat "$PIDFILE")" 2>/dev/null || true
  rm -f "$PIDFILE"
fi
pkill -f "$AVD_NAME" 2>/dev/null || true
echo ">> Arrêté."
