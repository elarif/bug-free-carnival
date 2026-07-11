package com.mysaas.identity;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Factory des composants identity pour la prod — assemble le {@link WebClient} Vert.x et le {@link
 * KratosClient} configuré avec l'URL publique de Kratos.
 *
 * <p>Usage depuis le module api :
 *
 * <pre>
 * IdentityComponents identity = IdentityComponents.create(vertx, appConfig.kratosPublicUrl());
 * identity.sessionFilter().mount(router);
 * </pre>
 */
public final class IdentityComponents {

  private final WebClient webClient;
  private final KratosClient kratosClient;

  public IdentityComponents(WebClient webClient, KratosClient kratosClient) {
    this.webClient = webClient;
    this.kratosClient = kratosClient;
  }

  /** Construit les composants identity depuis l'URL publique de Kratos. */
  public static IdentityComponents create(Vertx vertx, String kratosPublicUrl) {
    WebClient webClient = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true));
    KratosClient kratosClient = new KratosClient(webClient, kratosPublicUrl);
    return new IdentityComponents(webClient, kratosClient);
  }

  public KratosClient kratosClient() {
    return kratosClient;
  }

  /** Crée le filtre de session Kratos. */
  public KratosSessionFilter sessionFilter() {
    return new KratosSessionFilter(kratosClient);
  }

  /** Ferme le WebClient. */
  public void close() {
    if (webClient != null) {
      webClient.close();
    }
  }
}
