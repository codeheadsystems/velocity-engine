// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

/**
 * The kind of aggregation a feature computes (D2, FR-11).
 *
 * <p>A closed set: {@link #COUNT} (how many events), {@link #SUM} (how much, in integer cents per
 * P3/FR-10), and {@link #DISTINCT} (how many distinct values of a named dimension). Adding a new
 * aggregation is an additive SPI change realized as a new capability mix-in defaulting to
 * unsupported (ADR 0003, NFR-17), not by mutating this enum's meaning.
 */
public enum AggregationType {
  /** Count of events for a subject in a window. */
  COUNT,
  /** Sum of a {@code BigDecimal}-cents value for a subject in a window (FR-10). */
  SUM,
  /** Cardinality of a named dimension's distinct values for a subject in a window (FR-11). */
  DISTINCT
}
