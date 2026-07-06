package com.mysaas.tenant;

/**
 * Placeholder du module tenant. Sera implémenté en Phase 4 (TenantResolver, TenantContext, TenantSchemaManager).
 */
public final class TenantModule {

  private TenantModule() {
    // utilitaire
  }

  static String moduleId() {
    return "tenant";
  }
}
