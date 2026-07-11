package com.mysaas.oauth;

import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.List;

/**
 * Principal extrait d'un token OAuth2/OIDC Hydra.
 *
 * <p>Représente l'utilisateur/client authentifié via un Bearer token JWT (validation locale via
 * JWKS) ou opaque (introspection Hydra). Contient le subject, les scopes, le claim {@code
 * tenant_id} optionnel et l'issuer.
 *
 * @param subject le subject du token (sub)
 * @param scopes les scopes accordés (espace-délimités dans le token)
 * @param tenantId le claim tenant_id (peut être null)
 * @param issuer l'issuer du token (iss)
 */
public record TokenPrincipal(String subject, List<String> scopes, String tenantId, String issuer) {

  /** Parse les claims d'un JWT Hydra pour construire le principal. */
  public static TokenPrincipal fromJwtClaims(JsonObject claims) {
    String subject = claims.getString("sub");
    String issuer = claims.getString("iss");
    List<String> scopes = parseScopes(claims.getString("scope"));
    String tenantId = claims.getString("tenant_id");
    return new TokenPrincipal(subject, scopes, tenantId, issuer);
  }

  /** Parse la réponse d'introspection Hydra pour construire le principal. */
  public static TokenPrincipal fromIntrospection(JsonObject introspection) {
    String subject = introspection.getString("sub");
    String issuer = introspection.getString("iss");
    List<String> scopes = parseScopes(introspection.getString("scope"));
    String tenantId = introspection.getString("tenant_id");
    return new TokenPrincipal(subject, scopes, tenantId, issuer);
  }

  /** Vérifie si le principal a un scope donné. */
  public boolean hasScope(String scope) {
    return scopes != null && scopes.contains(scope);
  }

  /** Parse une string de scopes séparés par des espaces. */
  static List<String> parseScopes(String scopeString) {
    if (scopeString == null || scopeString.isBlank()) {
      return List.of();
    }
    return Arrays.stream(scopeString.trim().split("\\s+")).toList();
  }
}
