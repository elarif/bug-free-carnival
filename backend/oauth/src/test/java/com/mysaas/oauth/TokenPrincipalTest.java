package com.mysaas.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenPrincipalTest {

  @Test
  void fromJwtClaimsExtractsSubjectScopesAndTenantId() {
    JsonObject claims =
        new JsonObject()
            .put("sub", "00000000-0000-0000-0000-000000000001")
            .put("iss", "http://localhost:4444/")
            .put("scope", "openid profile tenant:acme")
            .put("tenant_id", "acme");

    TokenPrincipal principal = TokenPrincipal.fromJwtClaims(claims);

    assertThat(principal.subject()).isEqualTo("00000000-0000-0000-0000-000000000001");
    assertThat(principal.issuer()).isEqualTo("http://localhost:4444/");
    assertThat(principal.scopes()).containsExactly("openid", "profile", "tenant:acme");
    assertThat(principal.tenantId()).isEqualTo("acme");
  }

  @Test
  void fromJwtClaimsHandlesMissingTenantId() {
    JsonObject claims =
        new JsonObject()
            .put("sub", "user-2")
            .put("iss", "http://hydra:4444/")
            .put("scope", "openid");

    TokenPrincipal principal = TokenPrincipal.fromJwtClaims(claims);

    assertThat(principal.tenantId()).isNull();
    assertThat(principal.scopes()).containsExactly("openid");
  }

  @Test
  void fromJwtClaimsHandlesMissingScope() {
    JsonObject claims = new JsonObject().put("sub", "user-3").put("iss", "http://hydra:4444/");

    TokenPrincipal principal = TokenPrincipal.fromJwtClaims(claims);

    assertThat(principal.scopes()).isEmpty();
  }

  @Test
  void fromIntrospectionExtractsSubjectScopesAndTenantId() {
    JsonObject introspection =
        new JsonObject()
            .put("active", true)
            .put("sub", "user-4")
            .put("scope", "read write")
            .put("tenant_id", "globex");

    TokenPrincipal principal = TokenPrincipal.fromIntrospection(introspection);

    assertThat(principal.subject()).isEqualTo("user-4");
    assertThat(principal.scopes()).containsExactly("read", "write");
    assertThat(principal.tenantId()).isEqualTo("globex");
  }

  @Test
  void hasScopeReturnsTrueForPresentScope() {
    TokenPrincipal principal =
        new TokenPrincipal("user", List.of("read", "write"), "acme", "http://hydra:4444/");

    assertThat(principal.hasScope("read")).isTrue();
    assertThat(principal.hasScope("admin")).isFalse();
  }
}
