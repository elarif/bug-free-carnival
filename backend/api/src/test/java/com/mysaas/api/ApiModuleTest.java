package com.mysaas.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiModuleTest {

  @Test
  void moduleIdReturnsApi() {
    assertThat(ApiModule.moduleId()).isEqualTo("api");
  }
}
