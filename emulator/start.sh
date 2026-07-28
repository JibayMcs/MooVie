#!/usr/bin/env bash
# Démarre l'émulateur Android TV (API 36) en tâche de fond DÉTACHÉE :
# l'émulateur survit à la fin de ce script (nohup + disown).
# Fenêtre par défaut ; --headless pour sans UI.
set -euo pipefail
source "$(dirname "$0")/env.sh"

LOG="$EMU_DIR/emulator.log"
PIDFILE="$EMU_DIR/emulator.pid"

# Déjà en cours ?
if [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  echo ">> Émulateur déjà lancé (PID $(cat "$PIDFILE")). Utilise ./stop.sh d'abord."
  exit 0
fi

MODE_ARGS=()
if [[ "${1:-}" == "--headless" ]]; then
  MODE_ARGS=(-no-window -no-audio)
  echo ">> Mode headless"
else
  echo ">> Mode fenêtré (DISPLAY=${DISPLAY:-none})"
fi

echo ">> Lancement de l'AVD '$AVD_NAME' (log: $LOG)..."
# nohup + redirection + disown : le process n'est PAS tué quand ce script se termine.
nohup "$EMULATOR" -avd "$AVD_NAME" \
  -gpu auto \
  -accel on \
  -no-boot-anim \
  -no-snapshot \
  "${MODE_ARGS[@]}" >"$LOG" 2>&1 &
EMU_PID=$!
disown "$EMU_PID" 2>/dev/null || true
echo "$EMU_PID" > "$PIDFILE"
echo ">> PID émulateur: $EMU_PID (enregistré dans emulator.pid)"

echo ">> Attente du boot complet..."
"$ADB" wait-for-device
until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  # abandon si le process est mort
  kill -0 "$EMU_PID" 2>/dev/null || { echo "!! L'émulateur s'est arrêté. Voir $LOG"; exit 1; }
  sleep 2
done
echo ">> Android TV démarré et prêt. L'émulateur continue de tourner après ce script."
"$ADB" devices
