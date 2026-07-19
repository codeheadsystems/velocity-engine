// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.SubjectSource;
import com.codeheadsystems.velocity.core.model.yaml.FeatureDefinitionDoc;
import com.codeheadsystems.velocity.core.model.yaml.FeatureDefinitionDoc.AggregationDoc;
import com.codeheadsystems.velocity.core.model.yaml.FeatureDefinitionDoc.SubjectSourceDoc;
import com.codeheadsystems.velocity.core.model.yaml.FeatureDefinitionDoc.WindowDoc;
import com.codeheadsystems.velocity.core.model.yaml.FeatureDefinitionsDoc;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Imports and exports feature definitions as round-trippable YAML (FR-28).
 *
 * <p>The YAML document is the canonical external representation of a namespace's feature
 * configuration, so it can be versioned in source control and moved between environments. The wire
 * shape ({@link FeatureDefinitionsDoc}) is a plain Jackson DTO kept separate from the domain, so
 * the domain stays serialization-neutral; this class maps between the two. Unknown fields are
 * tolerated on read for forward compatibility.
 *
 * <p>Shape (durations are ISO-8601, e.g. {@code PT1H}):
 *
 * <pre>{@code
 * namespace: acme
 * definitions:
 *   - name: card.count.1h
 *     subjectType: card
 *     subjectSource: { kind: PRIMARY }
 *     backend: memory
 *     aggregation: { type: COUNT }
 *     windows:
 *       - { duration: PT1H, type: TUMBLING }
 *   - name: ip.count.1h
 *     subjectType: ip
 *     subjectSource: { kind: FROM_DIMENSION, dimension: ip }
 *     backend: memory
 *     aggregation: { type: COUNT }
 *     windows:
 *       - { duration: PT1M, type: TUMBLING }
 * }</pre>
 */
public final class FeatureDefinitionYaml {

  private final YAMLMapper mapper;

  /** Creates a YAML codec with a forward-compatible mapper. */
  public FeatureDefinitionYaml() {
    this.mapper =
        YAMLMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
  }

  /**
   * Exports a namespace's definitions to YAML.
   *
   * @param namespace the namespace to stamp on the document
   * @param definitions the definitions to export
   * @return the YAML document
   */
  public String export(final Namespace namespace, final List<FeatureDefinition> definitions) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(definitions, "definitions");
    final List<FeatureDefinitionDoc> docs = new ArrayList<>(definitions.size());
    for (final FeatureDefinition definition : definitions) {
      docs.add(toDoc(definition));
    }
    return mapper.writeValueAsString(new FeatureDefinitionsDoc(namespace.value(), docs));
  }

  /**
   * Imports the definitions from a YAML document.
   *
   * @param yaml the YAML document
   * @return the domain definitions
   */
  public List<FeatureDefinition> importDefinitions(final String yaml) {
    Objects.requireNonNull(yaml, "yaml");
    final FeatureDefinitionsDoc doc = mapper.readValue(yaml, FeatureDefinitionsDoc.class);
    final List<FeatureDefinitionDoc> definitions =
        doc.definitions() == null ? List.of() : doc.definitions();
    final List<FeatureDefinition> result = new ArrayList<>(definitions.size());
    for (final FeatureDefinitionDoc definition : definitions) {
      result.add(fromDoc(definition));
    }
    return result;
  }

  /**
   * The namespace declared in a YAML document, if any.
   *
   * @param yaml the YAML document
   * @return the namespace, or empty if the document does not declare one
   */
  public @Nullable Namespace importNamespace(final String yaml) {
    Objects.requireNonNull(yaml, "yaml");
    final FeatureDefinitionsDoc doc = mapper.readValue(yaml, FeatureDefinitionsDoc.class);
    final String namespace = doc.namespace();
    return namespace == null || namespace.isBlank() ? null : new Namespace(namespace);
  }

  private static FeatureDefinitionDoc toDoc(final FeatureDefinition definition) {
    final List<WindowDoc> windows = new ArrayList<>(definition.windows().size());
    for (final Window window : definition.windows()) {
      windows.add(new WindowDoc(window.duration().toString(), window.type().name()));
    }
    final Aggregation aggregation = definition.aggregation();
    return new FeatureDefinitionDoc(
        definition.name(),
        definition.subjectType(),
        toSubjectSourceDoc(definition.subjectSource()),
        new AggregationDoc(aggregation.type().name(), aggregation.dimension()),
        windows,
        definition.backend(),
        definition.distinctThreshold());
  }

  private static SubjectSourceDoc toSubjectSourceDoc(final SubjectSource source) {
    return switch (source) {
      case SubjectSource.Primary ignored -> new SubjectSourceDoc("PRIMARY", null);
      case SubjectSource.FromDimension fromDimension ->
          new SubjectSourceDoc("FROM_DIMENSION", fromDimension.dimensionName());
    };
  }

  private static FeatureDefinition fromDoc(final FeatureDefinitionDoc doc) {
    final AggregationDoc aggregationDoc =
        Objects.requireNonNull(doc.aggregation(), "aggregation is required");
    final List<WindowDoc> windowDocs =
        Objects.requireNonNull(doc.windows(), "windows are required");
    final List<Window> windows = new ArrayList<>(windowDocs.size());
    for (final WindowDoc windowDoc : windowDocs) {
      windows.add(
          new Window(
              Duration.parse(Objects.requireNonNull(windowDoc.duration(), "window duration")),
              WindowType.valueOf(Objects.requireNonNull(windowDoc.type(), "window type"))));
    }
    return new FeatureDefinition(
        Objects.requireNonNull(doc.name(), "name is required"),
        Objects.requireNonNull(doc.subjectType(), "subjectType is required"),
        fromSubjectSourceDoc(doc.subjectSource()),
        fromAggregationDoc(aggregationDoc),
        windows,
        Objects.requireNonNull(doc.backend(), "backend is required"),
        doc.distinctThreshold());
  }

  private static SubjectSource fromSubjectSourceDoc(final @Nullable SubjectSourceDoc doc) {
    if (doc == null || doc.kind() == null || "PRIMARY".equals(doc.kind())) {
      return SubjectSource.primary();
    }
    if ("FROM_DIMENSION".equals(doc.kind())) {
      return SubjectSource.fromDimension(
          Objects.requireNonNull(doc.dimension(), "FROM_DIMENSION requires a dimension"));
    }
    throw new IllegalArgumentException("unknown subjectSource kind '" + doc.kind() + "'");
  }

  private static Aggregation fromAggregationDoc(final AggregationDoc doc) {
    final AggregationType type =
        AggregationType.valueOf(Objects.requireNonNull(doc.type(), "aggregation type is required"));
    return switch (type) {
      case COUNT -> Aggregation.count();
      case SUM -> Aggregation.sum();
      case DISTINCT ->
          Aggregation.distinct(
              Objects.requireNonNull(doc.dimension(), "DISTINCT requires a dimension"));
    };
  }
}
