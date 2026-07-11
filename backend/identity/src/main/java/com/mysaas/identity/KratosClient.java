package com.mysaas.identity;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client HTTP vers l'API publique d'Ory Kratos.
 *
 * <p>Wrapper Vert.x WebClient autour de l'endpoint {@code GET /sessions/whoami} qui valide un
 * cookie de session Kratos et renvoie l'{@link Identity} courante.
 */
public final class KratosClient {

  private static final Logger LOG = LoggerFactory.getLogger(KratosClient.class);

  private final WebClient client;
  private final String kratosPublicUrl;

  public KratosClient(WebClient client, String kratosPublicUrl) {
    this.client = client;
    this.kratosPublicUrl = kratosPublicUrl.replaceAll("/+$", "");
  }

  /**
   * Appelle {@code /sessions/whoami} avec le cookie de session Kratos transmis par le client HTTP.
   *
   * @param cookie la valeur du header {@code Cookie} (ex: {@code "ory_kratos_session=<token>"}) ;
   *     null ou vide si absent
   * @return un Future contenant l'Identity si la session est valide (200), ou un Future échoué avec
   *     une {@link KratosSessionException} si la session est invalide (401/403), ou une erreur
   *     serveur (5xx)
   */
  public Future<Identity> whoami(String cookie) {
    if (cookie == null || cookie.isBlank()) {
      return Future.failedFuture(new KratosSessionException(401, "Cookie de session absent"));
    }

    LOG.debug("Whoami Kratos: appel /sessions/whoami");
    return client
        .getAbs(kratosPublicUrl + "/sessions/whoami")
        .putHeader("Cookie", cookie)
        .putHeader("Accept", "application/json")
        .send()
        .compose(
            resp -> {
              int status = resp.statusCode();
              if (status == 200) {
                JsonObject body = resp.bodyAsJsonObject();
                Identity identity = Identity.fromWhoami(body);
                if (identity == null) {
                  return Future.failedFuture(
                      new KratosSessionException(
                          500, "Réponse whoami malformée: identity absente"));
                }
                return Future.succeededFuture(identity);
              }
              LOG.debug("Whoami Kratos: statut {}", status);
              return Future.failedFuture(
                  new KratosSessionException(status, "Session invalide (HTTP " + status + ")"));
            });
  }
}
