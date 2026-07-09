package com.mysaas.api.health;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handlers pour /health (liveness) et /ready (readiness). */
public final class HealthHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HealthHandler.class);

  private final ReadyChecker readyChecker;

  public HealthHandler(ReadyChecker readyChecker) {
    this.readyChecker = readyChecker;
  }

  /** Montre les routes de health sur le router. */
  public void mount(Router router) {
    router.get("/health").handler(this::liveness);
    router.get("/ready").handler(this::readiness);
  }

  /** Liveness — toujours 200 si le verticle tourne. */
  void liveness(RoutingContext ctx) {
    LOG.debug("Liveness check");
    ctx.json(new JsonObject().put("status", "UP").put("check", "liveness"));
  }

  /** Readiness — 200 si Postgres est accessible, 503 sinon. */
  void readiness(RoutingContext ctx) {
    LOG.debug("Readiness check");
    readyChecker
        .check()
        .onSuccess(
            ok -> {
              ctx.json(
                  new JsonObject()
                      .put("status", "UP")
                      .put("check", "readiness")
                      .put("postgres", "UP"));
            })
        .onFailure(
            err -> {
              LOG.warn("Readiness check failed: {}", err.getMessage());
              ctx.response()
                  .setStatusCode(503)
                  .putHeader("Content-Type", "application/json")
                  .end(
                      new JsonObject()
                          .put("status", "DOWN")
                          .put("check", "readiness")
                          .put("postgres", "DOWN")
                          .put("error", err.getMessage())
                          .encode());
            });
  }

  /** Interface du checker de readiness (Postgres ping). */
  @FunctionalInterface
  public interface ReadyChecker {
    Future<Void> check();
  }
}
