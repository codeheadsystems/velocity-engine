// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinitionVersionHasherTest {

  @Test
  void isDeterministic() {
    assertThat(DefinitionVersionHasher.hash(Fixtures.allDefinitions()))
        .isEqualTo(DefinitionVersionHasher.hash(Fixtures.allDefinitions()));
  }

  @Test
  void isOrderIndependent() {
    final List<FeatureDefinition> forward = Fixtures.allDefinitions();
    final List<FeatureDefinition> reversed =
        List.of(
            Fixtures.ipCount(),
            Fixtures.cardDistinctIp(),
            Fixtures.cardSum(),
            Fixtures.cardCount());
    assertThat(DefinitionVersionHasher.hash(forward))
        .isEqualTo(DefinitionVersionHasher.hash(reversed));
  }

  @Test
  void changesWhenADefinitionChanges() {
    assertThat(DefinitionVersionHasher.hash(List.of(Fixtures.cardCount())))
        .isNotEqualTo(DefinitionVersionHasher.hash(List.of(Fixtures.cardSum())));
  }

  @Test
  void emptySetHashesStably() {
    assertThat(DefinitionVersionHasher.hash(List.of()))
        .isEqualTo(DefinitionVersionHasher.hash(List.of()))
        .isNotBlank();
  }
}
