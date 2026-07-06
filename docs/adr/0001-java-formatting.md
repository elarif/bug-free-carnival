# ADR 0001 — Formatage Java : google-java-format via wrapper gjf

Date: 2026-07-06
Statut: Accepté

## Contexte

Le projet utilise Java 25 (Temurin 25.0.2). Le formatage Java doit être automatisé
et vérifié pendant `mvn verify`.

Spotless (maven-plugin 2.44.3) embarque google-java-format 1.25.2, qui dépend de
l'API interne `com.sun.tools.javac.util.Log$DeferredDiagnosticHandler`. Cette API a
changé dans Java 25 — `NoSuchMethodError: getDiagnostics()`.

Testé :
- `googleJavaFormat` (Spotless) → `NoSuchMethodError` (Java 25)
- `palantirJavaFormat` (Spotless) → même erreur (utilise aussi javac en interne)
- `eclipse` formatter (Spotless) → fonctionne mais style différent de Google

## Décision

Utiliser **google-java-format** via un wrapper maison (`infra/scripts/gjf`) qui :
1. Télécharge la dernière version du JAR `google-java-format-*-all-deps.jar` dans
   `~/.config/google-java-format/` (cache local, hors repo)
2. Supporte `--check` (vérification, exit 1 si non conforme), formatage en place,
   `--update`, `--install <version>`
3. Accepte fichiers et répertoires (récursion sur `*.java`)
4. Est invoqué pendant `mvn verify` via `exec-maven-plugin` (goal `exec`)

Le JAR `all-deps` est autonome (embarque son propre javac parser) et fonctionne
avec Java 25 contrairement à la version embarquée par Spotless.

SortPom (`sortpom-maven-plugin` 4.0.0) remplace Spotless pour le tri des POMs.
Checkstyle est conservé pour les règles de style (hors ImportOrder, géré par gjf).

## Alternatives considérées

- **Spotless + eclipse formatter** : fonctionne, mais style Eclipse (pas Google).
  Conservé temporairement puis remplacé par gjf pour cohérence.
- **google-java-format CLI direct (sans wrapper)** : pas de cache, pas de gestion
  de version, pas d'intégration Maven propre.
- **Attendre une version de Spotless compatible Java 25** : bloquant, pas de visibilité.

## Conséquences

- Le wrapper `infra/scripts/gjf` doit être présent et exécutable sur chaque poste.
- Le cache `~/.config/google-java-format/` est créé au premier appel.
- `mvn verify` exécute `gjf --check` via `exec-maven-plugin`.
- Pour formater manuellement : `infra/scripts/gjf <fichiers ou répertoires>`.
- Spotless n'est plus utilisé dans aucun module.