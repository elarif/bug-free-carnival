#!/usr/bin/env bash
# init-tenants.sh — Créer les schémas Postgres de test pour les tenants
set -euo pipefail

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-mysaas}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-mysaas-dev}"
POSTGRES_DB="${POSTGRES_DB:-mysaas}"

TENANTS="${TENANTS:-default acme demo}"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

if ! command -v kubectl >/dev/null 2>&1; then
  red "Erreur: kubectl introuvable"
  exit 1
fi

# Récupérer le pod Postgres
POD=$(kubectl get pod -n ory -l app.kubernetes.io/name=postgresql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [[ -z "$POD" ]]; then
  yellow "Postgres pod introuvable — skip init-tenants"
  exit 0
fi

printf '%s Création des schémas tenants dans Postgres:\n' "$(yellow '==')"

for tenant in $TENANTS; do
  SCHEMA="tenant_${tenant}"
  printf '  %s → schéma %s ... ' "$tenant" "$SCHEMA"
  if kubectl exec -n ory "$POD" -- env PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE SCHEMA IF NOT EXISTS ${SCHEMA};" >/dev/null 2>&1; then
    green "OK"
  else
    red "ÉCHEC"
  fi
done

printf '\n%s Schémas présents:\n' "$(yellow '==')"
kubectl exec -n ory "$POD" -- env PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dn" 2>/dev/null || true