// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeheadsystems.velocity.testkit.InMemoryVelocityBackend;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackendRegistryTest {

  private final InMemoryVelocityBackend backend = new InMemoryVelocityBackend();

  @Test
  void resolvesRegisteredBackend() {
    final BackendRegistry registry = new BackendRegistry(Map.of("memory", backend));
    assertThat(registry.backend("memory")).isSameAs(backend);
    assertThat(registry.names()).containsExactly("memory");
  }

  @Test
  void unknownBackendThrowsWithKnownNames() {
    final BackendRegistry registry = new BackendRegistry(Map.of("memory", backend));
    assertThatThrownBy(() -> registry.backend("redis"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("redis")
        .hasMessageContaining("memory");
  }

  @Test
  void emptyRegistryIsRejected() {
    assertThatThrownBy(() -> new BackendRegistry(Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankNameIsRejected() {
    assertThatThrownBy(() -> new BackendRegistry(Map.of(" ", backend)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
