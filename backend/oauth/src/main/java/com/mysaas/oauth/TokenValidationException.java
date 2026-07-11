package com.mysaas.oauth;

/**
 * Exception levée quand la validation d'un token Hydra échoue (JWT invalide, expiré, mauvais
 * issuer).
 */
public final class TokenValidationException extends RuntimeException {

  private final int statusCode;

  public TokenValidationException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public TokenValidationException(int statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
