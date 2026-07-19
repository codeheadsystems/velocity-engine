// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.DistinctStore;
import com.codeheadsystems.velocity.spi.SeedSupport;
import com.codeheadsystems.velocity.spi.SlidingSupport;
import com.codeheadsystems.velocity.spi.SumStore;
import com.codeheadsystems.velocity.spi.TumblingSupport;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities.WindowSpec;
import com.codeheadsystems.velocity.spi.model.BucketValue;
import com.codeheadsystems.velocity.spi.model.DistinctMember;
import com.codeheadsystems.velocity.spi.model.Exactness;
import com.codeheadsystems.velocity.spi.model.FailureCode;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.FeatureValue;
import com.codeheadsystems.velocity.spi.model.Intent;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.Intent.SumIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel;
import com.codeheadsystems.velocity.spi.model.SeedAggregate.CountValue;
import com.codeheadsystems.velocity.spi.model.SeedAggregate.ExactDistinct;
import com.codeheadsystems.velocity.spi.model.SeedAggregate.HllDistinct;
import com.codeheadsystems.velocity.spi.model.SeedAggregate.SumValue;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

/**
 * The reference in-memory {@code velocity-spi} backend (ADR 0004, NFR-13/18).
 *
 * <p>Two jobs: it <strong>proves the frozen SPI contract is implementable</strong> as an exact,
 * atomic, thread-safe store, and it is the primary subject the conformance TCK ({@code
 * com.codeheadsystems.velocity.testkit.tck}) runs against. It implements every data-plane mix-in —
 * {@link CountStore}, {@link SumStore}, {@link DistinctStore} — over both window markers ({@link
 * SlidingSupport}, {@link TumblingSupport}) plus {@link SeedSupport}, and declares {@link
 * ReadYourWriteLevel#ATOMIC} because an apply's returned value already reflects that write.
 *
 * <p><strong>Model.</strong> Windowing lives in the backend (ADR 0003). The store keeps <em>raw
 * events</em> per {@code (namespace, subject, aggregation)} — a list of instants for COUNT, of
 * {@code (instant, cents)} for SUM, of {@code (instant, member)} for DISTINCT — and every window a
 * feature declares is computed from that one stored event stream <em>at read time</em> (so one
 * apply records a single underlying event, not one per window). This is exactly the property that
 * lets a seeded bucket and an organically recorded bucket merge identically (ADR 0008). Window
 * arithmetic is {@link WindowMath}. The backend is the clock authority (FR-3): inject a {@link
 * MutableClock} to drive window aging deterministically.
 *
 * <p><strong>Distinct is exact-only here.</strong> The clamp/threshold are set effectively
 * infinite, so this backend never degrades to HLL; it therefore rejects an {@link HllDistinct} seed
 * (ADR 0005/0006).
 */
