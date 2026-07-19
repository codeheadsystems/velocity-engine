// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.api;

import com.codeheadsystems.velocity.dropwizard.api.Wire.CapabilitiesResponse;
import com.codeheadsystems.velocity.dropwizard.api.Wire.FeatureValueDto;
import com.codeheadsystems.velocity.dropwizard.api.Wire.PerFeatureDto;
import com.codeheadsystems.velocity.dropwizard.api.Wire.RecordResponse;
import com.codeheadsystems.velocity.dropwizard.api.Wire.ResultDto;
import com.codeheadsystems.velocity.dropwizard.api.Wire.WindowBoundsDto;
import com.codeheadsystems.velocity.dropwizard.api.Wire.WindowDto;
import com.codeheadsystems.velocity.dropwizard.api.Wire.WindowSpecDto;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.FeatureValue;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import java.util.List;

/**
 * Maps {@code velocity-spi} result types to the HTTP wire DTOs (money/value as decimal strings).
 */
public final class WireMapper {

  private WireMapper() {}

  /** Maps an apply result to the record response. */
  public static RecordResponse toRecordResponse(final ApplyResult result) {
    return new RecordResponse(result.perFeature().stream().map(WireMapper::toPerFeature).toList());
  }

  private static PerFeatureDto toPerFeature(final PerFeature perFeature) {
    return new PerFeatureDto(
        perFeature.feature().name(), perFeature.status().name(), toResult(perFeature.result()));
  }

  /** Maps a single feature result (Success value or distinguishable Failure). */
  public static ResultDto toResult(final FeatureResult result) {
    return switch (result) {
      case FeatureResult.Success success ->
          new ResultDto("SUCCESS", toFeatureValue(success.value()), null, null);
      case FeatureResult.Failure failure ->
          new ResultDto("FAILURE", null, failure.code().name(), failure.detail());
    };
  }

  private static FeatureValueDto toFeatureValue(final FeatureValue value) {
    return new FeatureValueDto(
        value.feature().name(),
        toWindow(value.window()),
        value.value().toPlainString(),
        value.exactness().name(),
        value.readYourWriteLevel().name(),
        value.definitionVersionHash(),
        toBounds(value.windowBounds()),
        value.asOf().toString());
  }

  private static WindowDto toWindow(final Window window) {
    return new WindowDto(window.duration().toString(), window.type().name());
  }

  private static WindowBoundsDto toBounds(final WindowBounds bounds) {
    return new WindowBoundsDto(bounds.start().toString(), bounds.end().toString());
  }

  /** Maps backend capabilities to the capabilities response. */
  public static CapabilitiesResponse toCapabilities(final BackendCapabilities caps) {
    final List<WindowSpecDto> windows =
        caps.windows().stream()
            .map(
                spec ->
                    new WindowSpecDto(
                        toWindow(spec.window()),
                        spec.exactness().name(),
                        spec.granularity().toString()))
            .toList();
    return new CapabilitiesResponse(
        caps.aggregations().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
        windows,
        caps.distinctHllSliding(),
        caps.distinctExactCardinalityClamp(),
        caps.distinctHllThresholdDefault(),
        caps.maxRetention().toString(),
        caps.readYourWriteLevel().name(),
        caps.idempotencySupported(),
        caps.seedSupported(),
        caps.maxAtomicFanOut());
  }
}
