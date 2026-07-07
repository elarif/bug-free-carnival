# AGENTS.md — Conventions pour opencode

## Build \& test

Le backend Maven multi-module vit dans `backend/`. **Tous les `mvn` se lancent depuis `backend/`** :

```bash
mvn -q verify                # build + tests + lint complet
mvn -q -pl <module> verify    # un module précis (api, tenant, identity, authz, oauth, core)
mvn -q compile                # compile rapide sans tests
```

## Lint / format

- **google-java-format** (formatage Java) via le wrapper `infra/scripts/gjf`
  (cache dans `~/.config/google-java-format/`). Lancé automatiquement par
  `exec-maven-plugin` pendant `mvn verify` (`gjf --check` sur `src/main/java` et
  `src/test/java`).
- **SortPom** (tri des POMs) via `sortpom-maven-plugin` 4.0.0. Lancé pendant
  `mvn verify`. Pour trier : `mvn sortpom:sort`.
- **Checkstyle** (style) via `maven-checkstyle-plugin` 3.6.0, config
  `backend/checkstyle.xml`. Lancé pendant `mvn verify`.
- En cas d'échec gjf : formater avec `infra/scripts/gjf <fichiers ou répertoires>`
  puis relancer `mvn verify`.
- Vérifier systématiquement après toute modification Java :
  `mvn -q -pl <module> checkstyle:check` et `infra/scripts/gjf --check <fichiers>`.
- **Note** : Spotless n'est plus utilisé (google-java-format 1.25.2 embarqué par
  Spotless est incompatible Java 25). Voir ADR 0000 et le commit `4ef2812`.

## Infra

- Cluster k3d : `bash infra/k3d/up.sh`, `bash infra/k3d/down.sh`
- Smoke Ory : `bash infra/scripts/smoke-ory.sh`
- Init tenants : `bash infra/scripts/init-tenants.sh`
- Charts Helm : `helm lint infra/helm/<chart>/` (non applicable — values only, charts via repo)
- Manifests : `kubeval infra/manifests/`
- Voir `docs/runbook/k3d-cluster.md` pour le détail

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