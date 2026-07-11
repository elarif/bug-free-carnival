package com.mysaas.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
class KratosClientTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private KratosClient kratosClient;

  @BeforeEach
  void setUp(Vertx vertx) {
    wireMock = new WireMockServer(0);
    wireMock.start();
    webClient = WebClient.create(vertx);
    kratosClient = new KratosClient(webClient, "http://localhost:" + wireMock.port());
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
  void whoamiReturnsIdentityWhenSessionValid(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlEqualTo("/sessions/whoami"))
            .withHeader("Cookie", equalTo("ory_kratos_session=valid-session-token"))
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

    kratosClient
        .whoami("ory_kratos_session=valid-session-token")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      Identity identity = ar.result();
                      assertThat(identity.id()).isEqualTo("00000000-0000-0000-0000-000000000001");
                      assertThat(identity.tenantId()).isEqualTo("acme");
                      assertThat(identity.email()).isEqualTo("alice@acme.com");
                      ctx.completeNow();
                    }));
  }

  @Test
  void whoamiFailsWhenSessionInvalid(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlEqualTo("/sessions/whoami"))
            .withHeader("Cookie", equalTo("ory_kratos_session=invalid"))
            .willReturn(aResponse().withStatus(401)));

    kratosClient
        .whoami("ory_kratos_session=invalid")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(KratosSessionException.class);
                      assertThat(((KratosSessionException) ar.cause()).statusCode()).isEqualTo(401);
                      ctx.completeNow();
                    }));
  }

  @Test
  void whoamiFailsWhenCookieNull(Vertx vertx, VertxTestContext ctx) {
    kratosClient
        .whoami(null)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(KratosSessionException.class);
                      assertThat(((KratosSessionException) ar.cause()).statusCode()).isEqualTo(401);
                      ctx.completeNow();
                    }));
  }

  @Test
  void whoamiFailsOnServerError(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlEqualTo("/sessions/whoami"))
            .withHeader("Cookie", equalTo("ory_kratos_session=ok"))
            .willReturn(aResponse().withStatus(500)));

    kratosClient
        .whoami("ory_kratos_session=ok")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(KratosSessionException.class);
                      assertThat(((KratosSessionException) ar.cause()).statusCode()).isEqualTo(500);
                      ctx.completeNow();
                    }));
  }
}
