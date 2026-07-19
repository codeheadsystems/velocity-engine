// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.SubjectSource;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.Namespace;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureDefinitionYamlTest {

  private static final Namespace ACME = new Namespace("acme");
  private final FeatureDefinitionYaml yaml = new FeatureDefinitionYaml();

  private static FeatureDefinition distinctWithThreshold() {
    return new FeatureDefinition(
        "card.distinct.ip.capped",
        "card",
        SubjectSource.primary(),
        Aggregation.distinct("ip"),
        List.of(Fixtures.TUMBLING_1H),
        Fixtures.BACKEND,
        5000L);
  }

  @Test
  void exportThenImportRoundTrips() {
    final List<FeatureDefinition> definitions =
        List.of(
            Fixtures.cardCount(),
            Fixtures.cardSum(),
            Fixtures.cardDistinctIp(),
            Fixtures.ipCount(),
            distinctWithThreshold());

    final String exported = yaml.export(ACME, definitions);
    final List<FeatureDefinition> imported = yaml.importDefinitions(exported);

    assertThat(imported).isEqualTo(definitions);
    assertThat(yaml.importNamespace(exported)).isEqualTo(ACME);
  }

  @Test
  void exportedYamlIsHumanReadable() {
    final String exported = yaml.export(ACME, List.of(Fixtures.ipCount()));
    assertThat(exported)
        .contains("acme")
        .contains("ip.count")
        .contains("FROM_DIMENSION")
        .contains("PT1H");
  }

  @Test
  void subjectSourceDefaultsToPrimaryWhenAbsent() {
    final String withoutSource =
        """
        namespace: acme
        definitions:
          - name: card.count
            subjectType: card
            backend: memory
            aggregation:
              type: COUNT
            windows:
              - duration: PT1H
                type: TUMBLING
        """;
    final List<FeatureDefinition> imported = yaml.importDefinitions(withoutSource);
    assertThat(imported).hasSize(1);
    assertThat(imported.get(0).subjectSource()).isInstanceOf(SubjectSource.Primary.class);
  }

  @Test
  void unknownFieldsAreToleratedForForwardCompatibility() {
    final String withExtra =
        """
        namespace: acme
        futureField: ignored
        definitions:
          - name: card.count
            subjectType: card
            backend: memory
            someNewKnob: 7
            aggregation:
              type: COUNT
            windows:
              - duration: PT1M
                type: TUMBLING
        """;
    assertThat(yaml.importDefinitions(withExtra)).hasSize(1);
  }
}
