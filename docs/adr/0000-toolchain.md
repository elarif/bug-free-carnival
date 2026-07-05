# ADR 0000 — Choix de la toolchain d'infrastructure locale

Date: 2026-07-05
Statut: Accepté

## Contexte

Le projet SaaS multi-tenant s'appuie sur la suite Ory (Kratos, Keto, Hydra) déployée
dans un cluster Kubernetes local pour le développement. Il faut choisir la pile
d'outils pour exécuter ce cluster localement et interagir avec lui.

Critères :
- Reproductibilité entre développeurs
- Faible empreinte (machine de dev)
- Compatibilité avec les charts Helm officiels Ory
- Capacité à simuler un environnement K8s proche de la production

## Décision

Adopter la stack suivante :

1. **Docker Engine** (et non Podman, ni Docker Desktop)
2. **k3d** comme runtime Kubernetes local (k3s dans Docker)
3. **kubectl** officiel (v1.30) pour piloter le cluster
4. **Helm** v4 pour déployer les charts Ory/Postgres/Mailhog

## Alternatives considérées

### Runtime conteneurs
- **Podman** : écarté car k3d ne supporte officiellement que Docker ; compatibilité
  imperfecte avec rootless.
- **Docker Desktop** : écarté (licence commerciale, overhead).

### Kubernetes local
- **Minikube** : plus lourd, VM complète, démarrage lent.
- **Kind** : viable, mais k3d offre une meilleure intégration avec k3s et
  des commandes plus ergonomiques (`k3d cluster create/stop/delete`).
- **k3s direct** : pas de gestion multi-cluster pratique, nécessite systemd.

### Ory self-hosted
- **Ory Network (SaaS)** : écarté — dépendance cloud, pas de contrôle local.
- **Binaires natifs Ory** : écarté — configuration manuelle fastidieuse,
  pas d'orchestration, pas scalable.

## Conséquences

- Docker Engine doit être installé sur chaque poste de dev (script fourni
  dans `docs/runbook/install-toolchain.md`).
- Le cluster local k3d expose les services Ory via port-forward ou ingress.
- Helm v4 gère les charts officiels Ory pour Kratos/Hydra ; Keto déployé via
  manifests maison (chart communautaire immature — voir ADR à venir).
- Versions installées figées dans le runbook pour reproductibilité.