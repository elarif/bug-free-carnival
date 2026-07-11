package com.mysaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysaas.tenant.InMemoryTenantRegistry;
import com.mysaas.tenant.InMemoryTenantSchemaManager;
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
 * Tests du webhook after-registration : provisioning du tenant par défaut quand le trait tenant_id
 * est absent, ou association si présent.
 */
@ExtendWith(VertxExtension.class)
class RegistrationWebhookHandlerTest {

  private WebClient client;
  private HttpServer server;
  private InMemoryTenantRegistry registry;
  private InMemoryTenantSchemaManager schemaManager;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    registry = new InMemoryTenantRegistry();
    registry.delete("default");
    schemaManager = new InMemoryTenantSchemaManager();
    new RegistrationWebhookHandler(registry, schemaManager).mount(router);

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
  void webhookWithoutTenantIdProvisionsDefaultTenant(Vertx vertx, VertxTestContext ctx) {
    JsonObject payload =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000010")
                    .put("traits", new JsonObject().put("email", "carol@globex.com")));

    client
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
                      assertThat(body.getString("tenant")).isEqualTo("globex");
                      assertThat(registry.exists("globex")).isTrue();
                      assertThat(schemaManager.createdSchemas()).contains("tenant_globex");
                      ctx.completeNow();
                    }));
  }

  @Test
  void webhookWithExistingTenantIdDoesNothing(Vertx vertx, VertxTestContext ctx) {
    JsonObject payload =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000011")
                    .put(
                        "traits",
                        new JsonObject().put("email", "alice@acme.com").put("tenant_id", "acme")));

    client
        .post(server.actualPort(), "localhost", "/webhooks/kratos/after-registration")
        .sendJsonObject(payload)
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
  void webhookWithNewTenantIdCreatesTenant(Vertx vertx, VertxTestContext ctx) {
    JsonObject payload =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000012")
                    .put(
                        "traits",
                        new JsonObject()
                            .put("email", "dave@initech.com")
                            .put("tenant_id", "initech")));

    client
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
                      assertThat(body.getString("tenant")).isEqualTo("initech");
                      assertThat(schemaManager.createdSchemas()).contains("tenant_initech");
                      ctx.completeNow();
                    }));
  }

  @Test
  void webhookWithMalformedPayloadReturns400(Vertx vertx, VertxTestContext ctx) {
    client
        .post(server.actualPort(), "localhost", "/webhooks/kratos/after-registration")
        .sendJsonObject(new JsonObject().put("foo", "bar"))
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
  void webhookWithoutEmailProvisionsDefaultTenant(Vertx vertx, VertxTestContext ctx) {
    JsonObject payload =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000013")
                    .put("traits", new JsonObject()));

    client
        .post(server.actualPort(), "localhost", "/webhooks/kratos/after-registration")
        .sendJsonObject(payload)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("tenant")).isEqualTo("default");
                      assertThat(registry.exists("default")).isTrue();
                      ctx.completeNow();
                    }));
  }
}
