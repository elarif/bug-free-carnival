# mysaas — SaaS multi-tenant

SaaS multi-tenant bâti sur la suite Ory (Kratos / Keto / Hydra), un backend Java Vert.x,
et un frontend à définir. Le projet est au stade bootstrap.

## Stack

| Couche       | Technologie                                            |
|--------------|--------------------------------------------------------|
| Identité     | Ory Kratos (inscription, login, sessions)              |
| OAuth2/OIDC  | Ory Hydra (tokens JWT signés localement)               |
| Permissions  | Ory Keto (relation tuples par tenant)                   |
| Backend      | Java 25 + Vert.x 5.1.3 (Maven multi-module)              |
| Frontend     | À définir (React/Vue/Svelte)                            |
| Base de données | PostgreSQL — un schéma par tenant                  |
| Migrations   | Liquibase (multi-schéma programmatique)                |
| Runtime local| k3d (k3s dans Docker) + charts Helm                     |
| Mail dev     | Mailhog                                                 |
| CI/CD        | GitHub Actions + GHCR                                   |

## Structure du repo

```
backend/   — Java Vert.x multi-module Maven (core, tenant, identity, authz, oauth, api)
frontend/  — à définir
infra/     — k3d, helm, manifests, scripts, ci
docs/      — architecture, ADR, runbooks
```

## Décisions figées

Voir `docs/adr/` pour le détail :

- **ADR 0000** — Toolchain : Docker Engine + k3d + kubectl + helm
- **ADR 0001** — Formatage Java : google-java-format via wrapper gjf (Spotless écarté, incompatible Java 25)
- **ADR 0002** — Déploiement Ory sur k3d avec schémas Postgres séparés
- **ADR 0003** — Chart Helm umbrella + enrichissements Ory (Phase 2.5)
- Charts Helm officiels Ory pour Kratos/Hydra ; manifests maison pour Keto
- Liquibase pour les migrations (multi-schéma programmatique)
- Registre k3d local (dev) + GHCR (CI)
- Mailhog pour le SMTP en dev
- Tokens Hydra en JWT signés localement + introspection en fallback
- Multi-tenant : un schéma Postgres par tenant

## Démarrage rapide

### Prérequis

Installer la toolchain : voir `docs/runbook/install-toolchain.md`.

Versions attendues : Docker 29.x, k3d 5.9.0, kubectl 1.30.x, helm 4.x, Java 25, Maven.

### Build backend

```bash
cd backend
mvn -q verify               # build + tests + lint complet
mvn -q -pl <module> verify    # un module précis
mvn -q compile                # compile rapide sans tests
```

### Formatage Java

```bash
infra/scripts/gjf <fichiers ou répertoires>           # formater en place
infra/scripts/gjf --check <fichiers ou répertoires>  # vérifier (exit 1 si sale)
infra/scripts/gjf --update                             # mettre à jour vers la dernière version
```

Le wrapper `gjf` télécharge google-java-format (JAR `all-deps`) dans
`~/.config/google-java-format/`. Lancé automatiquement pendant `mvn verify`.

### Infra locale

```bash
bash infra/k3d/up.sh              # créer le cluster k3d + Ory + Postgres + Mailhog
bash infra/scripts/smoke-ory.sh   # vérifier que Kratos/Hydra/Keto répondent
bash infra/scripts/init-ory.sh    # créer client OAuth2 + tuples Keto de test
bash infra/k3d/down.sh            # supprimer le cluster
```

Les services Ory ne servent pas de page à la racine (`/` → 404). C'est normal —
ils n'exposent que des endpoints d'API. Les health checks se font sur
`/health/alive` (200). Voir `docs/runbook/k3d-cluster.md` pour la liste
complète des endpoints.

## Plan de bootstrap

Le projet suit un plan en 8 phases avec vérifications à chaque jalon.

| Phase | Périmètre                                          | Statut       |
|-------|----------------------------------------------------|--------------|
| 0     | Préparation outillage + squelette repo             | ✅ Terminée  |
| 1     | Squelette Maven multi-module (core/tenant/…)       | ✅ Terminée  |
| 2     | Infra k3d + Ory (Kratos/Keto/Hydra) + Postgres      | ✅ Terminée  |
| 3     | Skeleton API Vert.x (/health, /ready)               | ✅ Terminée  |
| 4     | Couche tenant (résolution + schémas Postgres)      | ✅ Terminée  |
| 5     | Intégration Kratos (sessions + webhooks)           | ✅ Terminée  |
| 6     | Intégration Hydra (resource server, JWT)           | ✅ Terminée  |
| 7     | Intégration Keto (permissions par tenant)           | ✅ Terminée  |
| 8     | Pipeline CI + documentation                         | À venir      |

