// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model;

import java.util.Objects;

/**
 * Where a feature's fan-out subject comes from for a given event (D5, FR-18).
 *
 * <p>A single {@code record()} can update a feature keyed on the event's own subject <em>and</em>
 * features keyed on a dimension-derived subject (e.g. also counting per-{@code ip}). This sealed
 * type names those two origins so the {@code FanOutResolver} can decide, per definition, which
 * {@link com.codeheadsystems.velocity.spi.model.Subject subject} an event contributes to.
 */
public sealed interface SubjectSource permits SubjectSource.Primary, SubjectSource.FromDimension {

  /**
   * The event's own primary subject (the subject passed to {@code record()}). The definition
   * applies only when that subject's type matches the definition's {@code subjectType}.
   */
  record Primary() implements SubjectSource {}

  /**
   * A subject derived from one of the event's dimensions (FR-18). The definition applies only when
   * the named dimension is present on the event; the derived subject is {@code (subjectType,
   * dimensionValue)}.
   *
   * @param dimensionName the non-blank dimension whose value becomes the subject value
   */
  record FromDimension(String dimensionName) implements SubjectSource {

    /** Validates the dimension name is present and non-blank. */
    public FromDimension {
      Objects.requireNonNull(dimensionName, "dimensionName");
      if (dimensionName.isBlank()) {
        throw new IllegalArgumentException("dimensionName must not be blank");
      }
    }
  }

  /**
   * The event's own primary subject.
   *
   * @return a {@link Primary} source
   */
  static SubjectSource primary() {
    return new Primary();
  }

  /**
   * A subject derived from the named dimension.
   *
   * @param dimensionName the dimension whose value becomes the subject value
   * @return a {@link FromDimension} source
   */
  static SubjectSource fromDimension(String dimensionName) {
    return new FromDimension(dimensionName);
  }
}
