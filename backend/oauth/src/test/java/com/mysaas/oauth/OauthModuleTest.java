package com.mysaas.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OauthModuleTest {

  @Test
  void moduleIdReturnsOauth() {
    assertThat(OauthModule.moduleId()).isEqualTo("oauth");
  }
}
