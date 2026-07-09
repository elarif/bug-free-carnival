package com.mysaas.tenant;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoints d'administration des tenants.
 *
 * <p>Routes :
 *
 * <ul>
 *   <li>GET /admin/tenants — liste tous les tenants
 *   <li>POST /admin/tenants — crée un tenant (body: {slug, display_name})
 *   <li>GET /admin/tenants/:slug — détails d'un tenant
 *   <li>DELETE /admin/tenants/:slug — supprime un tenant
 * </ul>
 */
public final class TenantAdminHandler {

  private static final Logger LOG = LoggerFactory.getLogger(TenantAdminHandler.class);

  private final TenantRegistry registry;
  private final TenantSchemaManager schemaManager;

  public TenantAdminHandler(TenantRegistry registry, TenantSchemaManager schemaManager) {
    this.registry = registry;
    this.schemaManager = schemaManager;
  }

  /** Pattern de slug valide : alphanumérique + tirets, 2..63 chars, pas de tiret en bout. */
  static final java.util.regex.Pattern SLUG_PATTERN =
      java.util.regex.Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

  public void mount(Router router) {
    router.get("/admin/tenants").handler(this::listTenants);
    router.post("/admin/tenants").handler(this::createTenant);
    router.get("/admin/tenants/:slug").handler(this::getTenant);
    router.delete("/admin/tenants/:slug").handler(this::deleteTenant);
  }

  /** Valide le slug ; renvoie le message d'erreur si invalide, null si OK. */
  static String validateSlug(String slug) {
    if (slug == null || slug.isBlank()) {
      return "slug_required";
    }
    if (!SLUG_PATTERN.matcher(slug).matches()) {
      return "invalid_slug_format";
    }
    return null;
  }

  void listTenants(RoutingContext ctx) {
    JsonArray tenants = new JsonArray();
    registry.findAll().forEach(t -> tenants.add(toJson(t)));
    ctx.json(new JsonObject().put("tenants", tenants));
  }

  void createTenant(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String slug = body.getString("slug");
    String displayName = body.getString("display_name", slug);

    if (slug != null) {
      slug = slug.trim().toLowerCase();
    }

    String slugErr = validateSlug(slug);
    if (slugErr != null) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", slugErr).encode());
      return;
    }

    if (registry.exists(slug)) {
      ctx.response()
          .setStatusCode(409)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "tenant_already_exists").encode());
      return;
    }

    TenantContext tenant = registry.create(slug, displayName);
    schemaManager.createSchemaIfNeeded(tenant);
    LOG.info("Tenant créé: {} ({})", slug, displayName);

    ctx.response()
        .setStatusCode(201)
        .putHeader("Content-Type", "application/json")
        .end(toJson(tenant).encode());
  }

  void getTenant(RoutingContext ctx) {
    String slug = ctx.pathParam("slug");
    TenantContext tenant = registry.find(slug);

    if (tenant == null) {
      ctx.response()
          .setStatusCode(404)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "tenant_not_found").encode());
      return;
    }

    JsonObject json = toJson(tenant);
    json.put("schema_exists", schemaManager.schemaExists(tenant));
    ctx.json(json);
  }

  void deleteTenant(RoutingContext ctx) {
    String slug = ctx.pathParam("slug");
    boolean deleted = registry.delete(slug);

    if (!deleted) {
      ctx.response()
          .setStatusCode(404)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "tenant_not_found").encode());
      return;
    }

    LOG.info("Tenant supprimé: {}", slug);
    ctx.response().setStatusCode(204).end();
  }

  private static JsonObject toJson(TenantContext tenant) {
    return new JsonObject()
        .put("slug", tenant.slug())
        .put("schema", tenant.schemaName())
        .put("display_name", tenant.displayName());
  }
}
