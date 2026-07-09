package com.mysaas.tenant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implémentation in-memory du {@link TenantSchemaManager} — pour les tests.
 *
 * <p>Enregistre les schémas créés dans un {@link Set} et les expose pour les assertions de test.
 */
public final class InMemoryTenantSchemaManager implements TenantSchemaManager {

  private final Set<String> schemas = new LinkedHashSet<>();

  @Override
  public boolean createSchemaIfNeeded(TenantContext tenant) {
    return schemas.add(tenant.schemaName());
  }

  @Override
  public boolean schemaExists(TenantContext tenant) {
    return schemas.contains(tenant.schemaName());
  }

  @Override
  public List<String> listTenantSchemas() {
    return List.copyOf(schemas);
  }

  /** Expose les schémas créés pour les assertions de test. */
  public Set<String> createdSchemas() {
    return Set.copyOf(schemas);
  }
}
