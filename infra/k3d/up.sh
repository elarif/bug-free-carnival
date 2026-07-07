#!/usr/bin/env bash
# up.sh — Créer le cluster k3d local + registre + namespaces + déployer Ory + Postgres + Mailhog
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-mysaas}"
REGISTRY_PORT="${REGISTRY_PORT:-5001}"
KRATOS_PUBLIC_PORT="${KRATOS_PUBLIC_PORT:-4433}"
KRATOS_ADMIN_PORT="${KRATOS_ADMIN_PORT:-4434}"
HYDRA_PUBLIC_PORT="${HYDRA_PUBLIC_PORT:-4444}"
HYDRA_ADMIN_PORT="${HYDRA_ADMIN_PORT:-4445}"
KETO_PUBLIC_PORT="${KETO_PUBLIC_PORT:-4466}"
KETO_ADMIN_PORT="${KETO_ADMIN_PORT:-4467}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
MAILHOG_PORT="${MAILHOG_PORT:-8025}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HELM_DIR="$REPO_ROOT/infra/helm"
MANIFESTS_DIR="$REPO_ROOT/infra/manifests"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

step() { printf '\n%s %s\n' "$(green '==')" "$*"; }

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    red "Erreur: '$1' introuvable. Voir docs/runbook/install-toolchain.md"
    exit 1
  fi
}

require_cmd k3d
require_cmd kubectl
require_cmd helm

# --- 1. Cluster k3d ---
step "Création du cluster k3d '$CLUSTER_NAME'"

if k3d cluster get "$CLUSTER_NAME" >/dev/null 2>&1; then
  yellow "Cluster '$CLUSTER_NAME' existe déjà — skip"
else
  k3d cluster create "$CLUSTER_NAME" \
    --agents 0 \
    --registry-create "k3d-$CLUSTER_NAME-registry:$REGISTRY_PORT" \
    -p "$KRATOS_PUBLIC_PORT:4433@loadbalancer" \
    -p "$KRATOS_ADMIN_PORT:4434@loadbalancer" \
    -p "$HYDRA_PUBLIC_PORT:4444@loadbalancer" \
    -p "$HYDRA_ADMIN_PORT:4445@loadbalancer" \
    -p "$KETO_PUBLIC_PORT:4466@loadbalancer" \
    -p "$KETO_ADMIN_PORT:4467@loadbalancer" \
    -p "$POSTGRES_PORT:5432@loadbalancer" \
    -p "$MAILHOG_PORT:8025@loadbalancer" \
    --k3s-arg "--disable=traefik@server:0"
fi

k3d kubeconfig get "$CLUSTER_NAME" > "$HOME/.kube/config" 2>/dev/null || true
kubectl config use-context "k3d-$CLUSTER_NAME" 2>/dev/null || true

# --- 2. Namespaces ---
step "Création des namespaces"
kubectl get ns ory >/dev/null 2>&1 || kubectl create ns ory
kubectl get ns app >/dev/null 2>&1 || kubectl create ns app

# --- 3. Postgres ---
step "Déploiement Postgres (bitnami)"
helm upgrade --install postgres bitnami/postgresql \
  --namespace ory \
  --values "$HELM_DIR/postgres/values.yaml" \
  --wait --timeout 180s 2>&1 | tail -5 || yellow "Postgres: helm en cours"

# Attendre que Postgres soit prêt
printf '%s Attente Postgres...\n' "$(yellow INFO)"
for i in $(seq 1 30); do
  if kubectl exec -n ory postgres-postgresql-0 -- env PGPASSWORD=mysaas-dev pg_isready -U mysaas 2>/dev/null; then
    green "Postgres prêt"
    break
  fi
  sleep 2
done

# --- 4. Schémas Ory (avant déploiement) ---
step "Création des schémas Ory dans Postgres"
PG_POD=$(kubectl get pod -n ory -l app.kubernetes.io/name=postgresql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [[ -n "$PG_POD" ]]; then
  kubectl exec -n ory "$PG_POD" -- env PGPASSWORD=mysaas-dev psql -U mysaas -d mysaas -c \
    "CREATE SCHEMA IF NOT EXISTS kratos; CREATE SCHEMA IF NOT EXISTS hydra; CREATE SCHEMA IF NOT EXISTS keto;" 2>/dev/null
  green "Schémas kratos, hydra, keto créés"
fi

# --- 5. Mailhog ---
step "Déploiement Mailhog"
helm upgrade --install mailhog mailhog/mailhog \
  --namespace ory \
  --values "$HELM_DIR/mailhog/values.yaml" 2>&1 | tail -3 || true

# --- 6. ConfigMaps maison ---
step "Application des manifests maison"
kubectl apply -f "$MANIFESTS_DIR/" 2>/dev/null || true

# --- 7. Kratos ---
step "Déploiement Ory Kratos"
helm upgrade --install kratos ory/kratos \
  --namespace ory \
  --values "$HELM_DIR/kratos/values.yaml" 2>&1 | tail -3 || true

# --- 8. Hydra ---
step "Déploiement Ory Hydra"
helm upgrade --install hydra ory/hydra \
  --namespace ory \
  --values "$HELM_DIR/hydra/values.yaml" 2>&1 | tail -3 || true

# --- 9. Keto ---
step "Déploiement Ory Keto"
helm upgrade --install keto ory/keto \
  --namespace ory \
  --values "$HELM_DIR/keto/values.yaml" 2>&1 | tail -3 || true

# --- 10. Init Postgres (schémas tenants) ---
step "Initialisation Postgres (schémas de test)"
bash "$REPO_ROOT/infra/scripts/init-tenants.sh" || yellow "init-tenants.sh: voir erreurs ci-dessus"

# --- 11. Vérifications ---
step "Vérifications"
echo "Pods:"
kubectl get pods -n ory -o wide 2>/dev/null || true
echo ""
echo "Services:"
kubectl get svc -n ory 2>/dev/null || true

green ""
green "Cluster '$CLUSTER_NAME' prêt !"
green "  Kratos public :  http://localhost:$KRATOS_PUBLIC_PORT"
green "  Kratos admin  :  http://localhost:$KRATOS_ADMIN_PORT"
green "  Hydra public  :  http://localhost:$HYDRA_PUBLIC_PORT"
green "  Hydra admin   :  http://localhost:$HYDRA_ADMIN_PORT"
green "  Keto public   :  http://localhost:$KETO_PUBLIC_PORT"
green "  Keto admin    :  http://localhost:$KETO_ADMIN_PORT"
green "  Postgres      :  localhost:$POSTGRES_PORT"
green "  Mailhog UI    :  http://localhost:$MAILHOG_PORT"
green ""
green "Smoke test: bash infra/scripts/smoke-ory.sh"