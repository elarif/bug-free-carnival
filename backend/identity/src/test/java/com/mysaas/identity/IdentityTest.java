package com.mysaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class IdentityTest {

  @Test
  void fromKratosWhoamiExtractsIdAndTenantId() {
    JsonObject whoami =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000001")
                    .put(
                        "traits",
                        new JsonObject().put("email", "alice@acme.com").put("tenant_id", "acme")));

    Identity identity = Identity.fromWhoami(whoami);

    assertThat(identity.id()).isEqualTo("00000000-0000-0000-0000-000000000001");
    assertThat(identity.tenantId()).isEqualTo("acme");
    assertThat(identity.email()).isEqualTo("alice@acme.com");
  }

  @Test
  void fromKratosWhoamiWithoutTenantIdReturnsNullTenant() {
    JsonObject whoami =
        new JsonObject()
            .put(
                "identity",
                new JsonObject()
                    .put("id", "00000000-0000-0000-0000-000000000002")
                    .put("traits", new JsonObject().put("email", "bob@example.com")));

    Identity identity = Identity.fromWhoami(whoami);

    assertThat(identity.id()).isEqualTo("00000000-0000-0000-0000-000000000002");
    assertThat(identity.tenantId()).isNull();
    assertThat(identity.email()).isEqualTo("bob@example.com");
  }

  @Test
  void fromKratosWhoamiWithMissingIdentityReturnsNull() {
    JsonObject whoami = new JsonObject().put("foo", "bar");
    assertThat(Identity.fromWhoami(whoami)).isNull();
  }

  @Test
  void fromKratosWhoamiWithNullInputReturnsNull() {
    assertThat(Identity.fromWhoami(null)).isNull();
  }
}
