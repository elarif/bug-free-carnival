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
 * Implémentation JDBC du {@link TenantRegistry} — pour la prod.
 *
 * <p>Stocke les tenants dans la table {@code public.tenants} (créée par Liquibase via {@link
 * TenantDbMigrator}). Schéma de la table :
 *
 * <pre>
 * CREATE TABLE public.tenants (
 *   slug         TEXT PRIMARY KEY,
 *   schema_name  TEXT NOT NULL,
 *   display_name TEXT NOT NULL,
 *   created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
 * );
 * </pre>
 */
public final class JdbcTenantRegistry implements TenantRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcTenantRegistry.class);

  private final DataSource dataSource;

  public JdbcTenantRegistry(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public TenantContext find(String slug) {
    if (slug == null || slug.isBlank()) {
      return null;
    }
    String sql = "SELECT schema_name, display_name FROM public.tenants WHERE slug = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, slug);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return new TenantContext(slug, rs.getString("schema_name"), rs.getString("display_name"));
        }
      }
      return null;
    } catch (SQLException e) {
      throw new TenantException("Échec recherche tenant " + slug, e);
    }
  }

  @Override
  public List<TenantContext> findAll() {
    String sql = "SELECT slug, schema_name, display_name FROM public.tenants ORDER BY slug";
    List<TenantContext> list = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      while (rs.next()) {
        list.add(
            new TenantContext(
                rs.getString("slug"), rs.getString("schema_name"), rs.getString("display_name")));
      }
      return list;
    } catch (SQLException e) {
      throw new TenantException("Échec listage des tenants", e);
    }
  }

  @Override
  public TenantContext create(String slug, String displayName) {
    TenantContext tenant = new TenantContext(slug, TenantContext.schemaNameFor(slug), displayName);
    String sql = "INSERT INTO public.tenants (slug, schema_name, display_name) VALUES (?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tenant.slug());
      ps.setString(2, tenant.schemaName());
      ps.setString(3, tenant.displayName());
      ps.executeUpdate();
      LOG.info("Tenant {} enregistré dans public.tenants", tenant.slug());
      return tenant;
    } catch (SQLException e) {
      throw new TenantException("Échec création tenant " + slug, e);
    }
  }

  @Override
  public boolean delete(String slug) {
    String sql = "DELETE FROM public.tenants WHERE slug = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, slug);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new TenantException("Échec suppression tenant " + slug, e);
    }
  }
}
