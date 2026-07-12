package com.mysaas.authz;

import com.mysaas.identity.Identity;
import com.mysaas.oauth.HydraTokenFilter;
import com.mysaas.oauth.TokenPrincipal;
import com.mysaas.tenant.TenantContext;
import com.mysaas.tenant.TenantResolver;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filtre d'autorisation Keto — combine le {@link TenantContext} et l'{@link Identity} (ou le {@link
 * TokenPrincipal}) pour vérifier les permissions via Ory Keto.
 *
 * <p>Monté sur le router (order -70, après tenant -100, identity -90, oauth -80). Pour chaque
 * requête sur un endpoint protégé :
 *
 * <ol>
 *   <li>Récupère le {@link TenantContext} injecté par {@code TenantFilter}
 *   <li>Récupère le subject depuis {@link Identity} (session Kratos) ou {@link TokenPrincipal} (JWT
 *       Hydra)
 *   <li>Vérifie le tuple {@code Tenant:<slug>#access@<subject>} via {@link KetoClient}
 *   <li>Si allowed → {@code ctx.next()} ; si denied → 403 ; si Keto injoignable → 503
 * </ol>
 *
 * <p>Les endpoints {@code /health}, {@code /ready}, {@code /admin/*}, {@code /webhooks/*} sont
 * exemptés.
 */
public final class KetoAuthzFilter {

  private static final Logger LOG = LoggerFactory.getLogger(KetoAuthzFilter.class);

  /** Namespace Keto pour les tenants. */
  public static final String NAMESPACE_TENANT = "Tenant";

  /** Relation par défaut vérifiée par le filtre. */
  public static final String DEFAULT_RELATION = "access";

  private static final java.util.Set<String> EXEMPT_PATHS = java.util.Set.of("/health", "/ready");

  private static final java.util.Set<String> EXEMPT_PREFIXES =
      java.util.Set.of("/admin/", "/webhooks/");

  private final KetoClient ketoClient;
  private final String relation;

  public KetoAuthzFilter(KetoClient ketoClient) {
    this(ketoClient, DEFAULT_RELATION);
  }

  public KetoAuthzFilter(KetoClient ketoClient, String relation) {
    this.ketoClient = ketoClient;
    this.relation = relation;
  }

  public void mount(Router router) {
    router.route().order(-70).handler(this::handle);
  }

  void handle(RoutingContext ctx) {
    String path = ctx.normalizedPath();

    if (EXEMPT_PATHS.contains(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
      ctx.next();
      return;
    }

    TenantContext tenant = ctx.get(TenantResolver.CTX_KEY);
    if (tenant == null) {
      LOG.debug("Authz: pas de tenant dans le contexte — skip (le TenantFilter a dû répondre 404)");
      ctx.next();
      return;
    }

    String subjectId = resolveSubjectId(ctx);
    if (subjectId == null) {
      LOG.debug("Authz: pas d'identity ni de token principal — 403");
      ctx.response()
          .setStatusCode(403)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "forbidden").encode());
      return;
    }

    LOG.debug("Authz: check Tenant:{}#{}@{}", tenant.slug(), relation, subjectId);
    ketoClient
        .check(NAMESPACE_TENANT, tenant.slug(), relation, subjectId)
        .onSuccess(
            allowed -> {
              if (allowed) {
                LOG.debug("Authz: accès autorisé à Tenant:{}", tenant.slug());
                ctx.next();
              } else {
                LOG.debug("Authz: accès refusé à Tenant:{} pour {}", tenant.slug(), subjectId);
                ctx.response()
                    .setStatusCode(403)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "forbidden").encode());
              }
            })
        .onFailure(
            err -> {
              LOG.warn("Authz: Keto injoignable: {}", err.getMessage());
              ctx.response()
                  .setStatusCode(503)
                  .putHeader("Content-Type", "application/json")
                  .end(new JsonObject().put("error", "authz_service_unavailable").encode());
            });
  }

  /** Résout le subject ID depuis l'Identity (Kratos) ou le TokenPrincipal (Hydra). */
  static String resolveSubjectId(RoutingContext ctx) {
    Identity identity = ctx.get(com.mysaas.identity.KratosSessionFilter.CTX_KEY);
    if (identity != null) {
      return identity.id();
    }
    TokenPrincipal principal = ctx.get(HydraTokenFilter.CTX_KEY);
    if (principal != null) {
      return principal.subject();
    }
    return null;
  }
}
