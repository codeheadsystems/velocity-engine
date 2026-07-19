// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.model.DistinctMember;
import com.codeheadsystems.velocity.spi.model.Namespace;
import org.junit.jupiter.api.Test;

class DimensionHasherTest {

  private static final Namespace ACME = new Namespace("acme");
  private final DimensionHasher hasher =
      new DimensionHasher(new InMemoryNamespaceSaltProvider("fixed".getBytes(UTF_8)));

  @Test
  void sameValueHashesDeterministically() {
    assertThat(hasher.hash(ACME, "203.0.113.7")).isEqualTo(hasher.hash(ACME, "203.0.113.7"));
  }

  @Test
  void differentValuesProduceDifferentMembers() {
    assertThat(hasher.hash(ACME, "1.1.1.1")).isNotEqualTo(hasher.hash(ACME, "2.2.2.2"));
  }

  @Test
  void tokenIsAFixedWidthHashNotTheRawValue() {
    final DistinctMember member = hasher.hash(ACME, "203.0.113.7");
    assertThat(member.token()).hasSize(32);
    // FR-38/R11: the raw dimension value is never what gets stored.
    assertThat(member.token()).isNotEqualTo("203.0.113.7".getBytes(UTF_8));
  }

  @Test
  void saltIsPerNamespace() {
    assertThat(hasher.hash(ACME, "203.0.113.7"))
        .isNotEqualTo(hasher.hash(new Namespace("beta"), "203.0.113.7"));
  }

  @Test
  void differentMasterSecretsProduceDifferentMembers() {
    final DimensionHasher other =
        new DimensionHasher(new InMemoryNamespaceSaltProvider("other".getBytes(UTF_8)));
    assertThat(hasher.hash(ACME, "203.0.113.7")).isNotEqualTo(other.hash(ACME, "203.0.113.7"));
  }
}
