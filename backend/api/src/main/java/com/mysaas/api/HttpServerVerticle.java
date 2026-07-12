package com.mysaas.api;

import com.mysaas.api.config.AppConfig;
import com.mysaas.api.health.HealthHandler;
import com.mysaas.authz.AuthzComponents;
import com.mysaas.identity.IdentityComponents;
import com.mysaas.identity.RegistrationWebhookHandler;
import com.mysaas.oauth.HydraTokenFilter;
import com.mysaas.oauth.OauthComponents;
import com.mysaas.oauth.TokenPrincipal;
import com.mysaas.tenant.TenantAdminHandler;
import com.mysaas.tenant.TenantComponents;
import com.mysaas.tenant.TenantFilter;
import com.mysaas.tenant.TenantResolver;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
  private Optional<OauthComponents> oauth = Optional.empty();
  private Optional<AuthzComponents> authz = Optional.empty();

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

    // Couche oauth : validation Bearer token (JWT JWKS + introspection fallback)
    initOauthLayer(router);

    // Couche authz : permissions par tenant via Keto
    initAuthzLayer(router);

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

  /** Initialise la couche oauth (HydraTokenFilter + endpoint /me). */
  private void initOauthLayer(Router router) {
    try {
      OauthComponents components =
          OauthComponents.create(
              vertx,
              appConfig.hydraJwksUrl(),
              appConfig.hydraPublicUrl(),
              appConfig.hydraAdminUrl(),
              appConfig.oauthClientId(),
              appConfig.oauthClientSecret());
      this.oauth = Optional.of(components);

      components.tokenFilter().mount(router);
      mountMeEndpoint(router);
      LOG.info(
          "Couche oauth initialisée (Hydra JWKS: {}, admin: {})",
          appConfig.hydraJwksUrl(),
          appConfig.hydraAdminUrl());
    } catch (Exception e) {
      LOG.warn(
          "Couche oauth désactivée (Hydra non configuré): {} — démarrage en mode dégradé",
          e.getMessage());
    }
  }

  /** Initialise la couche authz (KetoAuthzFilter — permissions par tenant). */
  private void initAuthzLayer(Router router) {
    try {
      AuthzComponents components = AuthzComponents.create(vertx, appConfig.ketoReadUrl());
      this.authz = Optional.of(components);
      components.authzFilter().mount(router);
      LOG.info("Couche authz initialisée (Keto read: {})", appConfig.ketoReadUrl());
    } catch (Exception e) {
      LOG.warn(
          "Couche authz désactivée (Keto non configuré): {} — démarrage en mode dégradé",
          e.getMessage());
    }
  }

  /** Endpoint protégé /me — retourne le principal du token Bearer. */
  private void mountMeEndpoint(Router router) {
    router
        .get("/me")
        .handler(
            ctx -> {
              TokenPrincipal principal = ctx.get(HydraTokenFilter.CTX_KEY);
              if (principal == null) {
                ctx.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "unauthorized").encode());
                return;
              }
              ctx.json(
                  new JsonObject()
                      .put("subject", principal.subject())
                      .put("tenant_id", principal.tenantId())
                      .put("issuer", principal.issuer())
                      .put("scopes", new JsonArray(principal.scopes())));
            });
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
