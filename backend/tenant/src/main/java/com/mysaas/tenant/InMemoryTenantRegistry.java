package com.mysaas.tenant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Implémentation in-memory du TenantRegistry — pour tests et dev. */
public final class InMemoryTenantRegistry implements TenantRegistry {

  private final Map<String, TenantContext> tenants = new ConcurrentHashMap<>();

  public InMemoryTenantRegistry() {
    // Tenant par défaut pour le dev
    register("default", "Default Tenant");
    register("acme", "Acme Corp");
    register("demo", "Demo Tenant");
  }

  @Override
  public TenantContext find(String slug) {
    return tenants.get(slug);
  }

  @Override
  public List<TenantContext> findAll() {
    return List.copyOf(tenants.values());
  }

  @Override
  public TenantContext create(String slug, String displayName) {
    TenantContext ctx = new TenantContext(slug, TenantContext.schemaNameFor(slug), displayName);
    tenants.put(slug, ctx);
    return ctx;
  }

  @Override
  public boolean delete(String slug) {
    return tenants.remove(slug) != null;
  }

  /** Enregistre un tenant (alias de create, pour le setup). */
  public void register(String slug, String displayName) {
    create(slug, displayName);
  }
}
