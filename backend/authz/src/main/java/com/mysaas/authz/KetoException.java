package com.mysaas.authz;

/**
 * Exception levée par {@link KetoClient} quand l'API Keto est injoignable ou retourne une erreur.
 */
public final class KetoException extends RuntimeException {

  private final int statusCode;

  public KetoException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
