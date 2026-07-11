package com.mysaas.identity;

import com.mysaas.tenant.TenantContext;
import com.mysaas.tenant.TenantRegistry;
import com.mysaas.tenant.TenantSchemaManager;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Webhook Kratos appelé après une inscription réussie.
 *
 * <p>Route: {@code POST /webhooks/kratos/after-registration}. Kratos envoie le payload de
 * l'identité créée (avec ses traits). Le handler provisionne un tenant par défaut si l'utilisateur
 * n'en a pas un :
 *
 * <ul>
 *   <li>Si le trait {@code tenant_id} est présent et que le tenant existe déjà → 204 (rien à faire)
 *   <li>Si le trait {@code tenant_id} est présent mais le tenant n'existe pas → crée le tenant +
 *       schéma → 200
 *   <li>Si le trait {@code tenant_id} est absent → provisionne un tenant par défaut dérivé du
 *       domaine de l'email (ex: {@code alice@globex.com} → {@code globex}), ou {@code default} si
 *       l'email est absent → 200
 * </ul>
 */
public final class RegistrationWebhookHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationWebhookHandler.class);

  private final TenantRegistry registry;
  private final TenantSchemaManager schemaManager;

  public RegistrationWebhookHandler(TenantRegistry registry, TenantSchemaManager schemaManager) {
    this.registry = registry;
    this.schemaManager = schemaManager;
  }

  public void mount(Router router) {
    router.post("/webhooks/kratos/after-registration").handler(this::afterRegistration);
  }

  void afterRegistration(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "missing_body").encode());
      return;
    }

    JsonObject identity = body.getJsonObject("identity");
    if (identity == null) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "missing_identity").encode());
      return;
    }

    JsonObject traits = identity.getJsonObject("traits", new JsonObject());
    String tenantId = traits.getString("tenant_id");
    String email = traits.getString("email");

    String slug = resolveTenantSlug(tenantId, email);
    if (slug == null) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "cannot_resolve_tenant").encode());
      return;
    }

    if (registry.exists(slug)) {
      LOG.debug("Webhook after-registration: tenant {} existe déjà → rien à faire", slug);
      ctx.response().setStatusCode(204).end();
      return;
    }

    TenantContext tenant = registry.create(slug, slug);
    schemaManager.createSchemaIfNeeded(tenant);
    LOG.info(
        "Webhook after-registration: tenant {} provisionné (identity={})",
        slug,
        identity.getString("id"));

    ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(
            new JsonObject()
                .put("action", "tenant_provisioned")
                .put("tenant", slug)
                .put("schema", tenant.schemaName())
                .encode());
  }

  /**
   * Résout le slug du tenant à provisionner. Priorité : trait tenant_id > domaine de l'email >
   * "default".
   */
  static String resolveTenantSlug(String tenantId, String email) {
    if (tenantId != null && !tenantId.isBlank()) {
      return tenantId.trim().toLowerCase();
    }
    if (email != null && !email.isBlank()) {
      int at = email.indexOf('@');
      if (at > 0 && at < email.length() - 1) {
        String domain = email.substring(at + 1).toLowerCase();
        int dot = domain.indexOf('.');
        if (dot > 0) {
          return domain.substring(0, dot);
        }
        return domain;
      }
    }
    return "default";
  }
}
