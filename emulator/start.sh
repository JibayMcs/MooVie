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
  `# Résolveurs explicites : sans eux, l'émulateur prend le premier de` \
  `# /etc/resolv.conf de l'hôte, souvent 127.0.0.1 (résolveur local), et tout` \
  `# nom échoue dès que ce service est arrêté, alors que ping par IP passe.` \
  -dns-server 1.1.1.1,8.8.8.8 \
  "${MODE_ARGS[@]}" >"$LOG" 2>&1 &
EMU_PID=$!
disown "$EMU_PID" 2>/dev/null || true
echo "$EMU_PID" > "$PIDFILE"
echo ">> PID émulateur: $EMU_PID (enregistré dans emulator.pid)"

echo ">> Attente du boot complet..."
# D'abord identifier *quel* device viser : sans ça, un téléphone branché en USB
# suffit à faire échouer chaque commande adb sur « more than one device ».
moovie_await_serial || exit 1
echo ">> Device ciblé: $ANDROID_SERIAL"
"$ADB" wait-for-device
until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  # abandon si le process est mort
  kill -0 "$EMU_PID" 2>/dev/null || { echo "!! L'émulateur s'est arrêté. Voir $LOG"; exit 1; }
  sleep 2
done
echo ">> $AVD_NAME démarré et prêt. L'émulateur continue de tourner après ce script."

# Après le boot, le Wi-Fi virtuel (profil mibox, Android 9) reste parfois « enabled »
# sans être associé : pas de réseau, et l'app affiche « hors ligne ». Un cycle
# disable/enable le relance. On tente au plus 3 cycles, sans faire échouer le script.
reseau_ok() {
  local sortie
  sortie="$("$ADB" shell ping -c 1 -W 3 1.1.1.1 2>/dev/null || true)"
  [[ "$sortie" == *" 0% packet loss"* ]]
}

echo ">> Vérification du réseau..."
reseau_ok_final=false
for essai in 1 2 3 4; do
  if reseau_ok; then
    reseau_ok_final=true
    break
  fi
  (( essai == 4 )) && break
  echo ">> Pas de réseau (essai $essai/3) : cycle Wi-Fi disable/enable..."
  "$ADB" shell svc wifi disable || true
  sleep 3
  "$ADB" shell svc wifi enable || true
  sleep 12
done

if $reseau_ok_final; then
  echo ">> Réseau OK (ping 1.1.1.1 répond)."
else
  echo "!! Réseau toujours injoignable après 3 cycles Wi-Fi. L'émulateur tourne, mais l'app"
  echo "   restera « hors ligne » : vérifie le Wi-Fi à la main (svc wifi enable via adb)."
fi
"$ADB" devices
