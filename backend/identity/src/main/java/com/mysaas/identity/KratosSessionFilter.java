package com.mysaas.identity;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filtre de session Kratos — valide le cookie de session Ory Kratos sur chaque requête et injecte
 * l'{@link Identity} courante dans le {@link RoutingContext}.
 *
 * <p>Monté sur le router après le {@code TenantFilter} (order -90). Si la session est invalide ou
 * absente → 401. Si Kratos est injoignable → 503. Les endpoints {@code /health}, {@code /ready} et
 * {@code /admin/*} sont exemptés.
 */
public final class KratosSessionFilter {

  private static final Logger LOG = LoggerFactory.getLogger(KratosSessionFilter.class);

  /** Clé utilisée pour stocker l'Identity dans le RoutingContext. */
  public static final String CTX_KEY = "identity";

  /** Endpoints qui ne nécessitent pas de session Kratos. */
  private static final java.util.Set<String> EXEMPT_PATHS = java.util.Set.of("/health", "/ready");

  /** Préfixes d'endpoints exemptés (ex: admin global, webhooks Kratos). */
  private static final java.util.Set<String> EXEMPT_PREFIXES =
      java.util.Set.of("/admin/", "/webhooks/");

  private final KratosClient kratosClient;

  public KratosSessionFilter(KratosClient kratosClient) {
    this.kratosClient = kratosClient;
  }

  /** Monte le filtre sur le router (order -90, après TenantFilter à -100). */
  public void mount(Router router) {
    router.route().order(-90).handler(this::handle);
  }

  void handle(RoutingContext ctx) {
    String path = ctx.normalizedPath();

    if (EXEMPT_PATHS.contains(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
      ctx.next();
      return;
    }

    String cookie = ctx.request().getHeader("Cookie");
    LOG.debug("Validation session Kratos: path={}, cookie présent={}", path, cookie != null);

    kratosClient
        .whoami(cookie)
        .onSuccess(
            identity -> {
              LOG.debug("Session valide: id={}, tenant={}", identity.id(), identity.tenantId());
              ctx.put(CTX_KEY, identity);
              ctx.next();
            })
        .onFailure(
            err -> {
              if (err instanceof KratosSessionException kse) {
                int status = kse.statusCode();
                if (status == 401 || status == 403) {
                  LOG.debug("Session invalide (HTTP {})", status);
                  ctx.response()
                      .setStatusCode(401)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", "unauthorized").encode());
                } else {
                  LOG.warn("Kratos injoignable (HTTP {}): {}", status, kse.getMessage());
                  ctx.response()
                      .setStatusCode(503)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", "identity_service_unavailable").encode());
                }
              } else {
                LOG.error("Erreur inattendue lors de la validation de session", err);
                ctx.response()
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "identity_service_unavailable").encode());
              }
            });
  }
}
