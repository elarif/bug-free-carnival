package com.mysaas.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
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

@ExtendWith(VertxExtension.class)
class HydraTokenFilterTest {

  private WireMockServer wireMock;
  private WebClient webClient;
  private HttpServer server;
  private JWTAuth signingAuth;
  private KeyPair keyPair;
  private String kid;
  private HydraTokenValidator validator;
  private HydraIntrospectionClient introspectionClient;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    wireMock = new WireMockServer(0);
    wireMock.start();

    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    keyPair = kpg.generateKeyPair();
    kid = "test-key-1";

    String pemPrivate = pemPrivate(keyPair);
    signingAuth =
        JWTAuth.create(
            vertx,
            new JWTAuthOptions()
                .addPubSecKey(
                    new PubSecKeyOptions().setAlgorithm("RS256").setId(kid).setBuffer(pemPrivate)));

    webClient = WebClient.create(vertx);
    String baseUrl = "http://localhost:" + wireMock.port();
    validator =
        new HydraTokenValidator(
            vertx, baseUrl + "/.well-known/jwks.json", "http://localhost:4444/");
    introspectionClient =
        new HydraIntrospectionClient(webClient, baseUrl, "test-client", "test-secret");

    Router router = Router.router(vertx);
    new HydraTokenFilter(validator, introspectionClient).mount(router);

    router
        .get("/api/me")
        .handler(
            c -> {
              TokenPrincipal principal = c.get(HydraTokenFilter.CTX_KEY);
              if (principal != null) {
                c.response()
                    .putHeader("Content-Type", "application/json")
                    .end(
                        new JsonObject()
                            .put("subject", principal.subject())
                            .put("tenant_id", principal.tenantId())
                            .put("scopes", new JsonArray(principal.scopes()))
                            .encode());
              } else {
                c.response().setStatusCode(500).end("{\"error\":\"no_principal\"}");
              }
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
  void requestWithValidJwtReturns200AndPrincipal(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    String jwt = createJwt("user-1", "http://localhost:4444/", "openid profile", "acme");

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
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
  void requestWithoutAuthorizationHeaderReturns401(Vertx vertx, VertxTestContext ctx) {
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
  void requestWithNonBearerAuthorizationReturns401(Vertx vertx, VertxTestContext ctx) {
    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("Authorization", "Basic dXNlcjpwYXNz")
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
  void requestWithInvalidJwtFallsBackToIntrospection(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    stubIntrospection("opaque-token-xyz", true, "user-2", "read write", "globex");

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("Authorization", "Bearer opaque-token-xyz")
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
  void requestWithExpiredJwtAndInactiveIntrospectionReturns401(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    stubIntrospection("expired-or-revoked", false, null, null, null);

    long pastTime = (System.currentTimeMillis() / 1000) - 3600;
    String expiredJwt =
        createJwtWithExpiry("user-3", "http://localhost:4444/", "openid", null, pastTime);

    WebClient.create(vertx, new WebClientOptions())
        .get(server.actualPort(), "localhost", "/api/me")
        .putHeader("Authorization", "Bearer " + expiredJwt)
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
  void healthEndpointBypassesTokenFilter(Vertx vertx, VertxTestContext ctx) {
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
  void adminEndpointsBypassTokenFilter(Vertx vertx, VertxTestContext ctx) {
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
    return createJwtWithExpiry(subject, issuer, scope, tenantId, null);
  }

  private String createJwtWithExpiry(
      String subject, String issuer, String scope, String tenantId, Long expiryEpochSeconds) {
    long now = System.currentTimeMillis() / 1000;
    JsonObject claims =
        new JsonObject().put("sub", subject).put("iss", issuer).put("iat", now).put("scope", scope);
    if (tenantId != null) {
      claims.put("tenant_id", tenantId);
    }
    claims.put("exp", expiryEpochSeconds != null ? expiryEpochSeconds : now + 3600);
    return signingAuth.generateToken(
        claims, new io.vertx.ext.auth.JWTOptions().setAlgorithm("RS256"));
  }

  private static String pemPrivate(KeyPair kp) {
    Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
    String encoded = encoder.encodeToString(kp.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
  }
}
