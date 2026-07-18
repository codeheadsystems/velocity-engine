// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;

/**
 * The structured key an aggregation is computed for (P5) — e.g. {@code (type="card", value="…")}.
 *
 * <p>A subject is a {@code (type, value)} pair rather than an opaque string so the engine and
 * backends can reason about the kind of thing being counted without parsing a delimited key.
 *
 * @param type the non-blank subject type (e.g. {@code "card"}, {@code "account"})
 * @param value the non-blank subject value
 */
public record Subject(String type, String value) {

  /** Validates that both components are present and non-blank. */
  public Subject {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    if (type.isBlank()) {
      throw new IllegalArgumentException("subject type must not be blank");
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("subject value must not be blank");
    }
  }
}
