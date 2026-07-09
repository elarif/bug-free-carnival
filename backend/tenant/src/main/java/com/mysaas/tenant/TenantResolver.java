package com.mysaas.tenant;

/**
 * Résout le tenant depuis la requête HTTP.
 *
 * <p>Ordre de priorité :
 *
 * <ol>
 *   <li>Header {@code X-Tenant} (pour les tests et API directes)
 *   <li>Sous-domaine du Host (ex: {@code acme.app.local} → "acme")
 * </ol>
 */
public final class TenantResolver {

  /** Header HTTP pour spécifier le tenant explicitement. */
  public static final String TENANT_HEADER = "X-Tenant";

  /** Clé utilisée pour stocker le TenantContext dans le RoutingContext. */
  public static final String CTX_KEY = "tenant";

  private final TenantRegistry registry;

  public TenantResolver(TenantRegistry registry) {
    this.registry = registry;
  }

  /**
   * Résout le tenant depuis le host et les headers.
   *
   * @param host le header Host de la requête (ex: "acme.app.local:8080")
   * @param tenantHeader la valeur du header X-Tenant (null si absent)
   * @return le TenantContext si le tenant existe, null sinon
   */
  public TenantContext resolve(String host, String tenantHeader) {
    String slug = extractSlug(host, tenantHeader);
    if (slug == null || slug.isBlank()) {
      return null;
    }
    return registry.find(slug);
  }

  /** Extrait le slug du tenant depuis le header X-Tenant ou le sous-domaine. */
  static String extractSlug(String host, String tenantHeader) {
    // Priorité 1: header X-Tenant
    if (tenantHeader != null && !tenantHeader.isBlank()) {
      return tenantHeader.trim().toLowerCase();
    }

    // Priorité 2: sous-domaine du Host
    if (host == null || host.isBlank()) {
      return null;
    }

    // Retirer le port du host
    String hostname = host.split(":")[0];

    // Extraire le premier segment (sous-domaine)
    String[] parts = hostname.split("\\.");
    if (parts.length < 2) {
      return null;
    }

    // Ignorer localhost et IPs
    if ("localhost".equalsIgnoreCase(hostname) || hostname.matches("^[0-9.]+$")) {
      return null;
    }

    return parts[0].toLowerCase();
  }
}
