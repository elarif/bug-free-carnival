package com.mysaas.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OauthComponentsTest {

  private Vertx vertx;

  @Test
  void createBuildsValidatorAndIntrospectionClient() {
    vertx = Vertx.vertx();
    OauthComponents components =
        OauthComponents.create(
            vertx,
            "http://hydra-public:4444/.well-known/jwks.json",
            "http://localhost:4444/",
            "http://hydra-admin:4445",
            "client-id",
            "client-secret");

    assertThat(components).isNotNull();
    assertThat(components.tokenValidator()).isNotNull();
    assertThat(components.introspectionClient()).isNotNull();
    assertThat(components.tokenFilter()).isNotNull();

    components.close();
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }
}
