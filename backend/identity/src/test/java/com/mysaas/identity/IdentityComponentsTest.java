package com.mysaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IdentityComponentsTest {

  private Vertx vertx;

  @Test
  void createBuildsKratosClientWithConfiguredUrl() {
    vertx = Vertx.vertx();
    IdentityComponents components =
        IdentityComponents.create(vertx, "http://kratos-public.ory.svc:4433");

    assertThat(components).isNotNull();
    assertThat(components.kratosClient()).isNotNull();

    components.close();
  }

  @Test
  void createHandlesTrailingSlashInUrl() {
    vertx = Vertx.vertx();
    IdentityComponents components = IdentityComponents.create(vertx, "http://localhost:4433/");

    assertThat(components).isNotNull();
    assertThat(components.kratosClient()).isNotNull();

    components.close();
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }
}
