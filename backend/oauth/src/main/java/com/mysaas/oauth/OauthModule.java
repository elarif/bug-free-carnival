package com.mysaas.oauth;

/**
 * Placeholder du module oauth. Sera implémenté en Phase 6 (HydraTokenFilter, validation JWT,
 * introspection fallback).
 */
public final class OauthModule {

  private OauthModule() {
    // utilitaire
  }

  static String moduleId() {
    return "oauth";
  }
}
