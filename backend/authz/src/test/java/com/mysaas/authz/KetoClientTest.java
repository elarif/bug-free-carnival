package com.mysaas.authz;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
class KetoClientTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private KetoClient ketoClient;

  @BeforeEach
  void setUp(Vertx vertx) {
    wireMock = new WireMockServer(0);
    wireMock.start();
    webClient = WebClient.create(vertx);
    ketoClient = new KetoClient(webClient, "http://localhost:" + wireMock.port());
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
  void checkReturnsTrueWhenAllowed(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlPathEqualTo("/relation-tuples/check"))
            .withQueryParam("namespace", WireMock.equalTo("Tenant"))
            .withQueryParam("object", WireMock.equalTo("acme"))
            .withQueryParam("relation", WireMock.equalTo("access"))
            .withQueryParam("subject_id", WireMock.equalTo("user-1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JsonObject().put("allowed", true).encode())));

    ketoClient
        .check("Tenant", "acme", "access", "user-1")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result()).isTrue();
                      ctx.completeNow();
                    }));
  }

  @Test
  void checkReturnsFalseWhenDenied(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlPathEqualTo("/relation-tuples/check"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JsonObject().put("allowed", false).encode())));

    ketoClient
        .check("Tenant", "globex", "access", "user-2")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result()).isFalse();
                      ctx.completeNow();
                    }));
  }

  @Test
  void checkFailsWhenKetoDown(Vertx vertx, VertxTestContext ctx) {
    wireMock.stubFor(
        get(urlPathEqualTo("/relation-tuples/check")).willReturn(aResponse().withStatus(500)));

    ketoClient
        .check("Tenant", "acme", "access", "user-1")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(KetoException.class);
                      ctx.completeNow();
                    }));
  }

  @Test
  void checkFailsWhenParamsNull(Vertx vertx, VertxTestContext ctx) {
    ketoClient
        .check(null, "acme", "access", "user-1")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(KetoException.class);
                      ctx.completeNow();
                    }));
  }
}
