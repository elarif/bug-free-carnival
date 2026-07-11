package com.mysaas.oauth;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validateur de tokens JWT Hydra — validation locale via JWKS.
 *
 * <p>Charge les clés publiques depuis l'endpoint JWKS d'Hydra ({@code /.well-known/jwks.json}) au
 * démarrage, puis valide les JWT Bearer localement (sans appel réseau à chaque requête). Vérifie la
 * signature, l'issuer et l'expiration. Si le JWKS est injoignable ou le token invalide, une {@link
 * TokenValidationException} est levée.
 */
public final class HydraTokenValidator {

  private static final Logger LOG = LoggerFactory.getLogger(HydraTokenValidator.class);

  private final Vertx vertx;
  private final String jwksUrl;
  private final String expectedIssuer;
  private final WebClient webClient;

  public HydraTokenValidator(Vertx vertx, String jwksUrl, String expectedIssuer) {
    this.vertx = vertx;
    this.jwksUrl = jwksUrl;
    this.expectedIssuer = expectedIssuer;
    this.webClient = WebClient.create(vertx);
  }

  /**
   * Valide un JWT localement via les JWKS Hydra.
   *
   * @param jwt le token JWT (sans le préfixe "Bearer ")
   * @return un Future contenant le {@link TokenPrincipal} si valide, ou un Future échoué avec une
   *     {@link TokenValidationException} si invalide
   */
  public Future<TokenPrincipal> validate(String jwt) {
    if (jwt == null || jwt.isBlank()) {
      return Future.failedFuture(new TokenValidationException(401, "Token JWT absent"));
    }

    LOG.debug("Validation JWT: chargement JWKS depuis {}", jwksUrl);
    return webClient
        .getAbs(jwksUrl)
        .send()
        .compose(
            resp -> {
              if (resp.statusCode() != 200) {
                return Future.failedFuture(
                    new TokenValidationException(
                        503, "JWKS Hydra injoignable (HTTP " + resp.statusCode() + ")"));
              }
              JsonObject jwks = resp.bodyAsJsonObject();
              JsonArray keys = jwks.getJsonArray("keys", new JsonArray());
              if (keys.isEmpty()) {
                return Future.failedFuture(
                    new TokenValidationException(503, "JWKS Hydra vide (aucune clé)"));
              }

              JWTAuthOptions options = new JWTAuthOptions();
              for (int i = 0; i < keys.size(); i++) {
                options.addJwk(keys.getJsonObject(i));
              }
              options.setJWTOptions(new io.vertx.ext.auth.JWTOptions().setIssuer(expectedIssuer));

              JWTAuth auth = JWTAuth.create(vertx, options);
              return auth.authenticate(new TokenCredentials(jwt))
                  .map(
                      user -> {
                        JsonObject claims = user.principal();
                        TokenPrincipal principal = TokenPrincipal.fromJwtClaims(claims);
                        LOG.debug(
                            "JWT valide: subject={}, tenant={}",
                            principal.subject(),
                            principal.tenantId());
                        return principal;
                      })
                  .recover(
                      err ->
                          Future.failedFuture(
                              new TokenValidationException(
                                  401, "JWT invalide: " + err.getMessage(), err)));
            })
        .onFailure(
            err -> {
              if (!(err instanceof TokenValidationException)) {
                LOG.error("Erreur inattendue lors de la validation JWT", err);
              }
            });
  }
}
