package com.mysaas.tenant;

import com.mysaas.tenant.db.TenantDbMigrator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Factory des composants tenant pour la prod — assemble la {@link DataSource} HikariCP, exécute les
 * migrations {@code public.tenants} et expose le {@link TenantRegistry} et le {@link
 * TenantSchemaManager} JDBC.
 *
 * <p>Usage depuis le module api :
 *
 * <pre>
 * TenantComponents tenants = TenantComponents.fromEnv(config);
 * tenants.migrate();
 * TenantRegistry registry = tenants.registry();
 * TenantSchemaManager schemas = tenants.schemaManager();
 * </pre>
 */
public final class TenantComponents {

  private final HikariDataSource dataSource;
  private final JdbcTenantRegistry registry;
  private final JdbcTenantSchemaManager schemaManager;
  private final TenantDbMigrator migrator;

  public TenantComponents(HikariDataSource dataSource) {
    this.dataSource = dataSource;
    this.registry = new JdbcTenantRegistry(dataSource);
    this.schemaManager = new JdbcTenantSchemaManager(dataSource);
    this.migrator = new TenantDbMigrator(dataSource);
  }

  /** Construit les composants tenant depuis les paramètres de config JDBC. */
  public static TenantComponents create(String jdbcUrl, String dbUser, String dbPassword) {
    HikariConfig hc = new HikariConfig();
    hc.setJdbcUrl(jdbcUrl);
    hc.setUsername(dbUser);
    hc.setPassword(dbPassword);
    hc.setPoolName("mysaas-tenant");
    hc.setMaximumPoolSize(5);
    hc.setMinimumIdle(1);
    return new TenantComponents(new HikariDataSource(hc));
  }

  /** Applique les migrations Liquibase (public.tenants) au démarrage. */
  public void migrate() {
    migrator.migrate();
  }

  public TenantRegistry registry() {
    return registry;
  }

  public TenantSchemaManager schemaManager() {
    return schemaManager;
  }

  public DataSource dataSource() {
    return dataSource;
  }

  /** Ferme le pool HikariCP. */
  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
  }
}
