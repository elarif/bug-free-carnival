package com.mysaas.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
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

/**
 * Tests du filtre {@link KratosSessionFilter} : validation de session Kratos via WireMock et
 * injection d'{@link Identity} dans le RoutingContext.
 */
@ExtendWith(VertxExtension.class)
class KratosSessionFilterTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HttpServer server;
  private KratosClient kratosClient;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    wireMock = new WireMockServer(0);
    wireMock.start();

    webClient = WebClient.create(vertx);
    kratosClient = new KratosClient(webClient, "http://localhost:" + wireMock.port());

    Router router = Router.router(vertx);
    new KratosSessionFilter(kratosClient).mount(router);

    // Route protégée qui retourne l'identity injectée
    router
        .get("/api/me")
        .handler(
            c -> {
              Identity identity = c.get(KratosSessionFilter.CTX_KEY);
              if (identity != null) {
                c.response()
                    .putHeader("Content-Type", "application/json")
                    .end(
                        new JsonObject()
                            .put("id", identity.id())
                            .put("tenant_id", identity.tenantId())
                            .put("email", identity.email())
                            .encode());
              } else {
                c.response().setStatusCode(500).end("{\"error\":\"no_identity\"}");
              }
            });

    // Endpoints exemptés
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
  void requestWithValidSessionReturns200AndIdentity(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlEqualTo("/sessions/whoami"))
            .withHeader("Cookie", equalTo("ory_kratos_session=valid"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        new JsonObject()
                            .put(
                                "identity",
                                new JsonObject()
                                    .put("id", "00000000-0000-0000-0000-000000000001")
                                    .put(
                                        "traits",
                                        new JsonObject()
                                            .put("email", "alice@acme.com")
                                            .put("tenant_id", "acme")))
                            .encode())));

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("Cookie", "ory_kratos_session=valid")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("id"))
                          .isEqualTo("00000000-0000-0000-0000-000000000001");
                      assertThat(body.getString("tenant_id")).isEqualTo("acme");
                      assertThat(body.getString("email")).isEqualTo("alice@acme.com");
                      ctx.completeNow();
                    }));
  }

  @Test
  void requestWithoutCookieReturns401(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(401);
                      assertThat(ar.result().bodyAsJsonObject().getString("error"))
                          .isEqualTo("unauthorized");
                      ctx.completeNow();
                    }));
  }

  @Test
  void requestWithInvalidSessionReturns401(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlEqualTo("/sessions/whoami"))
            .withHeader("Cookie", equalTo("ory_kratos_session=invalid"))
            .willReturn(aResponse().withStatus(401)));

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("Cookie", "ory_kratos_session=invalid")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(401);
                      ctx.completeNow();
                    }));
  }

  @Test
  void requestWhenKratosDownReturns503(Vertx vertx, VertxTestContext ctx) {
    // WireMock enregistré sur un port qui retourne 500
    wireMock.stubFor(
        get(urlEqualTo("/sessions/whoami"))
            .withHeader("Cookie", equalTo("ory_kratos_session=ok"))
            .willReturn(aResponse().withStatus(500)));

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("Cookie", "ory_kratos_session=ok")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(503);
                      assertThat(ar.result().bodyAsJsonObject().getString("error"))
                          .isEqualTo("identity_service_unavailable");
                      ctx.completeNow();
                    }));
  }

  @Test
  void healthEndpointBypassesSessionFilter(Vertx vertx, VertxTestContext ctx) {
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
  void adminEndpointsBypassSessionFilter(Vertx vertx, VertxTestContext ctx) {
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
}
