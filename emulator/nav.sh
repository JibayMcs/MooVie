#!/usr/bin/env bash
# Télécommande par terminal — pilote l'app via adb, indépendamment du clavier/
# souris de la fenêtre émulateur (utile si l'input hôte ne passe pas, ex. DISPLAY
# distant). Envoie des événements D-pad à l'app.
#
# Usage : ./nav.sh <touche> [répétitions]
#   touches : up | down | left | right | ok | back | home
#   ex : ./nav.sh down 3   (3 fois bas)   ./nav.sh ok   (valider)
set -euo pipefail
source "$(dirname "$0")/env.sh"

case "${1:-}" in
  up)            K=19 ;;
  down)          K=20 ;;
  left)          K=21 ;;
  right)         K=22 ;;
  ok|center|enter) K=23 ;;
  back)          K=4 ;;
  home)          K=3 ;;
  *) echo "usage: ./nav.sh up|down|left|right|ok|back|home [répétitions]"; exit 1 ;;
esac

N="${2:-1}"
for _ in $(seq 1 "$N"); do
  "$ADB" shell input keyevent "$K"
  sleep 0.3
done
echo ">> ${1} x${N}"
