package com.mysaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class TenantFilterTest {

  private WebClient client;
  private HttpServer server;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    Router router = Router.router(vertx);

    TenantRegistry registry = new InMemoryTenantRegistry();
    TenantFilter filter = new TenantFilter(new TenantResolver(registry));
    filter.mount(router);

    // Route de test qui retourne le tenant résolu
    router
        .get("/api/test")
        .handler(
            routingCtx -> {
              TenantContext tenant = routingCtx.get(TenantResolver.CTX_KEY);
              if (tenant != null) {
                routingCtx
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"tenant\":\"" + tenant.slug() + "\"}");
              } else {
                routingCtx.response().setStatusCode(500).end("{\"error\":\"no_tenant\"}");
              }
            });

    // /health exempté du filtre tenant
    router.get("/health").handler(c -> c.response().end("{\"status\":\"UP\"}"));

    // /admin/* exempté du filtre tenant
    router.get("/admin/tenants").handler(c -> c.response().end("{\"tenants\":[]}"));

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
  void requestWithXTenantHeaderPasses(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/api/test")
        .putHeader("X-Tenant", "acme")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result().statusCode()).isEqualTo(200);
                    assertThat(ar.result().bodyAsJsonObject().getString("tenant"))
                        .isEqualTo("acme");
                  });
              ctx.completeNow();
            });
  }

  @Test
  void requestWithoutTenantReturns404(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/api/test")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result().statusCode()).isEqualTo(404);
                  });
              ctx.completeNow();
            });
  }

  @Test
  void requestWithUnknownTenantReturns404(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/api/test")
        .putHeader("X-Tenant", "unknown")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result().statusCode()).isEqualTo(404);
                  });
              ctx.completeNow();
            });
  }

  @Test
  void healthEndpointBypassesTenantFilter(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/health")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result().statusCode()).isEqualTo(200);
                  });
              ctx.completeNow();
            });
  }

  @Test
  void adminEndpointsBypassTenantFilter(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/admin/tenants")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    assertThat(ar.result().statusCode()).isEqualTo(200);
                  });
              ctx.completeNow();
            });
  }
}
