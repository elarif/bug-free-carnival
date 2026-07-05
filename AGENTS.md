# AGENTS.md — Conventions pour opencode

## Build \& test

Le backend Maven multi-module vit dans `backend/`. **Tous les `mvn` se lancent depuis `backend/`** :

```bash
mvn -q verify                # build + tests + lint complet
mvn -q -pl <module> verify    # un module précis (api, tenant, identity, authz, oauth, core)
mvn -q compile                # compile rapide sans tests
```

## Lint / format

- **Spotless** (formatage) + **Checkstyle** (style) configurés dans le POM parent.
- En cas d'échec Spotless, lancer `mvn spotless:apply` puis refaire `mvn verify`.
- Vérifier systématiquement après toute modification Java : `mvn -q -pl <module> checkstyle:check spotless:check`

## Infra

- Cluster k3d : `bash infra/k3d/up.sh` (à créer Phase 2), `bash infra/k3d/down.sh`
- Smoke Ory : `bash infra/scripts/smoke-ory.sh`
- Charts Helm : `helm lint infra/helm/<chart>/`
- Manifests : `kubeval infra/manifests/`

## Tests

- JUnit 5 (unitaires) + REST-assured (tests API/contract)
- WireMock pour mocker Kratos/Hydra/Keto dans les tests d'intégration
- Pact pour les tests de contrat consommateur (à venir Phase 7)

## Conventions de commit

Conventional Commits : `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`, `ci:`, `infra:`.

## Structure du repo

```
backend/   — Java Vert.x multi-module Maven (core, tenant, identity, authz, oauth, api)
frontend/  — à définir
infra/     — k3d, helm, manifests, scripts, ci
docs/      — architecture, ADR, runbooks
```

## Outils attendus (Phase 0 installés)

- Java 25 (Temurin via SDKMAN)
- Maven (apt)
- Docker Engine 29.x
- k3d v5.9.0 (k3s v1.35.5)
- kubectl v1.30.14
- helm v4.2.2

## Décisions figées (voir docs/adr/)

- Ory : charts officiels pour Kratos/Hydra, manifests maison pour Keto
- DB migrations : Liquibase (multi-schéma programmatique)
- Registre images : k3d local (dev) + GHCR (CI)
- Mail SMTP dev : Mailhog
- Tokens Hydra : JWT signés localement + introspection fallback
- Multi-tenant : un schéma Postgres par tenant