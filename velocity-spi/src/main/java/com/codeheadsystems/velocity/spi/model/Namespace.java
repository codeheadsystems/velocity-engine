// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;

/**
 * A first-class tenancy scope (§2.1). Every key and SPI operation is namespace-scoped.
 *
 * @param value the non-blank namespace identifier
 */
public record Namespace(String value) {

  /** Validates the namespace value is present and non-blank. */
  public Namespace {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("namespace value must not be blank");
    }
  }
}
