#!/usr/bin/env bash
# up.sh — Créer le cluster k3d local + déployer la stack Ory via chart umbrella Helm
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
CHART_DIR="$REPO_ROOT/infra/helm/mysaas"

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

# Exporter le kubeconfig pour l'utilisateur courant
mkdir -p "$HOME/.kube"
k3d kubeconfig get "$CLUSTER_NAME" > "$HOME/.kube/config" 2>/dev/null || true
chmod 600 "$HOME/.kube/config"
kubectl config use-context "k3d-$CLUSTER_NAME" 2>/dev/null || true

# --- 2. Namespace ---
step "Création du namespace"
kubectl get ns ory >/dev/null 2>&1 || kubectl create ns ory

# --- 3. Dépendances du chart umbrella ---
step "Résolution des dépendances Helm"
helm dependency update "$CHART_DIR" 2>&1 | tail -10

# --- 4. Déploiement de la stack complète ---
step "Déploiement de la stack Ory (chart umbrella mysaas)"

# Phase 1: installer avec migrations Ory désactivées (Postgres démarre d'abord)
helm upgrade --install mysaas "$CHART_DIR" \
  --namespace ory \
  --set kratos.kratos.automigration.enabled=false \
  --set hydra.hydra.automigration.enabled=false \
  --set keto.keto.automigration.enabled=false \
  --wait --timeout 180s 2>&1 | tail -10

# Attendre que Postgres soit prêt
printf '%s Attente Postgres...\n' "$(yellow INFO)"
PG_POD=""
for i in $(seq 1 30); do
  PG_POD=$(kubectl get pod -n ory -l app.kubernetes.io/name=postgresql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
  if [[ -n "$PG_POD" ]] && kubectl exec -n ory "$PG_POD" -- env PGPASSWORD=mysaas-dev pg_isready -U mysaas 2>/dev/null; then
    green "Postgres prêt ($PG_POD)"
    break
  fi
  sleep 2
done

if [[ -z "$PG_POD" ]]; then
  red "Postgres pod introuvable"
  exit 1
fi

# Créer les schémas Ory + tenants
step "Création des schémas Postgres (Ory + tenants)"
kubectl exec -n ory "$PG_POD" -- env PGPASSWORD=mysaas-dev psql -U mysaas -d mysaas -c \
  "CREATE SCHEMA IF NOT EXISTS kratos; CREATE SCHEMA IF NOT EXISTS hydra; CREATE SCHEMA IF NOT EXISTS keto;" 2>/dev/null
green "Schémas Ory créés: kratos, hydra, keto"

bash "$REPO_ROOT/infra/scripts/init-tenants.sh" || yellow "init-tenants.sh: voir erreurs ci-dessus"

# Phase 2: relancer avec migrations activées (les schémas existent, les migrations réussissent)
step "Finalisation du déploiement (migrations Ory)"
# Forcer le recréation des pods pour qu'ils pick up les migrations
kubectl delete pod -n ory -l app.kubernetes.io/name=kratos --force --grace-period=0 2>/dev/null || true
kubectl delete pod -n ory -l app.kubernetes.io/name=hydra --force --grace-period=0 2>/dev/null || true
kubectl delete pod -n ory -l app.kubernetes.io/name=keto --force --grace-period=0 2>/dev/null || true
helm upgrade --install mysaas "$CHART_DIR" \
  --namespace ory \
  --wait --timeout 300s 2>&1 | tail -15

# --- 5. Vérifications ---
step "Vérifications"
echo "Pods:"
kubectl get pods -n ory -o wide 2>/dev/null || true
echo ""
echo "Services:"
kubectl get svc -n ory 2>/dev/null || true
echo ""
echo "Jobs (init):"
kubectl get jobs -n ory 2>/dev/null || true

green ""
green "Cluster '$CLUSTER_NAME' prêt !"
green "  Kratos public :  http://localhost:$KRATOS_PUBLIC_PORT"
green "  Kratos admin  :  http://localhost:$KRATOS_ADMIN_PORT"
green "  Hydra public  :  http://localhost:$HYDRA_PUBLIC_PORT"
green "  Hydra admin   :  http://localhost:$HYDRA_ADMIN_PORT"
green "  Keto read     :  http://localhost:$KETO_PUBLIC_PORT"
green "  Keto write    :  http://localhost:$KETO_ADMIN_PORT"
green "  Postgres      :  localhost:$POSTGRES_PORT"
green "  Mailhog UI    :  http://localhost:$MAILHOG_PORT"
green ""
green "Smoke test: bash infra/scripts/smoke-ory.sh"