### Détail des phases

**Phase 0 — Préparation outillage**
Installation de Docker Engine, k3d, kubectl, helm. Création de l'arborescence du repo
(`backend/`, `frontend/`, `infra/`, `docs/`), conventions (`AGENTS.md`, `.gitignore`,
`.editorconfig`), ADR 0000 et runbook d'installation.

**Phase 1 — Squelette Maven multi-module**
POM parent avec BOMs (Vert.x 5.1.3, Jackson, Postgres, JUnit5, REST-assured).
Modules : `core`, `tenant`, `identity`, `authz`, `oauth`, `api`. Module `core` minimal
avec `MainVerticle` placeholder. Formatage Java via google-java-format (wrapper
`infra/scripts/gjf`, JAR `all-deps` autonome — Spotless écarté, incompatible Java 25).
Tri des POMs via sortpom-maven-plugin 4.0.0. Style via Checkstyle.
Vérifications : `mvn verify` passe sur les 6 modules (gjf + sortpom + checkstyle + tests).

**Phase 2 — Infra k3d + Ory + Postgres + Mailhog**
Chart Helm umbrella `infra/helm/mysaas` (dépendances: postgresql, mailhog, kratos, hydra,
keto — Ory v0.62.1 / app v26.2.0). Config consolidée dans un seul `values.yaml`. Postgres
avec schémas séparés par service Ory + schémas par tenant. Kratos enrichi : flows
recovery/verification/logout, hooks session/revoke_active_sessions, identity schema avec
trait `tenant_id`. Client OAuth2 Hydra de test. Keto namespaces User/Tenant + tuples de
test. Scripts `smoke-ory.sh`, `init-tenants.sh`, `init-ory.sh`. Déploiement en 2 phases
(migrations différées).
Vérifications : pods Running, 6/6 health endpoints 200, schémas Postgres, client Hydra, tuples Keto.

**Phase 3 — Skeleton API Vert.x**
`MainVerticle` (VerticleBase v5, `main()` inline — Launcher retiré en v5) déploie
`HttpServerVerticle`. Router Vert.x Web avec `/health` (liveness → 200) et `/ready`
(readiness → ping TCP Postgres, 503 si down). Config via env vars (`AppConfig`).
Tests vertx-junit5 + WebClient (2 tests: `/health` 200, `/ready` 503).
Vérifications : `mvn -pl api verify` ✅, runtime `curl /health` → 200, `curl /ready` → 503.

**Phase 4 — Couche tenant**
`TenantResolver` (header `X-Tenant` ou sous-domaine). `TenantContext` propagé via
`RoutingContext`. `TenantSchemaManager` (création/migration schéma Postgres par tenant
via Liquibase). Table `public.tenants` (registry) + CRUD admin. Filtre `TenantFilter`.
Vérifications : tests unitaires + tests API contract, création de tenant → schéma créé.

Implémentation : `TenantContext` (record), `TenantRegistry` (interface) + `JdbcTenantRegistry`
(table `public.tenants`) / `InMemoryTenantRegistry`. `TenantSchemaManager` (interface) +
`JdbcTenantSchemaManager` (DataSource HikariCP) / `InMemoryTenantSchemaManager`. `TenantResolver`,
`TenantFilter` (order -100, exempte `/health`, `/ready`, `/admin/*`). `TenantAdminHandler`
(CRUD `/admin/tenants` + validation slug). `TenantDbMigrator` (Liquibase, changelog
`tenants-changelog.xml`). `TenantComponents` (factory prod HikariCP). Câblage dans
`HttpServerVerticle` (mode dégradé si DB injoignable). 24 tests (10 unit + 5 filter + 8 contract + 1 module).
Vérifications : `mvn verify` ✅, `mvn -pl tenant verify` ✅.

**Phase 5 — Intégration Kratos**
Client HTTP Vert.x vers Kratos `/sessions/whoami`. `KratosSessionFilter` (validation
cookie/session, injection `Identity` dans le contexte). Endpoint webhook
(after registration) → provisioning tenant par défaut. Tests WireMock + REST-assured.
Vérifications : `mvn -pl identity verify`, smoke réel login → `/whoami` → 200.

Implémentation : `Identity` (record — id, tenantId, email, traits). `KratosClient`
(WebClient Vert.x → `/sessions/whoami`, Future<Identity>). `KratosSessionException`
(statusCode 401/403/500). `KratosSessionFilter` (order -90, exempt `/health`, `/ready`,
`/admin/*`, `/webhooks/*` ; 401 si session invalide, 503 si Kratos injoignable).
`RegistrationWebhookHandler` (`POST /webhooks/kratos/after-registration` — provisioning
tenant par défaut dérivé du domaine email si `tenant_id` absent, ou création du tenant
indiqué s'il n'existe pas). `IdentityComponents` (factory prod WebClient). Câblage dans
`HttpServerVerticle` (mode dégradé si Kratos injoignable). 22 nouveaux tests (4 unit
Identity + 4 KratosClient WireMock + 6 KratosSessionFilter + 5 RegistrationWebhook +
2 IdentityComponents + 4 API contract intégration).
Vérifications : `mvn verify` ✅, `mvn -pl identity verify` ✅.

