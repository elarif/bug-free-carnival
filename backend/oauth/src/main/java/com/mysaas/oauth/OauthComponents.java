package com.mysaas.oauth;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Factory des composants OAuth pour la prod — assemble le {@link HydraTokenValidator} (validation
 * JWT locale via JWKS) et le {@link HydraIntrospectionClient} (fallback opaque tokens).
 */
public final class OauthComponents {

  private final WebClient webClient;
  private final HydraTokenValidator tokenValidator;
  private final HydraIntrospectionClient introspectionClient;

  public OauthComponents(
      WebClient webClient,
      HydraTokenValidator tokenValidator,
      HydraIntrospectionClient introspectionClient) {
    this.webClient = webClient;
    this.tokenValidator = tokenValidator;
    this.introspectionClient = introspectionClient;
  }

  /**
   * Construit les composants OAuth depuis la config Hydra.
   *
   * @param vertx l'instance Vert.x
   * @param jwksUrl l'URL du JWKS Hydra ({@code /.well-known/jwks.json})
   * @param issuer l'issuer attendu (URL publique Hydra)
   * @param hydraAdminUrl l'URL admin Hydra pour l'introspection
   * @param clientId l'ID du client OAuth2 pour l'introspection
   * @param clientSecret le secret du client OAuth2 pour l'introspection
   */
  public static OauthComponents create(
      Vertx vertx,
      String jwksUrl,
      String issuer,
      String hydraAdminUrl,
      String clientId,
      String clientSecret) {
    WebClient webClient = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true));
    HydraTokenValidator validator = new HydraTokenValidator(vertx, jwksUrl, issuer);
    HydraIntrospectionClient introspection =
        new HydraIntrospectionClient(webClient, hydraAdminUrl, clientId, clientSecret);
    return new OauthComponents(webClient, validator, introspection);
  }

  public HydraTokenValidator tokenValidator() {
    return tokenValidator;
  }

  public HydraIntrospectionClient introspectionClient() {
    return introspectionClient;
  }

  /** Crée le filtre de token Hydra. */
  public HydraTokenFilter tokenFilter() {
    return new HydraTokenFilter(tokenValidator, introspectionClient);
  }

  public void close() {
    if (webClient != null) {
      webClient.close();
    }
  }
}
