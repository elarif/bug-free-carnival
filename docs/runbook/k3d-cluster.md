# Runbook — Cluster k3d local (Ory + Postgres + Mailhog)

## Démarrage

```bash
bash infra/k3d/up.sh
```

Le script :
1. Crée le cluster k3d `mysaas` + registre local (port 5001)
2. Crée les namespaces `ory` et `app`
3. Déploie Postgres (bitnami) et attend qu'il soit prêt
4. Crée les schémas `kratos`, `hydra`, `keto` dans Postgres
5. Déploie Mailhog, Kratos, Hydra, Keto via Helm
6. Crée les schémas tenants (`tenant_default`, `tenant_acme`, `tenant_demo`)
7. Affiche le statut des pods et services

## Arrêt

```bash
bash infra/k3d/down.sh
```

Supprime complètement le cluster k3d (volumes inclus).

## Vérifications

```bash
# Pods
kubectl get pods -n ory

# Services
kubectl get svc -n ory

# Smoke test Ory
bash infra/scripts/smoke-ory.sh

# Schémas Postgres
bash infra/scripts/init-tenants.sh
```

## Endpoints locaux

| Service      | URL                                | Endpoint de test             |
|--------------|------------------------------------|------------------------------|
| Kratos public | http://localhost:4433             | `/health/alive` (200)        |
| Kratos admin  | http://localhost:4434             | `/health/alive` (200)        |
| Hydra public  | http://localhost:4444             | `/.well-known/openid-configuration` (200) |
| Hydra admin   | http://localhost:4445             | `/health/alive` (200)        |
| Keto read     | http://localhost:4466             | `/health/alive` (200)        |
| Keto write    | http://localhost:4467             | `/health/alive` (200)        |
| Postgres      | localhost:5432                    | `psql -U mysaas -d mysaas`   |
| Mailhog UI    | http://localhost:8025             | UI web                       |

> **Note** : Les services Ory ne servent pas de page à la racine (`/` → 404).
> C'est le comportement attendu — ils n'exposent que des endpoints d'API.
> Les health checks se font sur `/health/alive` (200).

### Endpoints API utiles

| Service  | Endpoint                              | Méthode  | Description                  |
|----------|---------------------------------------|----------|------------------------------|
| Kratos   | `/self-service/login/browser`         | GET      | Initialise un flow de login  |
| Kratos   | `/self-service/registration/browser`  | GET      | Initialise un flow d'inscription |
| Kratos   | `/sessions/whoami`                    | GET      | Valide une session           |
| Hydra    | `/.well-known/openid-configuration`   | GET      | OIDC discovery               |
| Hydra    | `/oauth2/auth`                        | GET      | Authorisation OAuth2         |
| Hydra    | `/admin/clients`                      | GET/POST | Gestion des clients OAuth2   |
| Keto     | `/read/<namespace>/<object>/<relation>` | GET    | Vérifie une permission       |
| Keto     | `/write`                              | PUT      | Écrit une relation tuple     |

## Configuration Postgres

- Utilisateur : `mysaas`
- Mot de passe : `mysaas-dev`
- Base : `mysaas`
- Schémas : `public`, `kratos`, `hydra`, `keto`, `tenant_*`

## Charts Helm

| Chart             | Version | Namespace |
|-------------------|---------|-----------|
| bitnami/postgresql | 18.7.12 | ory       |
| mailhog/mailhog    | 5.8.0   | ory       |
| ory/kratos         | 0.62.1  | ory       |
| ory/hydra          | 0.62.1  | ory       |
| ory/keto           | 0.62.1  | ory       |

## Notes

- Le cluster k3d désactive traefik (ports mappés directement via loadbalancer)
- Le mode dev est activé sur Kratos et Hydra (HTTP sans TLS)
- Les migrations Ory s'exécutent via helm hooks (jobs pre-install/pre-upgrade)
- Le schéma d'identité Kratos est embarqué en base64 dans la config (pas de ConfigMap)
- Persistence Postgres désactivée en dev (perte de données au redémarrage du cluster)