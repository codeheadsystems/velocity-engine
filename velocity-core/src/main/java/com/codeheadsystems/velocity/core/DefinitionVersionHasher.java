// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.core.model.SubjectSource;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.Window;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes the deterministic {@code versionHash} stamped onto a {@code FeatureDefinitions} snapshot
 * (FR-40).
 *
 * <p>The hash is a SHA-256 over a canonical, order-independent string form of the definitions:
 * definitions are sorted by name and each is rendered to a stable field-delimited line, so the same
 * set of definitions always hashes to the same value regardless of list order, and any change to
 * any field changes the hash. A caller that sees a value stamped with a different hash than it last
 * read knows the definition changed under it (FR-40, §15 R12).
 */
public final class DefinitionVersionHasher {

  private DefinitionVersionHasher() {}

  /**
   * Computes the version hash of a definition set.
   *
   * @param definitions the definitions to hash (any order)
   * @return a lowercase hex SHA-256 of the canonical form
   */
  public static String hash(final List<FeatureDefinition> definitions) {
    Objects.requireNonNull(definitions, "definitions");
    final List<String> lines = new ArrayList<>(definitions.size());
    for (final FeatureDefinition definition : definitions) {
      lines.add(canonical(definition));
    }
    lines.sort(String::compareTo);
    final String canonical = String.join("\n", lines);
    return sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
  }

  private static String canonical(final FeatureDefinition definition) {
    final StringBuilder builder = new StringBuilder();
    builder
        .append(definition.name())
        .append('|')
        .append(definition.subjectType())
        .append('|')
        .append(subjectSource(definition.subjectSource()))
        .append('|')
        .append(definition.backend())
        .append('|')
        .append(aggregation(definition.aggregation()))
        .append('|')
        .append(definition.distinctThreshold())
        .append('|');
    final List<String> windows = new ArrayList<>();
    for (final Window window : definition.windows()) {
      windows.add(window.type() + ":" + window.duration());
    }
    windows.sort(String::compareTo);
    builder.append(String.join(",", windows));
    return builder.toString();
  }

  private static String subjectSource(final SubjectSource source) {
    return switch (source) {
      case SubjectSource.Primary ignored -> "PRIMARY";
      case SubjectSource.FromDimension fromDimension ->
          "FROM_DIMENSION:" + fromDimension.dimensionName();
    };
  }

  private static String aggregation(final Aggregation aggregation) {
    return aggregation.type()
        + (aggregation.dimension() == null ? "" : ":" + aggregation.dimension());
  }

  private static String sha256Hex(final byte[] bytes) {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (final NoSuchAlgorithmException e) {
      // SHA-256 is a mandated JCA algorithm; its absence is an unrecoverable environment fault.
      throw new IllegalStateException("SHA-256 not available", e);
    }
    final byte[] hashed = digest.digest(bytes);
    final StringBuilder hex = new StringBuilder(hashed.length * 2);
    for (final byte b : hashed) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }
}
