package com.mysaas.identity;

/**
 * Exception levée par {@link KratosClient} quand la session Kratos est invalide, absente, ou que
 * Kratos renvoie un statut d'erreur.
 */
public final class KratosSessionException extends RuntimeException {

  private final int statusCode;

  public KratosSessionException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}
