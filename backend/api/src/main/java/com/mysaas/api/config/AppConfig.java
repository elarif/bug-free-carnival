package com.mysaas.api.config;

import io.vertx.core.json.JsonObject;

/**
 * Configuration de l'API chargée depuis les variables d'environnement (ou valeurs par défaut pour
 * le dev local).
 */
public final class AppConfig {

  private final int httpPort;
  private final String dbUrl;
  private final String dbUser;
  private final String dbPassword;
  private final String kratosPublicUrl;
  private final String kratosAdminUrl;
  private final String hydraPublicUrl;
  private final String hydraAdminUrl;
  private final String hydraJwksUrl;
  private final String oauthClientId;
  private final String oauthClientSecret;
  private final String ketoReadUrl;
  private final String ketoWriteUrl;

  public AppConfig(JsonObject config) {
    this.httpPort = config.getInteger("http.port", 8080);
    this.dbUrl = config.getString("db.url", "jdbc:postgresql://localhost:5432/mysaas");
    this.dbUser = config.getString("db.user", "mysaas");
    this.dbPassword = config.getString("db.password", "mysaas-dev");
    this.kratosPublicUrl = config.getString("kratos.public.url", "http://localhost:4433");
    this.kratosAdminUrl = config.getString("kratos.admin.url", "http://localhost:4434");
    this.hydraPublicUrl = config.getString("hydra.public.url", "http://localhost:4444");
    this.hydraAdminUrl = config.getString("hydra.admin.url", "http://localhost:4445");
    this.hydraJwksUrl =
        config.getString("hydra.jwks.url", this.hydraPublicUrl + "/.well-known/jwks.json");
    this.oauthClientId = config.getString("oauth.client.id", "mysaas-client");
    this.oauthClientSecret = config.getString("oauth.client.secret", "mysaas-client-secret");
    this.ketoReadUrl = config.getString("keto.read.url", "http://localhost:4466");
    this.ketoWriteUrl = config.getString("keto.write.url", "http://localhost:4467");
  }

  public int httpPort() {
    return httpPort;
  }

  public String dbUrl() {
    return dbUrl;
  }

  public String dbUser() {
    return dbUser;
  }

  public String dbPassword() {
    return dbPassword;
  }

  public String kratosPublicUrl() {
    return kratosPublicUrl;
  }

  public String kratosAdminUrl() {
    return kratosAdminUrl;
  }

  public String hydraPublicUrl() {
    return hydraPublicUrl;
  }

  public String hydraAdminUrl() {
    return hydraAdminUrl;
  }

  public String hydraJwksUrl() {
    return hydraJwksUrl;
  }

  public String oauthClientId() {
    return oauthClientId;
  }

  public String oauthClientSecret() {
    return oauthClientSecret;
  }

  public String ketoReadUrl() {
    return ketoReadUrl;
  }

  public String ketoWriteUrl() {
    return ketoWriteUrl;
  }

  /** Construit la config depuis les variables d'environnement. */
  public static AppConfig fromEnv() {
    JsonObject config = new JsonObject();
    config.put("http.port", envInt("HTTP_PORT", 8080));
    config.put("db.url", env("DB_URL", "jdbc:postgresql://localhost:5432/mysaas"));
    config.put("db.user", env("DB_USER", "mysaas"));
    config.put("db.password", env("DB_PASSWORD", "mysaas-dev"));
    config.put("kratos.public.url", env("KRATOS_PUBLIC_URL", "http://localhost:4433"));
    config.put("kratos.admin.url", env("KRATOS_ADMIN_URL", "http://localhost:4434"));
    config.put("hydra.public.url", env("HYDRA_PUBLIC_URL", "http://localhost:4444"));
    config.put("hydra.admin.url", env("HYDRA_ADMIN_URL", "http://localhost:4445"));
    config.put(
        "hydra.jwks.url", env("HYDRA_JWKS_URL", "http://localhost:4444/.well-known/jwks.json"));
    config.put("oauth.client.id", env("OAUTH_CLIENT_ID", "mysaas-client"));
    config.put("oauth.client.secret", env("OAUTH_CLIENT_SECRET", "mysaas-client-secret"));
    config.put("keto.read.url", env("KETO_READ_URL", "http://localhost:4466"));
    config.put("keto.write.url", env("KETO_WRITE_URL", "http://localhost:4467"));
    return new AppConfig(config);
  }

  private static String env(String key, String def) {
    String val = System.getenv(key);
    return val != null ? val : def;
  }

  private static int envInt(String key, int def) {
    String val = System.getenv(key);
    return val != null ? Integer.parseInt(val) : def;
  }
}
