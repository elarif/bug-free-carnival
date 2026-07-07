#!/usr/bin/env bash
# init-ory.sh — Initialiser les données de test Ory (client OAuth2 + tuples Keto)
set -euo pipefail

HYDRA_ADMIN="${HYDRA_ADMIN:-http://localhost:4445}"
KETO_WRITE="${KETO_WRITE:-http://localhost:4467}"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

step() { printf '\n%s %s\n' "$(green '==')" "$*"; }

# --- 1. Client OAuth2 Hydra ---
step "Création du client OAuth2 Hydra (mysaas-backend)"

CLIENT_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" "$HYDRA_ADMIN/admin/clients/mysaas-backend" 2>/dev/null || echo "000")

if [[ "$CLIENT_EXISTS" == "200" ]]; then
  yellow "Client 'mysaas-backend' existe déjà — skip"
else
  RESPONSE=$(curl -s -X POST "$HYDRA_ADMIN/admin/clients" \
    -H "Content-Type: application/json" \
    -d '{
      "client_id": "mysaas-backend",
      "client_name": "mysaas Backend (M2M + authorization_code)",
      "client_secret": "mysaas-backend-secret-dev",
      "grant_types": ["client_credentials", "authorization_code", "refresh_token"],
      "response_types": ["code", "token"],
      "scope": "openid offline offline_access",
      "redirect_uris": ["http://localhost:3000/callback"],
      "token_endpoint_auth_method": "client_secret_basic"
    }' 2>/dev/null)

  if echo "$RESPONSE" | grep -q "client_id"; then
    green "Client 'mysaas-backend' créé"
  else
    red "Échec création client: $RESPONSE"
    exit 1
  fi
fi

# --- 2. Relation tuples Keto de test ---
step "Création des relation tuples Keto de test"

# Tuple: User:test-user is owner of Tenant:default
KETO_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$KETO_WRITE/admin/relation-tuples" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "Tenant",
    "object": "default",
    "relation": "owners",
    "subject_id": "test-user"
  }' 2>/dev/null || echo "000")

if [[ "$KETO_RESPONSE" == "201" || "$KETO_RESPONSE" == "200" ]]; then
  green "Tuple créé: User:test-user is owner of Tenant:default"
else
  yellow "Tuple Keto: HTTP $KETO_RESPONSE (peut déjà exister)"
fi

# --- 3. Vérifications ---
step "Vérifications"

echo "Clients Hydra:"
curl -s "$HYDRA_ADMIN/admin/clients" 2>/dev/null | python3 -c "import sys,json; clients=json.load(sys.stdin); [print(f'  - {c[\"client_id\"]}: {c[\"client_name\"]}') for c in clients]" 2>/dev/null || echo "  (erreur)"

echo ""
echo "Tuple Keto (check):"
CHECK=$(curl -s "http://localhost:4466/relation-tuples/check?namespace=Tenant&object=default&relation=owners&subject_id=test-user" 2>/dev/null)
echo "  User:test-user owner on Tenant:default → $CHECK"

green ""
green "Init Ory terminé ✅"