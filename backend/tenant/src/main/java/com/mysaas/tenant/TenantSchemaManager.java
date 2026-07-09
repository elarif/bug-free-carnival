package com.mysaas.tenant;

import java.util.List;

/**
 * Gestionnaire des schémas Postgres par tenant.
 *
 * <p>Crée le schéma {@code tenant_<slug>} pour chaque tenant. En Phase 4, la création est simple
 * (CREATE SCHEMA). Les migrations Liquibase par schéma seront ajoutées en Phase 5+.
 *
 * <p>Implémentations :
 *
 * <ul>
 *   <li>{@link JdbcTenantSchemaManager} — pour la prod (DataSource HikariCP)
 *   <li>Fake in-memory — pour les tests (enregistre les schémas créés dans un Set)
 * </ul>
 */
public interface TenantSchemaManager {

  /**
   * Crée le schéma Postgres pour un tenant s'il n'existe pas déjà.
   *
   * @param tenant le tenant dont le schéma doit être créé
   * @return true si le schéma a été créé, false s'il existait déjà
   */
  boolean createSchemaIfNeeded(TenantContext tenant);

  /**
   * Vérifie si le schéma d'un tenant existe.
   *
   * @param tenant le tenant
   * @return true si le schéma existe
   */
  boolean schemaExists(TenantContext tenant);

  /**
   * Liste tous les schémas tenant_* présents dans Postgres.
   *
   * @return la liste des noms de schémas tenant
   */
  List<String> listTenantSchemas();
}