**Phase 6 — Intégration Hydra (resource server)**
Validation JWT Bearer localement (JWKS Hydra) + introspection fallback.
`HydraTokenFilter` (subject, scopes, tenant claim). Endpoint protégé `/me` d'exemple.
Tests token valide/invalide/expiré.
Vérifications : `mvn -pl oauth verify`, `curl -H "Authorization: Bearer <jwt>" /me` → 200.

Implémentation : `TokenPrincipal` (record — subject, scopes, tenantId, issuer ; parsing
depuis claims JWT ou réponse introspection). `TokenValidationException` (statusCode
401/503). `HydraTokenValidator` (charge JWKS Hydra via WebClient, crée `JWTAuth`
vertx-auth-jwt, valide signature + issuer + expiration). `HydraIntrospectionClient`
(fallback tokens opaques via `POST /admin/oauth2/introspect` avec client credentials).
`HydraTokenFilter` (order -80, extrait Bearer, valide JWT puis fallback introspection,
injecte `TokenPrincipal` ; 401 si invalide, 503 si Hydra injoignable ; exempt `/health`,
`/ready`, `/admin/*`, `/webhooks/*`). `OauthComponents` (factory prod). Endpoint `/me`
(retourne subject, tenant_id, issuer, scopes). Câblage dans `HttpServerVerticle` (mode
dégradé si Hydra injoignable). Config : `HYDRA_JWKS_URL`, `OAUTH_CLIENT_ID`,
`OAUTH_CLIENT_SECRET`. 22 nouveaux tests (5 TokenPrincipal + 6 HydraTokenValidator
WireMock + 4 HydraIntrospectionClient WireMock + 7 HydraTokenFilter + 1 OauthComponents
+ 4 API contract intégration — génération RSA + JWKS mocké).
Vérifications : `mvn verify` ✅ (78 tests total), `mvn -pl oauth verify` ✅.

**Phase 7 — Intégration Keto (permissions par tenant)**
Client Keto (check relation tuple : `tenant:<slug>::<relation>::<subject>`).
`KetoAuthzFilter` combinant `TenantContext` + `Identity` + permission. Annotations
sur routes. Tests unitaires (mock Keto) + tests API contract (Pact).
Vérifications : user sans perm sur tenant B → 403 ; même user sur tenant A → 200.

Implémentation : `KetoClient` (WebClient Vert.x → `GET /relation-tuples/check` sur
l'API read Keto, Future<Boolean>). `KetoException` (statusCode 400/500).
`KetoAuthzFilter` (order -70, après tenant -100, identity -90, oauth -80). Récupère le
`TenantContext` + le subject depuis `Identity` (Kratos) ou `TokenPrincipal` (Hydra),
vérifie `Tenant:<slug>#access@<subject>` ; 403 si refusé, 503 si Keto injoignable.
Exempte `/health`, `/ready`, `/admin/*`, `/webhooks/*`. `AuthzComponents` (factory prod).
Câblage dans `HttpServerVerticle` (mode dégradé si Keto injoignable). 16 nouveaux tests
(4 KetoClient WireMock + 7 KetoAuthzFilter + 1 AuthzComponents + 4 API contract
intégration — user autorisé sur tenant A / refusé sur tenant B / Keto down).
Vérifications : `mvn verify` ✅ (89 tests total), `mvn -pl authz verify` ✅.

**Phase 8 — Pipeline CI + documentation**
GitHub Actions (build, tests, lint, helm lint, kubeval, build image → GHCR).
Documentation finale (`architecture.md`, `bootstrap.md`, `runbook/`). Conventions
de commit (Conventional Commits) + PR template.
Vérifications : `mvn verify` complet en CI, `helm lint`, `kubeval`, image pushée.

## Conventions

- **Commits** : Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `ci:`, `infra:`)
- **Build** : tous les `mvn` se lancent depuis `backend/`
- **Lint** : google-java-format (`infra/scripts/gjf`) + SortPom + Checkstyle via `mvn verify`
- Voir `AGENTS.md` pour les conventions détaillées

## Documentation

- `docs/adr/` — Architecture Decision Records
- `docs/runbook/` — Procédures ops (installation toolchain, etc.)
- `docs/architecture.md` — Vue d'architecture (à venir Phase 8)