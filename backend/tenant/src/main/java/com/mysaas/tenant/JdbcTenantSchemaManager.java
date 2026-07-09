package com.mysaas.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestionnaire de schémas Postgres par tenant — implémentation JDBC via une {@link DataSource}
 * (HikariCP en prod).
 */
public final class JdbcTenantSchemaManager implements TenantSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcTenantSchemaManager.class);

  private final DataSource dataSource;

  public JdbcTenantSchemaManager(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean createSchemaIfNeeded(TenantContext tenant) {
    String schema = tenant.schemaName();
    try (Connection conn = dataSource.getConnection()) {
      if (schemaExists(conn, schema)) {
        LOG.debug("Schéma {} existe déjà", schema);
        return false;
      }
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE SCHEMA " + schema);
        LOG.info("Schéma {} créé pour le tenant {}", schema, tenant.slug());
        return true;
      }
    } catch (SQLException e) {
      throw new TenantException("Échec création schéma " + schema, e);
    }
  }

  @Override
  public boolean schemaExists(TenantContext tenant) {
    try (Connection conn = dataSource.getConnection()) {
      return schemaExists(conn, tenant.schemaName());
    } catch (SQLException e) {
      throw new TenantException("Échec vérification schéma " + tenant.schemaName(), e);
    }
  }

  @Override
  public List<String> listTenantSchemas() {
    List<String> schemas = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT schema_name FROM information_schema.schemata"
                    + " WHERE schema_name LIKE 'tenant_%' ORDER BY schema_name")) {
      while (rs.next()) {
        schemas.add(rs.getString(1));
      }
    } catch (SQLException e) {
      throw new TenantException("Échec du listage des schémas tenant", e);
    }
    return schemas;
  }

  private boolean schemaExists(Connection conn, String schema) throws SQLException {
    try (PreparedStatement ps =
        conn.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
      ps.setString(1, schema);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }
}
