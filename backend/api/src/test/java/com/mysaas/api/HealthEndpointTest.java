package com.mysaas.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysaas.api.health.HealthHandler;
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
class HealthEndpointTest {

  private WebClient client;
  private HttpServer server;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    Router router = Router.router(vertx);

    // ReadyChecker qui échoue toujours (DB non configurée en test)
    HealthHandler.ReadyChecker readyChecker = () -> io.vertx.core.Future.failedFuture("no db");
    new HealthHandler(readyChecker).mount(router);

    client = WebClient.create(vertx, new WebClientOptions());
    server = vertx.createHttpServer();
    server
        .requestHandler(router)
        .listen(0) // port aléatoire
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
  void tearDown(Vertx vertx) {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void healthReturns200(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/health")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    var resp = ar.result();
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsJsonObject().getString("status")).isEqualTo("UP");
                  });
              ctx.completeNow();
            });
  }

  @Test
  void readyReturns503WhenDbDown(Vertx vertx, VertxTestContext ctx) {
    client
        .get(server.actualPort(), "localhost", "/ready")
        .send()
        .onComplete(
            ar -> {
              ctx.verify(
                  () -> {
                    assertThat(ar.succeeded()).isTrue();
                    var resp = ar.result();
                    assertThat(resp.statusCode()).isEqualTo(503);
                    var body = resp.bodyAsJsonObject();
                    assertThat(body.getString("status")).isEqualTo("DOWN");
                    assertThat(body.getString("postgres")).isEqualTo("DOWN");
                  });
              ctx.completeNow();
            });
  }
}
