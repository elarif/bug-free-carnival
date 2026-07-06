package com.mysaas.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MainVerticleTest {

  @Test
  void mainVerticleClassExists() {
    assertThat(new MainVerticle()).isNotNull();
  }
}
