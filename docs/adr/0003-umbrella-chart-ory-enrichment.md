# ADR 0003 — Chart Helm umbrella + enrichissements Ory (Phase 2.5)

Date: 2026-07-07
Statut: Accepté

## Contexte

La Phase 2 a déployé la stack Ory avec 5 `values.yaml` séparés et un script `up.sh`
qui enchaînait 5 `helm upgrade` + `kubectl apply` pour les manifests. Cette approche :
- ne gérait pas l'ordre des dépendances (Postgres avant Ory)
- dispersait la config sur 5 fichiers
- rendait le debug et la reproductibilité fragiles
- ne provisionnait pas de client OAuth2 ni de tuples Keto de test

L'audit de la doc Ory officielle (https://www.ory.com/docs/oss/) a aussi révélé :
- Kratos : flows recovery/verification manquants, hooks `session`/`revoke_active_sessions` absents
- Hydra : `urls.logout` au lieu de `post_logout`, client OAuth2 de test absent
- Keto : namespace `tenant` sans modèle, OPL non défini
- Identity schema : pas de trait `tenant_id` pour le lien identity↔tenant

## Décision

### 1. Chart Helm umbrella `infra/helm/mysaas`

Consolider tous les charts en un seul chart umbrella avec dépendances :

```
infra/helm/mysaas/
├── Chart.yaml              # dépendances: postgresql, mailhog, kratos, hydra, keto
├── values.yaml             # TOUTE la config au même endroit
└── templates/
    └── _helpers.tpl
```

Déploiement : `helm dep update && helm upgrade --install mysaas infra/helm/mysaas -n ory`

Remplace : 5 `values.yaml` séparés, `infra/manifests/`, 5 `helm upgrade` dans `up.sh`.

### 2. Enrichissements Kratos

- Flows `recovery`, `verification`, `logout` ajoutés avec `ui_url`
- Hook `session` sur `registration.after.password` (auto-login post-inscription)
- Hook `revoke_active_sessions` sur `login.after.password` (sécurité)
- Method `code` activée (recovery/verification par code)
- Identity schema enrichi : trait `tenant_id` (optionnel, pour lien identity↔tenant)

### 3. Hydra

- `urls.logout` corrigé (v26 utilise `urls.logout`, pas `post_logout`)
- Client OAuth2 créé via API admin (`init-ory.sh`), pas via CRD (maester v0.0.42 bug avec `clientID`)

### 4. Keto

- Namespaces `User` (id:0) et `Tenant` (id:1) en config legacy (OPL via ConfigMap non supporté par le chart v0.62.1)
- Tuple de test : `User:test-user is owner of Tenant:default`
- Le modèle OPL complet (User/Tenant avec owners/members/admins + permits) sera monté en Phase 7

### 5. Script `init-ory.sh`

Crée :
- Client OAuth2 Hydra `mysaas-backend` (M2M + authorization_code)
- Tuple Keto de test (`User:test-user owner of Tenant:default`)
- Vérifie : clients Hydra, check permission Keto

### 6. Déploiement en 2 phases

Le `up.sh` déploie en 2 phases car les migrations Ory (hooks pre-install) échouent
si les schémas Postgres n'existent pas encore :

1. **Phase 1** : `helm install` avec migrations désactivées → Postgres démarre
2. **Init schémas** : création manuelle des schémas `kratos`/`hydra`/`keto` + `tenant_*`
3. **Phase 2** : `helm upgrade` avec migrations activées → les Ory migrent et démarrent

## Alternatives considérées

- **Hook pre-install pour init Postgres** : échoue car Postgres (dépendance) n'est pas
  prêt pendant les hooks pre-install
- **CRD OAuth2Client via hydra-maester** : maester v0.0.42 rejette `clientID` en strict
  decoding — bug du chart
- **OPL via ConfigMap montée** : chart Keto v0.62.1 ne supporte pas `extraVolumes` pour
  les fichiers OPL — utilise `namespaces[]` legacy à la place

## Conséquences

- `up.sh` fait 2 `helm upgrade` + init schémas entre les deux
- `init-ory.sh` crée le client Hydra + tuples Keto (idempotent)
- Le modèle OPL complet sera ajouté en Phase 7 quand le module `authz` sera implémenté
- Les migrations Ory en v26 utilisent `kratos migrate sql up` (pas `kratos migrate up`)
- Le chart umbrella est la seule release Helm (`helm uninstall mysaas` nettoie tout)