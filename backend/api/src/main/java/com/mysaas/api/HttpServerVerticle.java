package com.mysaas.api;

import com.mysaas.api.config.AppConfig;
import com.mysaas.api.health.HealthHandler;
import com.mysaas.identity.IdentityComponents;
import com.mysaas.identity.RegistrationWebhookHandler;
import com.mysaas.tenant.TenantAdminHandler;
import com.mysaas.tenant.TenantComponents;
import com.mysaas.tenant.TenantFilter;
import com.mysaas.tenant.TenantResolver;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verticle qui démarre le serveur HTTP et monte les routes. */
public class HttpServerVerticle extends VerticleBase {

  private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticle.class);

  private final AppConfig appConfig;
  private Optional<TenantComponents> tenants = Optional.empty();
  private Optional<IdentityComponents> identity = Optional.empty();

  public HttpServerVerticle(AppConfig appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  public Future<?> start() {
    Router router = Router.router(vertx);

    // Couche tenant : résolution + filtre + admin (si DB configurée)
    initTenantLayer(router);

    // Couche identity : session Kratos + webhook after-registration
    initIdentityLayer(router);

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

  /** Initialise la couche tenant (DataSource HikariCP + migrations + filtre + admin CRUD). */
  private void initTenantLayer(Router router) {
    try {
      TenantComponents components =
          TenantComponents.create(appConfig.dbUrl(), appConfig.dbUser(), appConfig.dbPassword());
      components.migrate();
      this.tenants = Optional.of(components);

      new TenantFilter(new TenantResolver(components.registry())).mount(router);
      new TenantAdminHandler(components.registry(), components.schemaManager()).mount(router);
      LOG.info("Couche tenant initialisée (registry JDBC + migrations public.tenants)");
    } catch (Exception e) {
      LOG.warn(
          "Couche tenant désactivée (DB non joignable): {} — démarrage en mode dégradé",
          e.getMessage());
    }
  }

  /** Initialise la couche identity (KratosSessionFilter + webhook after-registration). */
  private void initIdentityLayer(Router router) {
    try {
      IdentityComponents components = IdentityComponents.create(vertx, appConfig.kratosPublicUrl());
      this.identity = Optional.of(components);

      components.sessionFilter().mount(router);
      if (tenants.isPresent()) {
        TenantComponents tc = tenants.get();
        new RegistrationWebhookHandler(tc.registry(), tc.schemaManager()).mount(router);
      }
      LOG.info("Couche identity initialisée (Kratos public: {})", appConfig.kratosPublicUrl());
    } catch (Exception e) {
      LOG.warn(
          "Couche identity désactivée (Kratos non configuré): {} — démarrage en mode dégradé",
          e.getMessage());
    }
  }

  /** Crée le checker de readiness : ping JDBC si la couche tenant est active, sinon ping TCP. */
  private HealthHandler.ReadyChecker createReadyChecker() {
    return () -> {
      Optional<DataSource> ds = tenants.map(TenantComponents::dataSource);
      if (ds.isPresent()) {
        LOG.debug("Readiness: ping JDBC Postgres");
        return vertx
            .executeBlocking(
                () -> {
                  try (var conn = ds.get().getConnection()) {
                    conn.isValid(2);
                    return null;
                  }
                })
            .mapEmpty();
      }
      String host = extractHost(appConfig.dbUrl());
      int port = extractPort(appConfig.dbUrl());
      LOG.debug("Readiness: ping TCP Postgres {}:{}", host, port);
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
