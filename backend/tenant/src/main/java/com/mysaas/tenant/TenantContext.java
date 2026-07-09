package com.mysaas.tenant;

/**
 * Contexte tenant propagé dans le RoutingContext pendant le traitement d'une requête.
 *
 * <p>Stocké via {@code ctx.put("tenant", tenantContext)} dans le filtre tenant.
 */
public record TenantContext(String slug, String schemaName, String displayName) {

  /**
   * Crée un TenantContext à partir du slug du tenant.
   *
   * @param slug le slug du tenant (ex: "acme")
   */
  public static TenantContext of(String slug) {
    return new TenantContext(slug, schemaNameFor(slug), slug);
  }

  /**
   * Construit le nom du schéma Postgres pour un tenant.
   *
   * @param slug le slug du tenant
   * @return le nom du schéma (ex: "tenant_acme")
   */
  public static String schemaNameFor(String slug) {
    return "tenant_" + slug;
  }
}
