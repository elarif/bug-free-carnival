package com.mysaas.oauth;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filtre de token Hydra — extrait le Bearer token du header {@code Authorization}, le valide via
 * JWKS (JWT local) avec fallback introspection (token opaque), et injecte le {@link TokenPrincipal}
 * dans le {@link RoutingContext}.
 *
 * <p>Monté sur le router (order -80, après KratosSessionFilter à -90). Si le token est valide →
 * injection du principal. Si invalide → 401. Si Hydra injoignable → 503. Les endpoints {@code
 * /health}, {@code /ready}, {@code /admin/*}, {@code /webhooks/*} sont exemptés.
 */
public final class HydraTokenFilter {

  private static final Logger LOG = LoggerFactory.getLogger(HydraTokenFilter.class);

  /** Clé utilisée pour stocker le TokenPrincipal dans le RoutingContext. */
  public static final String CTX_KEY = "tokenPrincipal";

  private static final String BEARER_PREFIX = "Bearer ";

  private static final java.util.Set<String> EXEMPT_PATHS = java.util.Set.of("/health", "/ready");

  private static final java.util.Set<String> EXEMPT_PREFIXES =
      java.util.Set.of("/admin/", "/webhooks/");

  private final HydraTokenValidator validator;
  private final HydraIntrospectionClient introspectionClient;

  public HydraTokenFilter(
      HydraTokenValidator validator, HydraIntrospectionClient introspectionClient) {
    this.validator = validator;
    this.introspectionClient = introspectionClient;
  }

  public void mount(Router router) {
    router.route().order(-80).handler(this::handle);
  }

  void handle(RoutingContext ctx) {
    String path = ctx.normalizedPath();

    if (EXEMPT_PATHS.contains(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
      ctx.next();
      return;
    }

    String authHeader = ctx.request().getHeader("Authorization");
    String token = extractBearerToken(authHeader);
    if (token == null) {
      LOG.debug("Pas de Bearer token dans Authorization: {}", authHeader);
      ctx.response()
          .setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "unauthorized").encode());
      return;
    }

    LOG.debug("Validation Bearer token");
    validator
        .validate(token)
        .onFailure(err -> introspectFallback(token, err, ctx))
        .onSuccess(principal -> injectAndNext(principal, ctx));
  }

  private void introspectFallback(String token, Throwable validationError, RoutingContext ctx) {
    if (validationError instanceof TokenValidationException tve && tve.statusCode() == 401) {
      LOG.debug("JWT invalide, fallback introspection: {}", tve.getMessage());
      introspectionClient
          .introspect(token)
          .onSuccess(principal -> injectAndNext(principal, ctx))
          .onFailure(err -> reject(err, ctx));
    } else {
      reject(validationError, ctx);
    }
  }

  private void injectAndNext(TokenPrincipal principal, RoutingContext ctx) {
    LOG.debug("Token valide: subject={}, tenant={}", principal.subject(), principal.tenantId());
    ctx.put(CTX_KEY, principal);
    ctx.next();
  }

  private void reject(Throwable err, RoutingContext ctx) {
    if (err instanceof TokenValidationException tve) {
      int status = tve.statusCode();
      if (status == 401) {
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "unauthorized").encode());
      } else {
        LOG.warn("Hydra injoignable (HTTP {}): {}", status, tve.getMessage());
        ctx.response()
            .setStatusCode(503)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", "oauth_service_unavailable").encode());
      }
    } else {
      LOG.error("Erreur inattendue lors de la validation du token", err);
      ctx.response()
          .setStatusCode(503)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "oauth_service_unavailable").encode());
    }
  }

  /** Extrait le token du header Authorization (format "Bearer <token>"). */
  static String extractBearerToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    return token.isEmpty() ? null : token;
  }
}
