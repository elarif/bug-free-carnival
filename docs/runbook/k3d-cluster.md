# Runbook — Cluster k3d local (Ory + Postgres + Mailhog)

## Démarrage

```bash
bash infra/k3d/up.sh
```

Le script :
1. Crée le cluster k3d `mysaas` + registre local (port 5001)
2. Crée le namespace `ory`
3. Résout les dépendances Helm (`helm dep update`)
4. **Phase 1** : déploie le chart umbrella avec migrations Ory désactivées (Postgres démarre)
5. Attend que Postgres soit prêt
6. Crée les schémas `kratos`, `hydra`, `keto` + `tenant_*` dans Postgres
7. **Phase 2** : relance le chart avec migrations activées (Ory migrent et démarrent)
8. Affiche le statut des pods et services

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

# Smoke test Ory (health endpoints)
bash infra/scripts/smoke-ory.sh

# Schémas Postgres (crée les schémas tenants si absents)
bash infra/scripts/init-tenants.sh

# Init Ory (client OAuth2 Hydra + tuples Keto de test)
bash infra/scripts/init-ory.sh
```

## Chart umbrella

Toute la stack est déployée via un seul chart Helm umbrella :

```
infra/helm/mysaas/
├── Chart.yaml              # dépendances: postgresql, mailhog, kratos, hydra, keto
├── values.yaml             # TOUTE la config au même endroit
└── templates/
    └── _helpers.tpl
```

Déploiement manuel :
```bash
helm dep update infra/helm/mysaas
helm upgrade --install mysaas infra/helm/mysaas -n ory
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
| Keto     | `/relation-tuples/check`              | GET      | Vérifie une permission       |
| Keto     | `/admin/relation-tuples`              | PUT      | Écrit une relation tuple     |

## Configuration Postgres

- Utilisateur : `mysaas`
- Mot de passe : `mysaas-dev`
- Base : `mysaas`
- Schémas : `public`, `kratos`, `hydra`, `keto`, `tenant_*`

## Charts Helm (dépendances du chart umbrella)

| Chart             | Version | Namespace |
|-------------------|---------|-----------|
| bitnami/postgresql | 18.7.12 | ory       |
| mailhog/mailhog    | 5.8.0   | ory       |
| ory/kratos         | 0.62.1  | ory       |
| ory/hydra          | 0.62.1  | ory       |
| ory/keto           | 0.62.1  | ory       |

## Client OAuth2 de test

Créé par `init-ory.sh` via l'API admin Hydra :

| Champ | Valeur |
|-------|--------|
| client_id | `mysaas-backend` |
| client_secret | `mysaas-backend-secret-dev` |
| grant_types | client_credentials, authorization_code, refresh_token |
| scope | openid offline offline_access |
| redirect_uri | http://localhost:3000/callback |

## Keto namespaces

| Namespace | ID | Relations |
|-----------|----|-----------|
| User      | 0  | (namespace simple, pas de relations) |
| Tenant    | 1  | owners, members, admins |

Tuple de test : `User:test-user is owner of Tenant:default`

## Notes

- Le cluster k3d désactive traefik (ports mappés directement via loadbalancer)
- Le mode dev est activé sur Kratos et Hydra (HTTP sans TLS)
- Les migrations Ory s'exécutent via helm hooks (jobs pre-install/pre-upgrade)
- Le schéma d'identité Kratos est embarqué en base64 dans la config (email + tenant_id)
- Persistence Postgres désactivée en dev (perte de données au redémarrage du cluster)
- Le déploiement en 2 phases est nécessaire car les migrations Ory échouent si les schémas
  Postgres n'existent pas encore (les hooks pre-install s'exécutent avant que Postgres soit prêt)
- Le client OAuth2 est créé via API admin (le CRD OAuth2Client de hydra-maester v0.0.42
  a un bug avec `clientID` en strict decoding)