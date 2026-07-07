#!/usr/bin/env bash
# smoke-ory.sh — Vérifier que les services Ory (Kratos, Hydra, Keto) répondent
set -euo pipefail

KRATOS_PUBLIC="${KRATOS_PUBLIC:-http://localhost:4433}"
KRATOS_ADMIN="${KRATOS_ADMIN:-http://localhost:4434}"
HYDRA_PUBLIC="${HYDRA_PUBLIC:-http://localhost:4444}"
HYDRA_ADMIN="${HYDRA_ADMIN:-http://localhost:4445}"
KETO_PUBLIC="${KETO_PUBLIC:-http://localhost:4466}"
KETO_ADMIN="${KETO_ADMIN:-http://localhost:4467}"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

check() {
  local name="$1" url="$2"
  local status
  status=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null || echo "000")
  if [[ "$status" == "200" ]]; then
    green "  ✅ $name ($url) → $status"
    return 0
  else
    red "  ❌ $name ($url) → $status"
    return 1
  fi
}

printf '%s Smoke test Ory:\n\n' "$(yellow '==')"
rc=0
check "Kratos public /health/alive" "$KRATOS_PUBLIC/health/alive" || rc=1
check "Kratos admin /health/alive"  "$KRATOS_ADMIN/health/alive"  || rc=1
check "Hydra public /health/alive"  "$HYDRA_PUBLIC/health/alive" || rc=1
check "Hydra admin /health/alive"   "$HYDRA_ADMIN/health/alive"  || rc=1
check "Keto public /health/alive"   "$KETO_PUBLIC/health/alive"  || rc=1
check "Keto admin /health/alive"    "$KETO_ADMIN/health/alive"   || rc=1

echo ""
if [[ $rc -eq 0 ]]; then
  green "Tous les services Ory répondent ✅"
else
  red "Certains services Ory ne répondent pas ❌"
  exit 1
fi