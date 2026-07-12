package com.mysaas.authz;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client HTTP vers l'API read d'Ory Keto.
 *
 * <p>Wrapper Vert.x WebClient autour de l'endpoint {@code GET /relation-tuples/check} qui vérifie
 * si un subject a une relation donnée sur un objet dans un namespace. Les tuples sont de la forme
 * {@code namespace:object#relation@subject}.
 */
public final class KetoClient {

  private static final Logger LOG = LoggerFactory.getLogger(KetoClient.class);

  private final WebClient client;
  private final String ketoReadUrl;

  public KetoClient(WebClient client, String ketoReadUrl) {
    this.client = client;
    this.ketoReadUrl = ketoReadUrl.replaceAll("/+$", "");
  }

  /**
   * Vérifie si un subject a une relation sur un objet dans un namespace.
   *
   * @param namespace le namespace Keto (ex: "Tenant")
   * @param object l'objet (ex: slug du tenant "acme")
   * @param relation la relation (ex: "access", "owners")
   * @param subjectId le subject (ex: l'ID Kratos de l'utilisateur)
   * @return un Future contenant true si la relation existe (allowed), false sinon ; ou un Future
   *     échoué avec une {@link KetoException} si Keto est injoignable
   */
  public Future<Boolean> check(String namespace, String object, String relation, String subjectId) {
    if (namespace == null || object == null || relation == null || subjectId == null) {
      return Future.failedFuture(new KetoException(400, "Paramètres de check Keto absents"));
    }

    LOG.debug("Check Keto: {}:{}#{}@{}", namespace, object, relation, subjectId);
    return client
        .getAbs(ketoReadUrl + "/relation-tuples/check")
        .addQueryParam("namespace", namespace)
        .addQueryParam("object", object)
        .addQueryParam("relation", relation)
        .addQueryParam("subject_id", subjectId)
        .send()
        .compose(
            resp -> {
              int status = resp.statusCode();
              if (status != 200) {
                LOG.debug("Keto check: statut {}", status);
                return Future.failedFuture(
                    new KetoException(status, "Keto injoignable (HTTP " + status + ")"));
              }
              JsonObject body = resp.bodyAsJsonObject();
              boolean allowed = body.getBoolean("allowed", false);
              LOG.debug(
                  "Keto check: {}:{}#{}@{} → {}", namespace, object, relation, subjectId, allowed);
              return Future.succeededFuture(allowed);
            });
  }
}
