package com.mysaas.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mysaas.authz.KetoAuthzFilter;
import com.mysaas.authz.KetoClient;
import com.mysaas.identity.Identity;
import com.mysaas.tenant.InMemoryTenantRegistry;
import com.mysaas.tenant.TenantContext;
import com.mysaas.tenant.TenantFilter;
import com.mysaas.tenant.TenantResolver;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
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
 * Tests de contrat API Phase 7 : intégration TenantFilter + Identity simulée + KetoAuthzFilter.
 * Valide user sans permission sur tenant B → 403, même user sur tenant A → 200, Keto down → 503.
 */
@ExtendWith(VertxExtension.class)
class AuthzIntegrationTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HttpServer server;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    wireMock = new WireMockServer(0);
    wireMock.start();

    webClient = WebClient.create(vertx);
    KetoClient ketoClient = new KetoClient(webClient, "http://localhost:" + wireMock.port());

    InMemoryTenantRegistry registry = new InMemoryTenantRegistry();

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Couche tenant (order -100)
    new TenantFilter(new TenantResolver(registry)).mount(router);

    // Handler simulé (order -95) qui injecte une Identity depuis X-Identity-Id
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

    // Couche authz (order -70)
    new KetoAuthzFilter(ketoClient).mount(router);

    // Route protégée
    router
        .get("/api/data")
        .handler(
            c -> {
              TenantContext tenant = c.get(TenantResolver.CTX_KEY);
              Identity identity = c.get(com.mysaas.identity.KratosSessionFilter.CTX_KEY);
              c.response()
                  .putHeader("Content-Type", "application/json")
                  .end(
                      new JsonObject()
                          .put("tenant", tenant.slug())
                          .put("user", identity.id())
                          .encode());
            });

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
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  void userWithPermissionOnTenantAReturns200(Vertx vertx, VertxTestContext ctx) {
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
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("tenant")).isEqualTo("acme");
                      assertThat(body.getString("user")).isEqualTo("user-1");
                      ctx.completeNow();
                    }));
  }

  @Test
  void userWithoutPermissionOnTenantBReturns403(Vertx vertx, VertxTestContext ctx) {
    stubKetoCheck("globex", "access", "user-1", false);

    // Enregistrer globex dans le registry pour que le TenantFilter passe
    // (le InMemoryTenantRegistry par défaut a acme/default/demo)
    // On ne peut pas modifier le registry ici, donc on utilise acme mais avec user sans permission
    stubKetoCheck("acme", "access", "user-2", false);

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Tenant", "acme")
        .putHeader("X-Identity-Id", "user-2")
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
  void sameUserAllowedOnTenantADeniedOnTenantB(Vertx vertx, VertxTestContext ctx) {
    // user-1 a accès à acme mais pas à demo (demo est dans le InMemoryTenantRegistry par défaut)
    stubKetoCheck("acme", "access", "user-1", true);
    stubKetoCheck("demo", "access", "user-1", false);

    // D'abord sur acme → 200
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/data")
        .putHeader("X-Tenant", "acme")
        .putHeader("X-Identity-Id", "user-1")
        .send()
        .onComplete(
            ar1 -> {
              ctx.verify(
                  () -> {
                    assertThat(ar1.succeeded()).isTrue();
                    assertThat(ar1.result().statusCode()).isEqualTo(200);
                  });
              // Puis sur demo → 403
              WebClient.create(vertx, new WebClientOptions())
                  .get(server.actualPort(), "localhost", "/api/data")
                  .putHeader("X-Tenant", "demo")
                  .putHeader("X-Identity-Id", "user-1")
                  .send()
                  .onComplete(
                      ar2 ->
                          ctx.verify(
                              () -> {
                                assertThat(ar2.succeeded()).isTrue();
                                assertThat(ar2.result().statusCode()).isEqualTo(403);
                                ctx.completeNow();
                              }));
            });
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
