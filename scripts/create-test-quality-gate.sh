#!/usr/bin/env bash
# Creates or updates the quality gate "Test Quality" (the plugin API cannot create gates).
#
#   SONAR_HOST_URL=https://sonar.example.org SONAR_TOKEN=<token> scripts/create-test-quality-gate.sh [--with-sonar-way]
#
# The token needs the "Administer Quality Gates" permission. Running it again updates the conditions in place;
# conditions on other metrics added by hand are kept. --with-sonar-way also copies the conditions of "Sonar way"
# (recent SonarQube versions already add them to every new gate).
set -euo pipefail

GATE="Test Quality"
# metric operator threshold: the gate fails when the value is <operator> <threshold>
CONDITIONS=(
  "new_tq_correctness_issues GT 0"
  "new_tq_tests_without_assertion GT 0"
  "new_tq_untested_source_files GT 0"
  "tq_mutation_score LT 60"
  "tq_flaky_tests GT 0"
)

with_sonar_way=false
for arg in "$@"; do
  case "$arg" in
    --with-sonar-way) with_sonar_way=true ;;
    -h|--help) sed -n "2,7p" "$0"; exit 0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done
: "${SONAR_HOST_URL:?set SONAR_HOST_URL}"
: "${SONAR_TOKEN:?set SONAR_TOKEN}"
HOST="${SONAR_HOST_URL%/}"

# api GET|POST <path> [curl args...]: prints the body, fails on HTTP >= 400 with the server's message
api() {
  local method="$1" path="$2"; shift 2
  local out status
  out=$(curl -sS -u "$SONAR_TOKEN:" -X "$method" -w $'\n%{http_code}' "$HOST/api/$path" "$@")
  status="${out##*$'\n'}"
  out="${out%$'\n'*}"
  if [ "$status" -ge 400 ]; then
    echo "$method $path: HTTP $status $out" >&2
    return 1
  fi
  printf '%s' "$out"
}

# conditions <gate>: one "id metric op error" line per condition of the gate (no jq needed)
conditions() {
  api GET qualitygates/show -G --data-urlencode "name=$1" \
    | sed -n 's/.*"conditions":\[\([^]]*\)\].*/\1/p' | sed 's/},{/}|{/g' | tr '|' '\n' \
    | while IFS= read -r c || [ -n "$c" ]; do
        field() { printf '%s' "$c" | sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p"; }
        [ -n "$c" ] && echo "$(field id) $(field metric) $(field op) $(field error)"
      done
}

if ! api GET qualitygates/show -G --data-urlencode "name=$GATE" >/dev/null 2>&1; then
  api POST qualitygates/create --data-urlencode "name=$GATE" >/dev/null
  echo "created gate \"$GATE\""
fi

desired=()
if $with_sonar_way; then
  while read -r _ metric op error; do
    desired+=("$metric $op $error")
  done < <(conditions "Sonar way")
fi
desired+=("${CONDITIONS[@]}")

current=$(conditions "$GATE")
failed=0
for d in "${desired[@]}"; do
  read -r metric op error <<<"$d"
  existing=$(awk -v m="$metric" '$2 == m { print; exit }' <<<"$current")
  if [ -z "$existing" ]; then
    api POST qualitygates/create_condition --data-urlencode "gateName=$GATE" --data-urlencode "metric=$metric" \
      --data-urlencode "op=$op" --data-urlencode "error=$error" >/dev/null && echo "added   $metric $op $error" || failed=1
  else
    read -r id _ cur_op cur_error <<<"$existing"
    if [ "$cur_op $cur_error" = "$op $error" ]; then
      echo "kept    $metric $op $error"
    else
      api POST qualitygates/update_condition --data-urlencode "id=$id" --data-urlencode "metric=$metric" \
        --data-urlencode "op=$op" --data-urlencode "error=$error" >/dev/null && echo "updated $metric $op $error" || failed=1
    fi
  fi
done
[ "$failed" = 0 ] || { echo "some conditions failed; is the Test Quality plugin installed on the server?" >&2; exit 1; }
echo "gate \"$GATE\" is ready: select it in Project Settings > Quality Gate, or make it the default in Quality Gates."
