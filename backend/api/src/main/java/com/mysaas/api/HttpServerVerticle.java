package com.mysaas.api;

import com.mysaas.api.config.AppConfig;
import com.mysaas.api.health.HealthHandler;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verticle qui démarre le serveur HTTP et monte les routes. */
public class HttpServerVerticle extends VerticleBase {

  private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticle.class);

  private final AppConfig appConfig;

  public HttpServerVerticle(AppConfig appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  public Future<?> start() {
    Router router = Router.router(vertx);

    HealthHandler.ReadyChecker readyChecker = createReadyChecker();
    new HealthHandler(readyChecker).mount(router);

    return vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(appConfig.httpPort())
        .onSuccess(server -> LOG.info("Serveur HTTP démarré sur le port {}", appConfig.httpPort()))
        .onFailure(err -> LOG.error("Échec du démarrage du serveur HTTP", err))
        .mapEmpty();
  }

  /**
   * Crée le checker de readiness (ping Postgres via TCP).
   *
   * <p>En Phase 3, le check est un simple ping TCP sur le port Postgres. En Phase 4, il sera
   * remplacé par un vrai ping JDBC via le pool de connexions.
   */
  private HealthHandler.ReadyChecker createReadyChecker() {
    return () -> {
      String host = extractHost(appConfig.dbUrl());
      int port = extractPort(appConfig.dbUrl());
      LOG.debug("Readiness: ping Postgres {}:{}", host, port);
      return vertx
          .createNetClient()
          .connect(port, host)
          .onSuccess(
              socket -> {
                LOG.debug("Readiness: Postgres accessible");
                socket.close();
              })
          .mapEmpty();
    };
  }

  private static String extractHost(String jdbcUrl) {
    String withoutProtocol = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
    return withoutProtocol.substring(0, withoutProtocol.indexOf(":"));
  }

  private static int extractPort(String jdbcUrl) {
    String withoutProtocol = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
    String portPart = withoutProtocol.substring(withoutProtocol.indexOf(":") + 1);
    return Integer.parseInt(portPart.substring(0, portPart.indexOf("/")));
  }
}
