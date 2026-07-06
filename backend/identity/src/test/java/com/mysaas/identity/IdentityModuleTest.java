package com.mysaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityModuleTest {

  @Test
  void moduleIdReturnsIdentity() {
    assertThat(IdentityModule.moduleId()).isEqualTo("identity");
  }
}
