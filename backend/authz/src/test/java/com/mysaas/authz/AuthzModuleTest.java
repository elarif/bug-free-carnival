package com.mysaas.authz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthzModuleTest {

  @Test
  void moduleIdReturnsAuthz() {
    assertThat(AuthzModule.moduleId()).isEqualTo("authz");
  }
}
