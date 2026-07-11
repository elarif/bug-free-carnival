package com.mysaas.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
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
class HydraTokenValidatorTest {

  private WireMockServer wireMock;
  private KeyPair keyPair;
  private String kid;
  private JWTAuth signingAuth;
  private HydraTokenValidator validator;

  @BeforeEach
  void setUp(Vertx vertx) throws Exception {
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

    String jwksUrl = "http://localhost:" + wireMock.port() + "/.well-known/jwks.json";
    validator = new HydraTokenValidator(vertx, jwksUrl, "http://localhost:4444/");
  }

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  void validatesValidJwtAndReturnsPrincipal(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    String jwt =
        createJwt("user-1", "http://localhost:4444/", "openid profile tenant:acme", "acme");

    validator
        .validate(jwt)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      TokenPrincipal principal = ar.result();
                      assertThat(principal.subject()).isEqualTo("user-1");
                      assertThat(principal.issuer()).isEqualTo("http://localhost:4444/");
                      assertThat(principal.scopes())
                          .containsExactly("openid", "profile", "tenant:acme");
                      assertThat(principal.tenantId()).isEqualTo("acme");
                      ctx.completeNow();
                    }));
  }

  @Test
  void validatesJwtWithoutTenantId(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    String jwt = createJwt("user-2", "http://localhost:4444/", "openid", null);

    validator
        .validate(jwt)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.succeeded()).isTrue();
                      TokenPrincipal principal = ar.result();
                      assertThat(principal.tenantId()).isNull();
                      ctx.completeNow();
                    }));
  }

  @Test
  void rejectsTokenWithWrongIssuer(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    String jwt = createJwt("user-3", "http://wrong-issuer/", "openid", null);

    validator
        .validate(jwt)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
                      ctx.completeNow();
                    }));
  }

  @Test
  void rejectsMalformedToken(Vertx vertx, VertxTestContext ctx) {
    validator
        .validate("not-a-jwt")
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
                      ctx.completeNow();
                    }));
  }

  @Test
  void rejectsNullToken(Vertx vertx, VertxTestContext ctx) {
    validator
        .validate(null)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
                      ctx.completeNow();
                    }));
  }

  @Test
  void rejectsExpiredToken(Vertx vertx, VertxTestContext ctx) {
    stubJwks();
    long pastTime = (System.currentTimeMillis() / 1000) - 3600;
    String jwt = createJwtWithExpiry("user-4", "http://localhost:4444/", "openid", null, pastTime);

    validator
        .validate(jwt)
        .onComplete(
            ar ->
                ctx.verify(
                    () -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause()).isInstanceOf(TokenValidationException.class);
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
    if (expiryEpochSeconds != null) {
      claims.put("exp", expiryEpochSeconds);
    } else {
      claims.put("exp", now + 3600);
    }
    io.vertx.ext.auth.JWTOptions jwtOptions =
        new io.vertx.ext.auth.JWTOptions().setAlgorithm("RS256");
    return signingAuth.generateToken(claims, jwtOptions);
  }

  private static String pemPrivate(KeyPair kp) {
    java.util.Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
    String encoded = encoder.encodeToString(kp.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
  }
}
