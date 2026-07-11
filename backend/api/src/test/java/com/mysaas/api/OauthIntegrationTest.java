package com.mysaas.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mysaas.oauth.HydraIntrospectionClient;
import com.mysaas.oauth.HydraTokenFilter;
import com.mysaas.oauth.HydraTokenValidator;
import com.mysaas.oauth.TokenPrincipal;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests de contrat API Phase 6 : intégration HydraTokenFilter + endpoint /me avec JWKS mocké via
 * WireMock. Valide token valide (JWT), token opaque (introspection fallback), token manquant.
 */
@ExtendWith(VertxExtension.class)
class OauthIntegrationTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HttpServer server;
  private JWTAuth signingAuth;
  private KeyPair keyPair;
  private String kid;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    wireMock = new WireMockServer(0);
    wireMock.start();

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    keyPair = kpg.generateKeyPair();
    kid = "test-key-1";

    signingAuth =
        JWTAuth.create(
            vertx,
            new JWTAuthOptions()
                .addPubSecKey(
                    new PubSecKeyOptions()
                        .setAlgorithm("RS256")
                        .setId(kid)
                        .setBuffer(pemPrivate(keyPair))));

    webClient = WebClient.create(vertx);
    String baseUrl = "http://localhost:" + wireMock.port();
    HydraTokenValidator validator =
        new HydraTokenValidator(
            vertx, baseUrl + "/.well-known/jwks.json", "http://localhost:4444/");
    HydraIntrospectionClient introspection =
        new HydraIntrospectionClient(webClient, baseUrl, "test-client", "test-secret");

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    new HydraTokenFilter(validator, introspection).mount(router);

    router
        .get("/me")
        .handler(
            c -> {
              TokenPrincipal principal = c.get(HydraTokenFilter.CTX_KEY);
              if (principal == null) {
                c.response().setStatusCode(401).end("{\"error\":\"unauthorized\"}");
                return;
              }
              c.response()
                  .putHeader("Content-Type", "application/json")
                  .end(
                      new JsonObject()
                          .put("subject", principal.subject())
                          .put("tenant_id", principal.tenantId())
                          .put("scopes", new JsonArray(principal.scopes()))
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
  void meEndpointWithValidJwtReturns200(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    String jwt = createJwt("user-1", "http://localhost:4444/", "openid profile", "acme");

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/me")
        .putHeader("Authorization", "Bearer " + jwt)
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("subject")).isEqualTo("user-1");
                      assertThat(body.getString("tenant_id")).isEqualTo("acme");
                      ctx.completeNow();
                    }));
  }

  @Test
  void meEndpointWithoutTokenReturns401(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/me")
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
  void meEndpointWithOpaqueTokenFallsBackToIntrospection(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    stubIntrospection("opaque-abc", true, "user-2", "read", "globex");

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/me")
        .putHeader("Authorization", "Bearer opaque-abc")
        .send()
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      assertThat(ar.result().statusCode()).isEqualTo(200);
                      JsonObject body = ar.result().bodyAsJsonObject();
                      assertThat(body.getString("subject")).isEqualTo("user-2");
                      assertThat(body.getString("tenant_id")).isEqualTo("globex");
                      ctx.completeNow();
                    }));
  }

  @Test
  void meEndpointWithInvalidTokenReturns401(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    stubIntrospection("garbage", false, null, null, null);

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/me")
        .putHeader("Authorization", "Bearer garbage")
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

  private void stubJwks() {
    RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
    String n =
        Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
    String e =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(pub.getPublicExponent().toByteArray());

    JsonObject jwks =
        new JsonObject()
            .put(
                "keys",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("kty", "RSA")
                            .put("use", "sig")
                            .put("alg", "RS256")
                            .put("kid", kid)
                            .put("n", n)
                            .put("e", e)));

    wireMock.stubFor(
        get(urlEqualTo("/.well-known/jwks.json"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(jwks.encode())));
  }

  private void stubIntrospection(
      String token, boolean active, String subject, String scope, String tenantId) {
    JsonObject body = new JsonObject().put("active", active);
    if (subject != null) {
      body.put("sub", subject);
    }
    if (scope != null) {
      body.put("scope", scope);
    }
    if (tenantId != null) {
      body.put("tenant_id", tenantId);
    }

    wireMock.stubFor(
        post(urlEqualTo("/admin/oauth2/introspect"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body.encode())));
  }

  private String createJwt(String subject, String issuer, String scope, String tenantId) {
    long now = System.currentTimeMillis() / 1000;
    JsonObject claims =
        new JsonObject().put("sub", subject).put("iss", issuer).put("iat", now).put("scope", scope);
    if (tenantId != null) {
      claims.put("tenant_id", tenantId);
    }
    claims.put("exp", now + 3600);
    return signingAuth.generateToken(
        claims, new io.vertx.ext.auth.JWTOptions().setAlgorithm("RS256"));
  }

  private static String pemPrivate(KeyPair kp) {
    Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
    String encoded = encoder.encodeToString(kp.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
  }
}
