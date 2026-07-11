package com.mysaas.oauth;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client d'introspection Hydra — fallback pour les tokens opaques.
 *
 * <p>Appelle l'endpoint admin {@code POST /admin/oauth2/introspect} d'Hydra avec le token et les
 * credentials du client OAuth2. Si le token est actif, renvoie un {@link TokenPrincipal} extrait de
 * la réponse. Utilisé en fallback de {@link HydraTokenValidator} quand le token n'est pas un JWT
 * valide.
 */
public final class HydraIntrospectionClient {

  private static final Logger LOG = LoggerFactory.getLogger(HydraIntrospectionClient.class);

  private final WebClient client;
  private final String hydraAdminUrl;
  private final String clientId;
  private final String clientSecret;

  public HydraIntrospectionClient(
      WebClient client, String hydraAdminUrl, String clientId, String clientSecret) {
    this.client = client;
    this.hydraAdminUrl = hydraAdminUrl.replaceAll("/+$", "");
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  /**
   * Introspecte un token opaque via l'API admin Hydra.
   *
   * @param token le token à introspecter
   * @return un Future contenant le {@link TokenPrincipal} si le token est actif, ou un Future
   *     échoué avec une {@link TokenValidationException} si inactif (401) ou Hydra injoignable
   *     (503)
   */
  public Future<TokenPrincipal> introspect(String token) {
    if (token == null || token.isBlank()) {
      return Future.failedFuture(new TokenValidationException(401, "Token absent"));
    }

    LOG.debug("Introspection token via Hydra admin");
    MultipartForm form = MultipartForm.create().attribute("token", token);

    return client
        .postAbs(hydraAdminUrl + "/admin/oauth2/introspect")
        .basicAuthentication(clientId, clientSecret)
        .sendMultipartForm(form)
        .compose(
            resp -> {
              int status = resp.statusCode();
              if (status != 200) {
                LOG.debug("Introspection: Hydra retourne HTTP {}", status);
                return Future.failedFuture(
                    new TokenValidationException(503, "Hydra injoignable (HTTP " + status + ")"));
              }
              JsonObject body = resp.bodyAsJsonObject();
              boolean active = body.getBoolean("active", false);
              if (!active) {
                LOG.debug("Introspection: token inactif");
                return Future.failedFuture(
                    new TokenValidationException(401, "Token inactif ou révoqué"));
              }
              TokenPrincipal principal = TokenPrincipal.fromIntrospection(body);
              LOG.debug(
                  "Introspection OK: subject={}, tenant={}",
                  principal.subject(),
                  principal.tenantId());
              return Future.succeededFuture(principal);
            });
  }
}
