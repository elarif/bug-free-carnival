package com.mysaas.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantResolverTest {

  private final TenantRegistry registry = new InMemoryTenantRegistry();
  private final TenantResolver resolver = new TenantResolver(registry);

  @Test
  void resolveByHeaderXTenant() {
    TenantContext ctx = resolver.resolve("localhost:8080", "acme");
    assertThat(ctx).isNotNull();
    assertThat(ctx.slug()).isEqualTo("acme");
    assertThat(ctx.schemaName()).isEqualTo("tenant_acme");
  }

  @Test
  void resolveBySubdomain() {
    TenantContext ctx = resolver.resolve("acme.app.local:8080", null);
    assertThat(ctx).isNotNull();
    assertThat(ctx.slug()).isEqualTo("acme");
  }

  @Test
  void headerTakesPrecedenceOverSubdomain() {
    TenantContext ctx = resolver.resolve("demo.app.local:8080", "acme");
    assertThat(ctx).isNotNull();
    assertThat(ctx.slug()).isEqualTo("acme");
  }

  @Test
  void returnsNullForLocalhostWithoutHeader() {
    TenantContext ctx = resolver.resolve("localhost:8080", null);
    assertThat(ctx).isNull();
  }

  @Test
  void returnsNullForIpWithoutHeader() {
    TenantContext ctx = resolver.resolve("127.0.0.1:8080", null);
    assertThat(ctx).isNull();
  }

  @Test
  void returnsNullForUnknownTenant() {
    TenantContext ctx = resolver.resolve("localhost:8080", "unknown");
    assertThat(ctx).isNull();
  }

  @Test
  void returnsNullForEmptyHeader() {
    TenantContext ctx = resolver.resolve("localhost:8080", "");
    assertThat(ctx).isNull();
  }

  @Test
  void returnsNullForNullHost() {
    TenantContext ctx = resolver.resolve(null, null);
    assertThat(ctx).isNull();
  }

  @Test
  void slugIsLowercased() {
    TenantContext ctx = resolver.resolve("localhost:8080", "ACME");
    assertThat(ctx).isNotNull();
    assertThat(ctx.slug()).isEqualTo("acme");
  }

  @Test
  void schemaNameForSlug() {
    assertThat(TenantContext.schemaNameFor("acme")).isEqualTo("tenant_acme");
    assertThat(TenantContext.schemaNameFor("default")).isEqualTo("tenant_default");
  }
}
