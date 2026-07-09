package com.mysaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests de contrat API pour la couche tenant : filtre tenant + CRUD admin + création de schéma.
 *
 * <p>Utilise {@link InMemoryTenantRegistry} et {@link InMemoryTenantSchemaManager} (pas de DB) mais
 * exerce les vraies routes HTTP via {@link TenantFilter} et {@link TenantAdminHandler}.
 */
@ExtendWith(VertxExtension.class)
class TenantAdminContractTest {

  private WebClient client;
  private HttpServer server;
  private InMemoryTenantSchemaManager schemaManager;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    InMemoryTenantRegistry registry = new InMemoryTenantRegistry();
    schemaManager = new InMemoryTenantSchemaManager();
    new TenantFilter(new TenantResolver(registry)).mount(router);
    new TenantAdminHandler(registry, schemaManager).mount(router);

    // Route protégée par le filtre tenant (pour valider la résolution)
    router
        .get("/api/ping")
        .handler(
            c -> {
              TenantContext t = c.get(TenantResolver.CTX_KEY);
              c.response().end("{\"tenant\":\"" + t.slug() + "\"}");
            });

    client = WebClient.create(vertx, new WebClientOptions());
    server = vertx.createHttpServer();
    server
        .requestHandler(router)
        .listen(0)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                ctx.completeNow();
              } else {
                ctx.failNow(ar.cause());
              }
            });
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void listTenantsReturnsSeededTenants(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/admin/tenants")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      var body = ar.result().bodyAsJsonObject();
                      assertThat(body.getJsonArray("tenants").size()).isGreaterThanOrEqualTo(3);
                      ctx.completeNow();
                    }));
  }

  @Test
  void createTenantReturns201AndCreatesSchema(Vertx vertx, VertxTestContext ctx) {
    client
        .post(server.actualPort(), "localhost", "/admin/tenants")
        .sendJsonObject(
            new io.vertx.core.json.JsonObject().put("slug", "globex").put("display_name", "Globex"))
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(201);
                      var body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("slug")).isEqualTo("globex");
                      assertThat(body.getString("schema")).isEqualTo("tenant_globex");
                      assertThat(schemaManager.createdSchemas()).contains("tenant_globex");
                      ctx.completeNow();
                    }));
  }

  @Test
  void createTenantRejectsInvalidSlug(Vertx vertx, VertxTestContext ctx) {
    client
        .post(server.actualPort(), "localhost", "/admin/tenants")
        .sendJsonObject(new io.vertx.core.json.JsonObject().put("slug", "Bad Slug!"))
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(400);
                      ctx.completeNow();
                    }));
  }

  @Test
  void createDuplicateReturns409(Vertx vertx, VertxTestContext ctx) {
    client
        .post(server.actualPort(), "localhost", "/admin/tenants")
        .sendJsonObject(new io.vertx.core.json.JsonObject().put("slug", "acme"))
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(409);
                      ctx.completeNow();
                    }));
  }

  @Test
  void getTenantReturnsDetails(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/admin/tenants/acme")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      assertThat(ar.result().bodyAsJsonObject().getString("slug"))
                          .isEqualTo("acme");
                      ctx.completeNow();
                    }));
  }

  @Test
  void deleteTenantReturns204(Vertx vertx, VertxTestContext ctx) {
    client
        .delete(server.actualPort(), "localhost", "/admin/tenants/demo")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(204);
                      ctx.completeNow();
                    }));
  }

  @Test
  void protectedRouteWithTenantResolves(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/api/ping")
        .putHeader("X-Tenant", "acme")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      assertThat(ar.result().bodyAsJsonObject().getString("tenant"))
                          .isEqualTo("acme");
                      ctx.completeNow();
                    }));
  }

  @Test
  void protectedRouteWithoutTenantReturns404(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/api/ping")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(404);
                      ctx.completeNow();
                    }));
  }
}
