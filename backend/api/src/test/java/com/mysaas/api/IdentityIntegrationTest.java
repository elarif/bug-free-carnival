package com.mysaas.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mysaas.identity.Identity;
import com.mysaas.identity.KratosClient;
import com.mysaas.identity.KratosSessionFilter;
import com.mysaas.identity.RegistrationWebhookHandler;
import com.mysaas.tenant.InMemoryTenantRegistry;
import com.mysaas.tenant.InMemoryTenantSchemaManager;
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
 * Tests de contrat API Phase 5 : intégration complète TenantFilter + KratosSessionFilter + endpoint
 * /api/me + webhook after-registration. Kratos est mocké via WireMock.
 */
@ExtendWith(VertxExtension.class)
class IdentityIntegrationTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HttpServer server;
  private InMemoryTenantRegistry registry;
  private InMemoryTenantSchemaManager schemaManager;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    wireMock = new WireMockServer(0);
    wireMock.start();

    registry = new InMemoryTenantRegistry();
    schemaManager = new InMemoryTenantSchemaManager();
    webClient = WebClient.create(vertx);
    KratosClient kratosClient = new KratosClient(webClient, "http://localhost:" + wireMock.port());

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Couche tenant (order -100)
    new TenantFilter(new TenantResolver(registry)).mount(router);
    // Couche identity (order -90)
    new KratosSessionFilter(kratosClient).mount(router);
    // Webhook after-registration (exempté du filtre tenant via /admin/ mais monté explicitement)
    new RegistrationWebhookHandler(registry, schemaManager).mount(router);

    // Endpoint protégé exemple : /api/me retourne l'identity + le tenant
    router
        .get("/api/me")
        .handler(
            c -> {
              Identity identity = c.get(KratosSessionFilter.CTX_KEY);
              TenantContext tenant = c.get(TenantResolver.CTX_KEY);
              c.response()
                  .putHeader("Content-Type", "application/json")
                  .end(
                      new JsonObject()
                          .put("identity_id", identity.id())
                          .put("email", identity.email())
                          .put("tenant_id", identity.tenantId())
                          .put("resolved_tenant", tenant != null ? tenant.slug() : null)
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
  void protectedEndpointWithValidSessionAndTenantReturns200(Vertx vertx, VertxTestContext ctx) {
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
        .putHeader("X-Tenant", "acme")
        .putHeader("Cookie", "ory_kratos_session=valid")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("identity_id"))
                          .isEqualTo("00000000-0000-0000-0000-000000000001");
                      assertThat(body.getString("email")).isEqualTo("alice@acme.com");
                      assertThat(body.getString("tenant_id")).isEqualTo("acme");
                      assertThat(body.getString("resolved_tenant")).isEqualTo("acme");
                      ctx.completeNow();
                    }));
  }

  @Test
  void protectedEndpointWithoutSessionReturns401(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("X-Tenant", "acme")
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
  void protectedEndpointWithoutTenantReturns404(Vertx vertx, VertxTestContext ctx) {
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
                                    .put("id", "00000000-0000-0000-0000-000000000002")
                                    .put("traits", new JsonObject().put("email", "x@y.com")))
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
                      assertThat(ar.result().statusCode()).isEqualTo(404);
                      ctx.completeNow();
                    }));
  }

  @Test
  void webhookProvisionsTenantAndSchema(Vertx vertx, VertxTestContext ctx) {
    JsonObject payload =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000020")
                    .put("traits", new JsonObject().put("email", "newuser@newco.com")));

    WebClient.create(vertx, new WebClientOptions())
        .post(server.actualPort(), "localhost", "/webhooks/kratos/after-registration")
        .sendJsonObject(payload)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("action")).isEqualTo("tenant_provisioned");
                      assertThat(body.getString("tenant")).isEqualTo("newco");
                      assertThat(schemaManager.createdSchemas()).contains("tenant_newco");
                      ctx.completeNow();
                    }));
  }
}
