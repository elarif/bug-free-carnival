package com.mysaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantModuleTest {

  @Test
  void moduleIdReturnsTenant() {
    assertThat(TenantModule.moduleId()).isEqualTo("tenant");
  }
}
