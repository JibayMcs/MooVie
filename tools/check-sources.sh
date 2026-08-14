#!/usr/bin/env bash
# Contrôle de santé des sources — neuf titres, lancé à la main.
#
# Répond à « est-ce que tout marche encore ? », pas à « que couvre-t-on ? ».
# Pour la seconde, voir CoverageProbeTest (38 titres stratifiés).
#
#   ./tools/check-sources.sh              # relève, compare au précédent
#   ./tools/check-sources.sh --list       # historique des relevés
#
# Ce qui compte est la **comparaison** : « animesama 0 » ne veut rien dire seul,
# « animesama 9 puis 0 » désigne une panne. Les relevés sont donc conservés dans
# tools/reports/ (ignoré par git) et le script diffe automatiquement avec le
# dernier.
#
# Pourquoi ici et pas en CI : l'IP d'un runner GitHub n'est pas votre salon.
# Certains hébergeurs bloquent les plages de datacenter, et un provider peut
# sembler mort là-bas en marchant très bien chez vous. Lancé depuis la machine
# qui regarde vraiment, le relevé mesure la bonne connexion.
set -euo pipefail

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
RAPPORTS="$RACINE/tools/reports"
mkdir -p "$RAPPORTS"

if [[ "${1:-}" == "--list" ]]; then
  ls -1t "$RAPPORTS"/*.txt 2>/dev/null | head -20 || echo "aucun relevé"
  exit 0
fi

# Le relevé précédent, avant d'en écrire un nouveau.
PRECEDENT="$(ls -1t "$RAPPORTS"/*.txt 2>/dev/null | head -1 || true)"
COURANT="$RAPPORTS/$(date +%Y-%m-%d-%H%M).txt"

echo ">> Relevé en cours (neuf titres, comptez deux à trois minutes)…"
cd "$RACINE"
# `|| true` : la sonde n'échoue pas quand une source tombe — c'est le rapport
# qui le dit. Faire échouer Gradle masquerait le relevé derrière une pile.
./gradlew :app:desktopTest --tests "*QuickCoverageProbeTest*" \
  -Dmoovie.probe=1 --rerun-tasks 2>&1 |
  awk '/CONTRÔLE RAPIDE DES SOURCES/ {garde=1}
       garde && /^[[:space:]]*(Deprecated Gradle|BUILD |[0-9]+ actionable)/ {exit}
       garde {sub(/^[[:space:]]+/, ""); print}' > "$COURANT" || true

if [[ ! -s "$COURANT" ]]; then
  echo "!! Aucun relevé produit. Relancer à la main pour voir l'erreur :" >&2
  echo "   ./gradlew :app:desktopTest --tests '*QuickCoverageProbeTest*' -Dmoovie.probe=1 -i" >&2
  rm -f "$COURANT"
  exit 1
fi

cat "$COURANT"
echo
echo "   relevé écrit dans ${COURANT#"$RACINE"/}"

if [[ -z "$PRECEDENT" ]]; then
  echo
  echo ">> Premier relevé : rien à comparer. Le prochain dira ce qui a bougé."
  exit 0
fi

echo
echo "════════ CE QUI A CHANGÉ depuis $(basename "$PRECEDENT" .txt) ════════"

# Compare provider par provider. Un provider qui **tombe à zéro** alors qu'il
# rendait quelque chose est le signal qu'on guette : c'est exactement ce qu'a
# fait anime-sama en passant aux liens absolus.
ALERTE=0
while read -r nom avant; do
  maintenant="$(awk -v n="$nom" '$1=="PROVIDER" && $2==n {print $3}' "$COURANT")"
  [[ -z "$maintenant" ]] && continue
  if [[ "$maintenant" -eq 0 && "$avant" -gt 0 ]]; then
    echo "  ⛔ $nom : $avant → 0 titre — provider probablement mort"
    ALERTE=1
  elif [[ "$maintenant" -lt "$avant" ]]; then
    echo "  ⚠  $nom : $avant → $maintenant titres"
  elif [[ "$maintenant" -gt "$avant" ]]; then
    echo "  ✅ $nom : $avant → $maintenant titres"
  fi
done < <(awk '$1=="PROVIDER" {print $2, $3}' "$PRECEDENT")

AV="$(awk '$1=="COUVERTURE" {print $2}' "$PRECEDENT")"
MA="$(awk '$1=="COUVERTURE" {print $2}' "$COURANT")"
if [[ "$AV" != "$MA" ]]; then
  echo "  ▸ couverture : $AV → $MA"
  # La couverture globale seule ne suffit pas à alarmer : un hébergeur en
  # maintenance la fait baisser sans que rien ne soit cassé chez nous.
  [[ "${MA%%/*}" -lt "${AV%%/*}" ]] && echo "    (un hébergeur en panne suffit à l'expliquer — regarder les providers)"
fi

if [[ "$ALERTE" -eq 0 ]]; then
  echo "  (aucun provider effondré)"
else
  echo
  echo ">> Un provider est tombé à zéro. Avant de conclure : relancer une fois."
  echo "   Un site en maintenance le jour du relevé donne exactement cette trace."
fi
exit 0
