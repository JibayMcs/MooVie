#!/usr/bin/env bash
#
# Approvisionne une libmpv **portable** dans app/mpv-linux/, prête à être
# recopiée telle quelle dans l'AppImage par `bundleMpv`.
#
# Pourquoi ne pas prendre celle de la machine de build, comme avant ? Parce que
# la base la plus ancienne qu'on puisse viser décide de la version de mpv, et
# que cette version décide de la qualité de l'image. Ubuntu 22.04 sert mpv
# 0.34.1, qui ne sait pas convertir le HDR en SDR sur le chemin de rendu
# logiciel : toute source 4K arrive cramée, un ciel entier à 255. La 0.40 de
# Debian trixie corrige cela mais exige la glibc 2.38 — ce qui reviendrait à
# mettre 22.04 et Debian 12 sur le banc de touche pour réparer une image.
#
# conda-forge tranche le dilemme : ses paquets sont construits pour une glibc
# ancienne tout en suivant les versions amont. Mesuré sur mpv 0.41.0 :
#
#   | libmpv                | glibc de la fermeture | image 4K       |
#   |-----------------------|-----------------------|----------------|
#   | 0.34.1 (22.04, livrée)| 2.34                  | cramée (173)   |
#   | 0.40   (trixie)       | 2.38                  | correcte (84)  |
#   | 0.41.0 (conda-forge)  | 2.34                  | correcte (122) |
#
# Même plancher que ce qu'on livre déjà, donc aucune machine perdue en route.
#
# Usage :  tools/fetch-mpv-linux.sh [répertoire de sortie]
set -euo pipefail

SORTIE="${1:-$(cd "$(dirname "$0")/.." && pwd)/app/mpv-linux}"
TRAVAIL="$(mktemp -d)"
trap 'rm -rf "$TRAVAIL"' EXIT

# Les familles qu'on n'embarque jamais : couplées au pilote GPU, au serveur
# graphique ou à la configuration son de l'hôte. Toute session de bureau les
# fournit, et les emporter casse plus que cela n'aide. Même politique que la
# fermeture ELF de `bundleMpv` — à une exception près, documentée plus bas.
EXCLUES='^(ld-linux.*|libc|libm|libdl|libpthread|librt|libresolv|libGL.*|libGLX.*|libGLdispatch|libEGL.*|libX11.*|libXext|libXi|libXrender|libXtst|libXau|libXdmcp|libXfixes|libxcb.*|libasound|libdrm.*)\.'

# L'exception : libstdc++ et libgcc_s. `bundleMpv` les exclut, et à raison tant
# que la libmpv vient de la même distribution que l'hôte. Une libmpv conda-forge
# est construite avec un gcc bien plus récent : sa fermeture réclame
# GLIBCXX_3.4.36 quand Ubuntu 22.04 n'expose que 3.4.30. Sans elles, l'AppImage
# démarre, et le lecteur ne charge pas — sans erreur lisible. Embarquer une
# libstdc++ *plus récente* est sûr : la compatibilité ascendante est garantie
# dans ce sens, c'est l'inverse qui casse.

# Trois essais espacés, pour les deux téléchargements.
#
# Ce script se tient entre une release et son artefact Linux : un hoquet d'un
# hôte tiers ne doit pas coûter une publication. C'est arrivé — la v1.23.0 a
# échoué sur un `503` de micro.mamba.pm, une seconde après quoi le même appel
# passait. La leçon est celle déjà tirée pour la libmpv Windows, dont l'étape
# porte le même garde-fou : ce qu'on va chercher ailleurs, on le redemande.
reessaye() {
  local essai
  for essai in 1 2 3; do
    if "$@"; then return 0; fi
    echo "  essai $essai échoué, nouvelle tentative dans $((10 * essai)) s" >&2
    sleep $((10 * essai))
  done
  return 1
}

# Deux sources, et GitHub d'abord.
#
# `micro.mamba.pm` est l'adresse que la documentation amont donne, mais elle
# répond 503 par intermittence — un backend sur plusieurs, mesuré : trois essais
# d'affilée peuvent tous tomber sur un mauvais. Le dépôt GitHub sert le même
# binaire, sans archive à décompresser, sur l'hôte où la CI vit déjà. L'autre
# reste en repli : deux hôtes qui tombent en même temps, c'est un autre problème.
echo "→ micromamba"
mkdir -p "$TRAVAIL/mm/bin"
depuis_github() {
  curl -fsSL --retry 3 --retry-delay 5 --retry-all-errors -o "$TRAVAIL/mm/bin/micromamba" \
    https://github.com/mamba-org/micromamba-releases/releases/latest/download/micromamba-linux-64 &&
    chmod +x "$TRAVAIL/mm/bin/micromamba" &&
    "$TRAVAIL/mm/bin/micromamba" --version >/dev/null
}
depuis_mamba_pm() {
  curl -fsSL --retry 3 --retry-delay 5 --retry-all-errors \
    https://micro.mamba.pm/api/micromamba/linux-64/latest \
    | tar -xj -C "$TRAVAIL/mm" bin/micromamba
}
reessaye depuis_github || reessaye depuis_mamba_pm

echo "→ mpv depuis conda-forge (quelques minutes)"
cree_env() {
  "$TRAVAIL/mm/bin/micromamba" create -y -q -p "$TRAVAIL/env" -c conda-forge mpv >/dev/null
}
# L'environnement à demi créé d'un essai raté ferait échouer le suivant sur
# « prefix already exists » — un cas où réessayer aggrave au lieu d'aider.
cree_env_propre() { rm -rf "$TRAVAIL/env"; cree_env; }
reessaye cree_env_propre

LIB="$TRAVAIL/env/lib"
[ -f "$LIB/libmpv.so.2" ] || { echo "libmpv absente du paquet conda" >&2; exit 1; }

echo "→ fermeture ELF"
rm -rf "$SORTIE"
mkdir -p "$SORTIE"
# `ldd` avec LD_LIBRARY_PATH pointé sur l'environnement : tout ce qui se résout
# *dedans* est à nous, tout ce qui se résout ailleurs appartient à l'hôte.
copiees=0
while read -r chemin; do
  nom="$(basename "$chemin")"
  [[ "$nom" =~ $EXCLUES ]] && continue
  # -L : ce sont des liens vers libfoo.so.1.2.3 ; on veut le fichier réel sous
  # le nom que l'éditeur de liens cherchera (le SONAME).
  cp -L "$chemin" "$SORTIE/$nom"
  copiees=$((copiees + 1))
done < <(
  LD_LIBRARY_PATH="$LIB" ldd "$LIB/libmpv.so.2" \
    | awk -v env="$LIB" '$3 ~ "^" env {print $3}' | sort -u
)
cp -L "$LIB/libmpv.so.2" "$SORTIE/libmpv.so.2"
copiees=$((copiees + 1))

# Les paquets conda arrivent avec leurs symboles de débogage : 262 Mo bruts,
# 199 Mo une fois retirés. Retirer des symboles n'est pas retirer une capacité
# — rien à voir avec le plugin `xml` supprimé de libVLC en 1.18.0, qui, lui,
# enlevait la lecture des manifestes DASH.
strip --strip-unneeded "$SORTIE"/*.so* 2>/dev/null || true

version="$("$TRAVAIL/mm/bin/micromamba" list -p "$TRAVAIL/env" mpv 2>/dev/null \
  | awk '$1 == "mpv" {print $2}' | head -1)"
echo "${version:-inconnue}" > "$SORTIE/VERSION"

echo "✓ mpv ${version:-?} — $copiees bibliothèques, $(du -sh "$SORTIE" | cut -f1) dans $SORTIE"
