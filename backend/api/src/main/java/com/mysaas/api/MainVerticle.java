package com.mysaas.api;

import com.mysaas.api.config.AppConfig;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verticle principal — déploie HttpServerVerticle. */
public class MainVerticle extends VerticleBase {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    AppConfig appConfig = AppConfig.fromEnv();

    return vertx
        .deployVerticle(new HttpServerVerticle(appConfig))
        .onSuccess(id -> LOG.info("HttpServerVerticle déployé (deploymentID={})", id))
        .onFailure(err -> LOG.error("Échec du déploiement de HttpServerVerticle", err));
  }

  /** Point d'entrée — le Launcher a été retiré en Vert.x 5. */
  public static void main(String[] args) {
    LOG.info("Démarrage de mysaas-api...");
    Vertx vertx = Vertx.vertx();
    vertx
        .deployVerticle(new MainVerticle())
        .onSuccess(id -> LOG.info("Application démarrée (deploymentID={})", id))
        .onFailure(err -> LOG.error("Échec du démarrage", err));
  }
}
