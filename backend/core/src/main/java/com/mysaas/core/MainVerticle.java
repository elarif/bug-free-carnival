package com.mysaas.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

/**
 * Verticle principal placeholder du module core. Sera étendu par le module api (HttpServerVerticle) en Phase 3.
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    LOG.info("mysaas-core MainVerticle démarré (placeholder)");
    startPromise.complete();
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    LOG.info("mysaas-core MainVerticle arrêté");
    stopPromise.complete();
  }
}
