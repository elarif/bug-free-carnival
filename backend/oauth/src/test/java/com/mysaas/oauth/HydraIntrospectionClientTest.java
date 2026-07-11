package com.mysaas.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class HydraIntrospectionClientTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HydraIntrospectionClient introspectionClient;

  @BeforeEach
  void setUp(Vertx vertx) {
    wireMock = new WireMockServer(0);
    wireMock.start();
    webClient = WebClient.create(vertx);
    introspectionClient =
        new HydraIntrospectionClient(
            webClient, "http://localhost:" + wireMock.port(), "test-client", "test-secret");
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
  void introspectReturnsPrincipalWhenTokenActive(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        post(urlEqualTo("/admin/oauth2/introspect"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        new JsonObject()
                            .put("active", true)
                            .put("sub", "user-1")
                            .put("scope", "read write")
                            .put("tenant_id", "acme")
                            .encode())));

    introspectionClient
        .introspect("opaque-token-123")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      TokenPrincipal principal = ar.result();
                      assertThat(principal.subject()).isEqualTo("user-1");
                      assertThat(principal.scopes()).containsExactly("read", "write");
                      assertThat(principal.tenantId()).isEqualTo("acme");
                      ctx.completeNow();
                    }));
  }

  @Test
  void introspectFailsWhenTokenInactive(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        post(urlEqualTo("/admin/oauth2/introspect"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JsonObject().put("active", false).encode())));

    introspectionClient
        .introspect("revoked-token")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
                      assertThat(((TokenValidationException) ar.cause()).statusCode())
                          .isEqualTo(401);
                      ctx.completeNow();
                    }));
  }

  @Test
  void introspectFailsWhenHydraDown(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        post(urlEqualTo("/admin/oauth2/introspect")).willReturn(aResponse().withStatus(500)));

    introspectionClient
        .introspect("some-token")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
                      assertThat(((TokenValidationException) ar.cause()).statusCode())
                          .isEqualTo(503);
                      ctx.completeNow();
                    }));
  }

  @Test
  void introspectFailsWhenTokenNull(Vertx vertx, VertxTestContext ctx) {
    introspectionClient
        .introspect(null)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
                      assertThat(((TokenValidationException) ar.cause()).statusCode())
                          .isEqualTo(401);
                      ctx.completeNow();
                    }));
  }
}
