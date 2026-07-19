// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import com.codeheadsystems.velocity.testkit.InMemoryVelocityBackend;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Maps real {@code velocity-spi} results (from the in-memory backend) to the wire DTOs. */
final class WireMapperTest {

  private static final Namespace NS = new Namespace("acme");
  private static final Subject SUBJECT = new Subject("card", "1");
  private static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);

  private final InMemoryVelocityBackend backend = new InMemoryVelocityBackend(MutableClock.atNow());

  @Test
  void mapsRecordResponseSuccessWithStringValue() {
    final Feature feature = new Feature("card.count", Aggregation.count(), List.of(SLIDING_1M));
    final ApplyResult result =
        backend.applyCount(new ApplyContext(NS, null), List.of(new CountIntent(feature, SUBJECT)));

    final Wire.RecordResponse response = WireMapper.toRecordResponse(result);
    assertThat(response.perFeature()).hasSize(1);
    final Wire.PerFeatureDto perFeature = response.perFeature().get(0);
    assertThat(perFeature.feature()).isEqualTo("card.count");
    assertThat(perFeature.status()).isEqualTo("APPLIED");
    assertThat(perFeature.result().kind()).isEqualTo("SUCCESS");
    assertThat(perFeature.result().value()).isNotNull();
    assertThat(perFeature.result().value().value()).isEqualTo("1");
    assertThat(perFeature.result().value().exactness()).isEqualTo("EXACT");
    assertThat(perFeature.result().value().readYourWriteLevel()).isEqualTo("ATOMIC");
    assertThat(perFeature.result().value().window().type()).isEqualTo("SLIDING");
  }

  @Test
  void mapsFailureResultForUnsupportedWindow() {
    final Window unsupported = new Window(Duration.ofDays(7), WindowType.SLIDING);
    final List<FeatureResult> results =
        backend.queryCount(
            new QueryContext(NS, null),
            List.of(new QueryTuple(SUBJECT, Aggregation.count(), unsupported)));

    final Wire.ResultDto dto = WireMapper.toResult(results.get(0));
    assertThat(dto.kind()).isEqualTo("FAILURE");
    assertThat(dto.code()).isEqualTo("UNSUPPORTED_WINDOW");
    assertThat(dto.value()).isNull();
  }

  @Test
  void mapsCapabilities() {
    final Wire.CapabilitiesResponse caps = WireMapper.toCapabilities(backend.capabilities());
    assertThat(caps.aggregations()).contains("COUNT", "SUM", "DISTINCT");
    assertThat(caps.windows()).isNotEmpty();
    assertThat(caps.readYourWriteLevel()).isEqualTo("ATOMIC");
    assertThat(caps.seedSupported()).isTrue();
    assertThat(caps.distinctHllSliding()).isFalse();
  }
}
