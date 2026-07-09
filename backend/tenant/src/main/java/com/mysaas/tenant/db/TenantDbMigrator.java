package com.mysaas.tenant.db;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrator Liquibase pour le schéma {@code public} — crée la table {@code public.tenants} (registry
 * des tenants).
 *
 * <p>Les migrations par schéma tenant ({@code tenant_<slug>}) seront ajoutées en Phase 5+. En Phase
 * 4, seule la table registry partagée est migrée.
 */
public final class TenantDbMigrator {

  private static final Logger LOG = LoggerFactory.getLogger(TenantDbMigrator.class);

  private static final String CHANGELOG = "com/mysaas/tenant/db/tenants-changelog.xml";

  private final DataSource dataSource;

  public TenantDbMigrator(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Applique les migrations Liquibase sur le schéma {@code public}. */
  public void migrate() {
    try (Connection conn = dataSource.getConnection()) {
      liquibase.Liquibase liquibase =
          new liquibase.Liquibase(
              CHANGELOG,
              new liquibase.resource.ClassLoaderResourceAccessor(),
              new liquibase.database.jvm.JdbcConnection(conn));
      liquibase.update();
      LOG.info("Migrations public.tenants appliquées");
    } catch (Exception e) {
      throw new RuntimeException("Échec des migrations public.tenants", e);
    }
  }
}
