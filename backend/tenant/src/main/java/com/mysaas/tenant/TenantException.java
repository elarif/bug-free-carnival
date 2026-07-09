package com.mysaas.tenant;

/**
 * Exception runtime pour les erreurs liées aux tenants (résolution, registry, schémas).
 *
 * <p>Non-checked pour ne pas polluer les signatures des handlers Vert.x.
 */
public class TenantException extends RuntimeException {

  public TenantException(String message) {
    super(message);
  }

  public TenantException(String message, Throwable cause) {
    super(message, cause);
  }
}
