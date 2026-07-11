package com.mysaas.identity;

import io.vertx.core.json.JsonObject;

/**
 * Identité Ory Kratos extraite d'une session.
 *
 * <p>Représente l'identité courante de l'utilisateur (id, email, tenant_id) telle qu'exposée par
 * l'endpoint {@code /sessions/whoami} de Kratos. Le {@code tenant_id} est un trait optionnel du
 * schéma d'identité Kratos (voir {@code infra/helm/mysaas/values.yaml}).
 *
 * @param id l'identifiant unique Kratos de l'identité (UUID)
 * @param tenantId le slug du tenant auquel l'utilisateur est rattaché (peut être null)
 * @param email l'email de l'utilisateur (extrait des traits)
 * @param traits les traits bruts envoyés par Kratos
 */
public record Identity(String id, String tenantId, String email, JsonObject traits) {

  /**
   * Parse une réponse {@code /sessions/whoami} de Kratos et extrait l'identité.
   *
   * @param whoami le corps JSON de la réponse whoami
   * @return l'Identity si la réponse contient un bloc {@code identity}, null sinon
   */
  public static Identity fromWhoami(JsonObject whoami) {
    if (whoami == null) {
      return null;
    }
    JsonObject identity = whoami.getJsonObject("identity");
    if (identity == null) {
      return null;
    }
    String id = identity.getString("id");
    JsonObject traitObj = identity.getJsonObject("traits", new JsonObject());
    String tenantId = traitObj.getString("tenant_id");
    String email = traitObj.getString("email");
    return new Identity(id, tenantId, email, traitObj);
  }
}
