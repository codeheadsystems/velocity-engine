// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The HTTP wire DTOs, faithful to {@code velocity-api}'s OpenAPI contract. Money and every numeric
 * {@code value} are JSON <strong>strings of a decimal integer</strong> (integer cents for SUM;
 * count/cardinality for COUNT/DISTINCT) — resolving OQ-D, avoiding JSON-number precision loss.
 *
 * <p>Hand-written for this first increment; the eventual source of truth is the OpenAPI document,
 * from which these are to be generated (AR-3).
 */
public final class Wire {

  private Wire() {}

  /** A structured subject key. */
  public record SubjectDto(String type, String value) {}

  /** A window: ISO-8601 duration + {@code SLIDING|TUMBLING}. */
  public record WindowDto(String duration, String type) {}

  /** The half-open bounds a value covers. */
  public record WindowBoundsDto(String start, String end) {}

  /** POST /record request: subject + raw dimensions (hashed server-side) + optional money value. */
  public record RecordRequest(
      SubjectDto subject, @Nullable Map<String, String> dimensions, @Nullable String value) {}

  /** POST /query request: a subject and the feature names to read. */
  public record QueryRequest(SubjectDto subject, List<String> features) {}

  /** A computed feature value (ADR 0009 Success arm). */
  public record FeatureValueDto(
      String feature,
      WindowDto window,
      String value,
      String exactness,
      String readYourWriteLevel,
      @Nullable String definitionVersionHash,
      WindowBoundsDto windowBounds,
      String asOf) {}

  /**
   * A value-or-failure result (ADR 0009): {@code kind=SUCCESS} carries {@code value}; {@code
   * kind=FAILURE} carries {@code code} + optional {@code detail}. Never a silent zero.
   */
  public record ResultDto(
      String kind,
      @Nullable FeatureValueDto value,
      @Nullable String code,
      @Nullable String detail) {}

  /** One apply outcome per feature: status + result (FR-34). */
  public record PerFeatureDto(String feature, String status, ResultDto result) {}

  /** POST /record response. */
  public record RecordResponse(List<PerFeatureDto> perFeature) {}

  /** POST /query response: feature name to its per-window results. */
  public record QueryResponse(Map<String, List<ResultDto>> features) {}

  /** GET /capabilities response, mirroring {@code BackendCapabilities}. */
  public record CapabilitiesResponse(
      Set<String> aggregations,
      List<WindowSpecDto> windows,
      boolean distinctHllSliding,
      long distinctExactCardinalityClamp,
      long distinctHllThresholdDefault,
      String maxRetention,
      String readYourWriteLevel,
      boolean idempotencySupported,
      boolean seedSupported,
      int maxAtomicFanOut) {}

  /** A supported window spec. */
  public record WindowSpecDto(WindowDto window, String exactness, String granularity) {}

  /** RFC 9457 problem detail. */
  public record Problem(String type, String title, int status, @Nullable String detail) {}
}
