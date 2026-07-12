package com.mysaas.authz;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mysaas.identity.Identity;
import com.mysaas.tenant.InMemoryTenantRegistry;
import com.mysaas.tenant.TenantFilter;
import com.mysaas.tenant.TenantResolver;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
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
class KetoAuthzFilterTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HttpServer server;
  private KetoClient ketoClient;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    wireMock = new WireMockServer(0);
    wireMock.start();

    webClient = WebClient.create(vertx);
    ketoClient = new KetoClient(webClient, "http://localhost:" + wireMock.port());

    InMemoryTenantRegistry registry = new InMemoryTenantRegistry();
    registry.register("globex", "Globex");

    Router router = Router.router(vertx);
    // Couche tenant (order -100)
    new TenantFilter(new TenantResolver(registry)).mount(router);
    // Handler de test (order -95) qui injecte une Identity simulée depuis X-Identity-Id
    router
        .route()
        .order(-95)
        .handler(
            c -> {
              String id = c.request().getHeader("X-Identity-Id");
              if (id != null) {
                c.put(
                    com.mysaas.identity.KratosSessionFilter.CTX_KEY,
                    new Identity(id, null, null, null));
              }
              c.next();
            });
    // Couche authz (order -70, après tenant -100 et identity -90)
    new KetoAuthzFilter(ketoClient).mount(router);

    // Route protégée par authz — nécessite tenant + identity + permission
    router
        .get("/api/data")
        .handler(
            c -> {
              c.response().end("{\"ok\":true}");
            });

    router.get("/health").handler(c -> c.response().end("{\"status\":\"UP\"}"));
    router.get("/admin/tenants").handler(c -> c.response().end("{\"tenants\":[]}"));

    WebClient testClient = WebClient.create(vertx, new WebClientOptions());
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
    if (webClient != null) {
      webClient.close();
    }
  }

  @Test
  void requestWithAllowedPermissionReturns200(Vertx vertx, VertxTestContext ctx) {
    stubKetoCheck("acme", "access", "user-1", true);

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Tenant", "acme")
        .putHeader("X-Identity-Id", "user-1")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      ctx.completeNow();
                    }));
  }

  @Test
  void requestWithDeniedPermissionReturns403(Vertx vertx, VertxTestContext ctx) {
    stubKetoCheck("globex", "access", "user-1", false);

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Tenant", "globex")
        .putHeader("X-Identity-Id", "user-1")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(403);
                      assertThat(ar.result().bodyAsJsonObject().getString("error"))
                          .isEqualTo("forbidden");
                      ctx.completeNow();
                    }));
  }

  @Test
  void requestWithoutIdentityReturns403(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Tenant", "acme")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(403);
                      ctx.completeNow();
                    }));
  }

  @Test
  void requestWithoutTenantReturns404(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Identity-Id", "user-1")
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

  @Test
  void requestWhenKetoDownReturns503(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlPathEqualTo("/relation-tuples/check")).willReturn(aResponse().withStatus(500)));

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Tenant", "acme")
        .putHeader("X-Identity-Id", "user-1")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(503);
                      assertThat(ar.result().bodyAsJsonObject().getString("error"))
                          .isEqualTo("authz_service_unavailable");
                      ctx.completeNow();
                    }));
  }

  @Test
  void healthEndpointBypassesAuthzFilter(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/health")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      ctx.completeNow();
                    }));
  }

  @Test
  void adminEndpointsBypassAuthzFilter(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/admin/tenants")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      ctx.completeNow();
                    }));
  }

  private void stubKetoCheck(String object, String relation, String subjectId, boolean allowed) {
    wireMock.stubFor(
        get(urlPathEqualTo("/relation-tuples/check"))
            .withQueryParam("namespace", WireMock.equalTo("Tenant"))
            .withQueryParam("object", WireMock.equalTo(object))
            .withQueryParam("relation", WireMock.equalTo(relation))
            .withQueryParam("subject_id", WireMock.equalTo(subjectId))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JsonObject().put("allowed", allowed).encode())));
  }
}
