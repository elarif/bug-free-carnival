#!/usr/bin/env bash
# down.sh — Supprimer le cluster k3d local
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-mysaas}"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

if ! k3d cluster get "$CLUSTER_NAME" >/dev/null 2>&1; then
  yellow "Cluster '$CLUSTER_NAME' introuvable — rien à faire"
  exit 0
fi

printf '%s Suppression du cluster %s...\n' "$(yellow INFO)" "$CLUSTER_NAME"
k3d cluster delete "$CLUSTER_NAME"
green "Cluster '$CLUSTER_NAME' supprimé"