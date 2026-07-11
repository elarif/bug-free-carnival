package com.mysaas.tenant;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filtre tenant — résout le tenant depuis la requête et l'injecte dans le RoutingContext.
 *
 * <p>Monté sur le router avant les autres handlers. Si le tenant est introuvable :
 *
 * <ul>
 *   <li>Sur les endpoints protégés → 404 (tenant non trouvé)
 *   <li>Sur /health et /ready → passe (pas de tenant requis)
 * </ul>
 */
public final class TenantFilter {

  private static final Logger LOG = LoggerFactory.getLogger(TenantFilter.class);

  /** Endpoints qui ne nécessitent pas de tenant. */
  private static final java.util.Set<String> EXEMPT_PATHS = java.util.Set.of("/health", "/ready");

  /** Préfixes d'endpoints qui ne nécessitent pas de tenant (ex: admin global, webhooks Kratos). */
  private static final java.util.Set<String> EXEMPT_PREFIXES =
      java.util.Set.of("/admin/", "/webhooks/");

  private final TenantResolver resolver;

  public TenantFilter(TenantResolver resolver) {
    this.resolver = resolver;
  }

  /** Monte le filtre tenant sur le router. */
  public void mount(Router router) {
    router.route().order(-100).handler(this::handle);
  }

  void handle(RoutingContext ctx) {
    String path = ctx.normalizedPath();

    // Les endpoints de health n'ont pas besoin de tenant
    if (EXEMPT_PATHS.contains(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
      ctx.next();
      return;
    }

    String host = ctx.request().getHeader("Host");
    String tenantHeader = ctx.request().getHeader(TenantResolver.TENANT_HEADER);

    LOG.debug("Résolution tenant: host={}, X-Tenant={}", host, tenantHeader);

    TenantContext tenant = resolver.resolve(host, tenantHeader);

    if (tenant == null) {
      LOG.warn("Tenant non résolu: host={}, X-Tenant={}", host, tenantHeader);
      ctx.response()
          .setStatusCode(404)
          .putHeader("Content-Type", "application/json")
          .end(
              new JsonObject()
                  .put("error", "tenant_not_found")
                  .put("message", "Tenant introuvable")
                  .encode());
      return;
    }

    LOG.debug("Tenant résolu: {} → schéma {}", tenant.slug(), tenant.schemaName());
    ctx.put(TenantResolver.CTX_KEY, tenant);
    ctx.next();
  }
}
