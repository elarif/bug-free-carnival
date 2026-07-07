# ADR 0002 — Déploiement Ory sur k3d avec schémas Postgres séparés

Date: 2026-07-07
Statut: Accepté

## Contexte

Les services Ory (Kratos, Hydra, Keto) doivent être déployés sur le cluster k3d
local pour le développement. Chaque service a besoin de ses propres tables dans
Postgres.

Première tentative : tous les services partagent la même base `mysaas` et le même
schéma `public`. Résultat : conflit sur la table `networks` (créée par Kratos et
Hydra avec des structures différentes → `SQLSTATE 42P07: relation already exists`).

## Décision

Utiliser **un schéma Postgres distinct par service Ory** :

| Service | Schéma | DSN |
|---------|--------|-----|
| Kratos  | `kratos`  | `...?sslmode=disable&search_path=kratos` |
| Hydra   | `hydra`   | `...?sslmode=disable&search_path=hydra` |
| Keto    | `keto`    | `...?sslmode=disable&search_path=keto` |
| Tenants | `tenant_<slug>` | Un schéma par tenant (Phase 4) |

Les schémas sont créés avant le déploiement des charts Helm (étape 4 de `up.sh`).

## Autres décisions de déploiement

1. **Charts Helm officiels Ory v0.62.1** pour Kratos, Hydra et Keto (pas de
   manifests maison pour Keto — le chart officiel est suffisant).
2. **Mode dev** activé (`hydra.dev: true`, `kratos.development: true`) pour
   autoriser HTTP sans TLS en local.
3. **Migration automatique** via `automigration.type: job` sous
   `kratos.automigration` / `hydra.automigration` / `keto.automigration` (pas
   au niveau racine du values).
4. **Schéma d'identité Kratos** embarqué en `base64://` dans la config (évite
   le montage de ConfigMap — `extraVolumes` non supporté par le chart v0.62.1).
5. **Services LoadBalancer** avec ports correspondant aux mappings k3d
   (4433, 4434, 4444, 4445, 4466, 4467).
6. **Postgres** via chart bitnami (persistence désactivée en dev).
7. **Mailhog** pour le SMTP dev (port 8025 UI, 1025 SMTP).

## Conséquences

- Le script `infra/k3d/up.sh` crée les schémas Ory avant le déploiement Helm.
- Chaque service Ory est isolé dans son schéma (pas de conflit de tables).
- Les schémas tenants (`tenant_*`) sont créés par `infra/scripts/init-tenants.sh`.
- Le mode dev désactive les vérifications TLS — à ne pas utiliser en production.
- `infra/manifests/kratos-schema-configmap.yaml` n'est plus utilisé (schéma en
  base64) mais conservé pour référence future.