public final class InMemoryVelocityBackend
    implements CountStore, SumStore, DistinctStore, SlidingSupport, TumblingSupport, SeedSupport {

  private final Clock clock;
  private final BackendCapabilities capabilities;
  private final ConcurrentMap<StoreKey, EventLog> store = new ConcurrentHashMap<>();

  /** Creates a backend whose clock is {@link Clock#systemUTC()}. */
  public InMemoryVelocityBackend() {
    this(Clock.systemUTC());
  }

  /**
   * Creates a backend driven by the given clock — the authority for all window edges (FR-3).
   *
   * @param clock the backend clock; inject a {@link MutableClock} to control time in tests
   */
  public InMemoryVelocityBackend(final Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.capabilities = buildCapabilities();
  }

  private static BackendCapabilities buildCapabilities() {
    final List<WindowSpec> windows =
        List.of(
            windowSpec(WindowType.SLIDING, Duration.ofSeconds(5)),
            windowSpec(WindowType.SLIDING, Duration.ofMinutes(1)),
            windowSpec(WindowType.TUMBLING, Duration.ofMinutes(1)),
            windowSpec(WindowType.TUMBLING, Duration.ofHours(1)));
    return new BackendCapabilities(
        Set.of(AggregationType.COUNT, AggregationType.SUM, AggregationType.DISTINCT),
        windows,
        /* distinctHllSliding= */ false,
        /* distinctExactCardinalityClamp= */ Long.MAX_VALUE,
        /* distinctHllThresholdDefault= */ Long.MAX_VALUE,
        /* maxRetention= */ Duration.ofDays(30),
        ReadYourWriteLevel.ATOMIC,
        /* idempotencySupported= */ false,
        /* seedSupported= */ true,
        /* maxAtomicFanOut= */ Integer.MAX_VALUE);
  }

  private static WindowSpec windowSpec(final WindowType type, final Duration duration) {
    // In-memory keeps raw events, so "granularity" is nominal; report it as the window duration.
    return new WindowSpec(new Window(duration, type), Exactness.EXACT, duration);
  }

  /**
   * The clock this backend reads time from; the authority for every window edge (FR-3).
   *
   * @return the injected clock
   */
  public Clock clock() {
    return clock;
  }

  @Override
  public BackendCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public ApplyResult applyCount(final ApplyContext ctx, final List<CountIntent> intents) {
    return applyIntents(ctx, intents);
  }

  @Override
  public ApplyResult applySum(final ApplyContext ctx, final List<SumIntent> intents) {
    return applyIntents(ctx, intents);
  }

  @Override
  public ApplyResult applyDistinct(final ApplyContext ctx, final List<DistinctIntent> intents) {
    return applyIntents(ctx, intents);
  }

  private ApplyResult applyIntents(final ApplyContext ctx, final List<? extends Intent> intents) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(intents, "intents");
    final Namespace namespace = ctx.namespace();
    final Instant now = clock.instant();
    final List<PerFeature> outcomes = new ArrayList<>();
    for (final Intent intent : intents) {
      final Feature feature = intent.feature();
      final StoreKey key = new StoreKey(namespace, intent.subject(), feature.aggregation());
      final EventLog log = store.computeIfAbsent(key, unused -> new EventLog());
      final Event event = toEvent(intent, now);
      synchronized (log) {
        final boolean anySupported =
            feature.windows().stream().anyMatch(capabilities::supportsWindow);
        if (anySupported) {
          log.add(event);
        }
        final AggregationType type = feature.aggregation().type();
        for (final Window window : feature.windows()) {
          if (capabilities.supportsWindow(window)) {
            final FeatureValue value = log.valueOf(feature, window, type, now);
            outcomes.add(
                new PerFeature(feature, ApplyStatus.APPLIED, FeatureResult.success(value)));
          } else {
            outcomes.add(
                new PerFeature(
                    feature,
                    ApplyStatus.FAILED,
                    FeatureResult.failure(FailureCode.UNSUPPORTED_WINDOW, unsupported(window))));
          }
        }
      }
    }
    return new ApplyResult(outcomes);
  }

  private static Event toEvent(final Intent intent, final Instant now) {
    return switch (intent) {
      case CountIntent ignored -> new Event(now, null, null);
      case SumIntent sum -> new Event(now, sum.valueCents(), null);
      case DistinctIntent distinct -> new Event(now, null, distinct.member());
    };
  }

  @Override
  public List<FeatureResult> queryCount(final QueryContext ctx, final List<QueryTuple> tuples) {
    return queryTuples(ctx, tuples);
  }

  @Override
  public List<FeatureResult> querySum(final QueryContext ctx, final List<QueryTuple> tuples) {
    return queryTuples(ctx, tuples);
  }

  @Override
  public List<FeatureResult> queryDistinct(final QueryContext ctx, final List<QueryTuple> tuples) {
    return queryTuples(ctx, tuples);
  }

  private List<FeatureResult> queryTuples(final QueryContext ctx, final List<QueryTuple> tuples) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(tuples, "tuples");
    final Namespace namespace = ctx.namespace();
    final Instant now = clock.instant();
    final List<FeatureResult> results = new ArrayList<>();
    for (final QueryTuple tuple : tuples) {
      final Window window = tuple.window();
      if (!capabilities.supportsWindow(window)) {
        results.add(FeatureResult.failure(FailureCode.UNSUPPORTED_WINDOW, unsupported(window)));
        continue;
      }
      final AggregationType type = tuple.aggregation().type();
      final StoreKey key = new StoreKey(namespace, tuple.subject(), tuple.aggregation());
      final EventLog log = store.get(key);
      final Feature synthetic = syntheticFeature(tuple);
      final BigDecimal value;
      if (log == null) {
        // A supported window with genuinely no data is a KNOWN zero — Success(0), never a failure.
        value = BigDecimal.ZERO;
      } else {
        synchronized (log) {
          value = log.compute(window, WindowMath.boundsAt(window, now), type);
        }
      }
      results.add(
          FeatureResult.success(
              new FeatureValue(
                  synthetic,
                  window,
                  value,
                  Exactness.EXACT,
                  ReadYourWriteLevel.ATOMIC,
                  /* definitionVersionHash= */ null,
                  WindowMath.boundsAt(window, now),
                  now)));
    }
    return results;
  }

  @Override
  public void purge(final Namespace namespace, final @Nullable Subject subject) {
    Objects.requireNonNull(namespace, "namespace");
    if (subject == null) {
      store.keySet().removeIf(key -> key.namespace().equals(namespace));
    } else {
      store
          .keySet()
          .removeIf(key -> key.namespace().equals(namespace) && key.subject().equals(subject));
    }
  }

  @Override
  public void seed(
      final Namespace namespace,
      final Subject subject,
      final Feature feature,
      final List<BucketValue> buckets) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(buckets, "buckets");
    final StoreKey key = new StoreKey(namespace, subject, feature.aggregation());
    for (final BucketValue bucket : buckets) {
      final Duration bucketDuration =
          Duration.between(bucket.bounds().start(), bucket.bounds().end());
      final Window matched = matchingSupportedWindow(feature, bucketDuration);
      if (matched == null) {
        throw new IllegalArgumentException(
            "seed bucket of "
                + bucketDuration
                + " matches no supported window of feature '"
                + feature.name()
                + "'");
      }
      final Instant at = midpoint(bucket.bounds());
      final EventLog log = store.computeIfAbsent(key, unused -> new EventLog());
      synchronized (log) {
        switch (bucket.aggregate()) {
          case CountValue count -> {
            for (long i = 0; i < count.count(); i++) {
              log.add(new Event(at, null, null));
            }
          }
          case SumValue sum -> log.add(new Event(at, sum.cents(), null));
          case ExactDistinct exact -> {
            for (final DistinctMember member : exact.members()) {
              log.add(new Event(at, null, member));
            }
          }
          case HllDistinct hll ->
              throw new IllegalArgumentException(
                  "in-memory backend is exact-only distinct; rejecting HLL seed "
                      + hll
                      + " (ADR 0005/0006)");
        }
      }
    }
  }

  private @Nullable Window matchingSupportedWindow(
      final Feature feature, final Duration bucketDuration) {
    for (final Window window : feature.windows()) {
      if (window.duration().equals(bucketDuration) && capabilities.supportsWindow(window)) {
        return window;
      }
    }
    return null;
  }

  private static Instant midpoint(final WindowBounds bounds) {
    return bounds.start().plus(Duration.between(bounds.start(), bounds.end()).dividedBy(2));
  }

  private static Feature syntheticFeature(final QueryTuple tuple) {
    final Aggregation aggregation = tuple.aggregation();
    final String name =
        "query:"
            + aggregation.type()
            + (aggregation.dimension() == null ? "" : ":" + aggregation.dimension());
    return new Feature(name, aggregation, List.of(tuple.window()));
  }

  private static String unsupported(final Window window) {
    return "window "
        + window.type()
        + " "
        + window.duration()
        + " not supported by in-memory backend";
  }

  /**
   * The join key both apply and query resolve to; the aggregation carries the DISTINCT dimension.
   */
  private record StoreKey(Namespace namespace, Subject subject, Aggregation aggregation) {}

  /** One raw event; {@code cents} is set only for SUM, {@code member} only for DISTINCT. */
  private record Event(
      Instant timestamp, @Nullable BigDecimal cents, @Nullable DistinctMember member) {}

  /**
   * The raw event stream for one {@link StoreKey}. Not thread-safe on its own; the backend
   * synchronizes on the instance so an apply's add-then-read is atomic (read-your-write, ADR 0007).
   */
  private static final class EventLog {

    private final List<Event> events = new ArrayList<>();

    void add(final Event event) {
      events.add(event);
    }

    FeatureValue valueOf(
        final Feature feature, final Window window, final AggregationType type, final Instant now) {
      final WindowBounds bounds = WindowMath.boundsAt(window, now);
      return new FeatureValue(
          feature,
          window,
          compute(window, bounds, type),
          Exactness.EXACT,
          ReadYourWriteLevel.ATOMIC,
          /* definitionVersionHash= */ null,
          bounds,
          now);
    }

    BigDecimal compute(final Window window, final WindowBounds bounds, final AggregationType type) {
      return switch (type) {
        case COUNT -> {
          long count = 0;
          for (final Event event : events) {
            if (WindowMath.contains(window, bounds, event.timestamp())) {
              count++;
            }
          }
          yield BigDecimal.valueOf(count);
        }
        case SUM -> {
          BigDecimal sum = BigDecimal.ZERO;
          for (final Event event : events) {
            final BigDecimal cents = event.cents();
            if (cents != null && WindowMath.contains(window, bounds, event.timestamp())) {
              sum = sum.add(cents);
            }
          }
          yield sum;
        }
        case DISTINCT -> {
          final Set<DistinctMember> distinct = new HashSet<>();
          for (final Event event : events) {
            final DistinctMember member = event.member();
            if (member != null && WindowMath.contains(window, bounds, event.timestamp())) {
              distinct.add(member);
            }
          }
          yield BigDecimal.valueOf(distinct.size());
        }
      };
    }
  }
}
