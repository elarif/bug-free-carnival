package com.mysaas.authz;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Factory des composants authz pour la prod — assemble le {@link WebClient} et le {@link
 * KetoClient} configuré avec l'URL read de Keto.
 */
public final class AuthzComponents {

  private final WebClient webClient;
  private final KetoClient ketoClient;

  public AuthzComponents(WebClient webClient, KetoClient ketoClient) {
    this.webClient = webClient;
    this.ketoClient = ketoClient;
  }

  /** Construit les composants authz depuis l'URL read de Keto. */
  public static AuthzComponents create(Vertx vertx, String ketoReadUrl) {
    WebClient webClient = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true));
    KetoClient ketoClient = new KetoClient(webClient, ketoReadUrl);
    return new AuthzComponents(webClient, ketoClient);
  }

  public KetoClient ketoClient() {
    return ketoClient;
  }

  /** Crée le filtre d'autorisation Keto. */
  public KetoAuthzFilter authzFilter() {
    return new KetoAuthzFilter(ketoClient);
  }

  public void close() {
    if (webClient != null) {
      webClient.close();
    }
  }
}
