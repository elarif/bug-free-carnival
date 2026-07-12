package com.mysaas.authz;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthzComponentsTest {

  private Vertx vertx;

  @Test
  void createBuildsKetoClientAndFilter() {
    vertx = Vertx.vertx();
    AuthzComponents components = AuthzComponents.create(vertx, "http://keto-read:4466");

    assertThat(components).isNotNull();
    assertThat(components.ketoClient()).isNotNull();
    assertThat(components.authzFilter()).isNotNull();

    components.close();
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }
}
