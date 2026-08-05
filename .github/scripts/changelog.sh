#!/usr/bin/env bash
# Notes de release à partir des commits conventionnels entre le tag précédent et
# le tag courant. Écrit sur la sortie standard au format $GITHUB_OUTPUT :
#
#   notes<<CHANGELOG_EOF
#   ...markdown...
#   CHANGELOG_EOF
#
# Pourquoi pas `generate_release_notes` seul : les notes automatiques de GitHub
# listent les *pull requests* fusionnées. Ce dépôt pousse directement sur main,
# il n'y a donc rien à lister et la release n'affichait qu'un « Full Changelog ».
#
# Utilisable en local pour prévisualiser :  bash .github/scripts/changelog.sh
set -euo pipefail

tag="${GITHUB_REF_NAME:-$(git describe --tags --abbrev=0)}"
# Tag précédent *atteignable depuis le parent* : robuste aux tags posés sur des
# branches parallèles. Absent (toute première release) → tout l'historique.
#
# Une version finale saute les pre-releases : personne ne les a reçues, les
# updaters ne servent que `releases/latest`. S'arrêter à la dernière rc ne
# raconterait donc à l'utilisateur que la fin de l'histoire. Une rc, elle, se
# compare bien à la précédente — ses notes s'adressent aux testeurs.
case "$tag" in
  *-*) prev="$(git describe --tags --abbrev=0 "${tag}^" 2>/dev/null || true)" ;;
  *)   prev="$(git describe --tags --abbrev=0 --exclude='*-*' "${tag}^" 2>/dev/null || true)" ;;
esac
range="${prev:+${prev}..}${tag}"
repo_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-JibayMcs/MooVie}"

# Une ligne par commit : type <TAB> scope <TAB> sujet <TAB> sha.
# Un sujet hors convention tombe dans le type « other » plutôt que d'être perdu.
parsed="$(
  git log --no-merges --reverse --pretty=format:'%H%x09%s' "$range" | awk -F'\t' '
    {
      sha = $1; subject = $2
      if (match(subject, /^[a-z]+(\([^)]*\))?!?: /)) {
        head = substr(subject, 1, RLENGTH - 2)
        rest = substr(subject, RLENGTH + 1)
        type = head; scope = ""
        if (match(head, /\(([^)]*)\)/)) {
          scope = substr(head, RSTART + 1, RLENGTH - 2)
          type = substr(head, 1, RSTART - 1)
        }
        sub(/!$/, "", type)
        # Le commit de release décrit la release elle-même : hors changelog.
        if (type == "chore" && scope == "release") next
        printf "%s\t%s\t%s\t%s\n", type, scope, rest, sha
      } else {
        printf "other\t\t%s\t%s\n", subject, sha
      }
    }'
)"

# section <titre> <types séparés par des virgules>
section() {
  local title="$1" types="$2" body
  body="$(printf '%s\n' "$parsed" | awk -F'\t' -v want=",$types," -v url="$repo_url" '
    NF && index(want, "," $1 ",") > 0 {
      short = substr($4, 1, 7)
      if ($2 != "") printf "- **%s**: %s ([%s](%s/commit/%s))\n", $2, $3, short, url, $4
      else printf "- %s ([%s](%s/commit/%s))\n", $3, short, url, $4
    }')"
  [ -n "$body" ] || return 0
  printf '### %s\n\n%s\n\n' "$title" "$body"
}

notes="$(
  section 'Features' 'feat'
  section 'Fixes' 'fix'
  section 'Performance' 'perf'
  section 'Refactoring' 'refactor'
  section 'Documentation' 'docs'
  section 'Maintenance' 'build,chore,ci,style,test,other'
)"

# Aucun commit exploitable : on laisse `generate_release_notes` seul plutôt que
# de publier une section vide.
[ -n "$notes" ] || exit 0

printf 'notes<<CHANGELOG_EOF\n%s\nCHANGELOG_EOF\n' "$notes"
