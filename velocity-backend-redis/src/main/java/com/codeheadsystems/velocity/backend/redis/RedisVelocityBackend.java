// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.redis;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.DistinctStore;
import com.codeheadsystems.velocity.spi.SlidingSupport;
import com.codeheadsystems.velocity.spi.SumStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities.WindowSpec;
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
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowBounds;
import com.codeheadsystems.velocity.spi.model.WindowType;
import io.lettuce.core.Range;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanIterator;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The Redis/Lettuce v1 SLIDING hot-path reference {@link
 * com.codeheadsystems.velocity.spi.VelocityBackend}.
 *
 * <p>Implements the aggregation mix-ins {@link CountStore}, {@link SumStore} and {@link
 * DistinctStore} over {@link SlidingSupport true sliding} windows. Values are exact and produced
 * under {@link ReadYourWriteLevel#ATOMIC} read-your-write: each apply is a single atomic Lua script
 * (Redis is single-threaded) that appends the event, evicts aged members, and returns the
 * post-write windowed value, so concurrent applies never lose an increment (NFR-6, acceptance #2).
 *
 * <p><strong>Backend owns the clock (ADR 0003, FR-3).</strong> A sliding window of duration {@code
 * D} covers {@code (now - D, now]} — leading edge exclusive — where {@code now} is read from the
 * injected {@link Clock}, never Redis {@code TIME}, so a controllable test clock drives window
 * edges. This matches {@code velocity-testkit}'s {@code InMemoryVelocityBackend}.
 *
 * <p><strong>Storage.</strong> One sorted set per {@code (namespace, subject, aggregation, window)}
 * keyed {@code v:{ns}:{subjectType}:{subjectValue}:{aggType}:{dimension}:S:{durationMs}} (dimension
 * empty for COUNT/SUM). COUNT/SUM append a unique member (a per-key {@code INCR} sequence) scored
 * by {@code now}; DISTINCT uses the opaque member token itself as the ZSET member (score = last
 * seen), so a re-applied member de-dupes. The windowed value is the members with score in {@code
 * (now-D, now]}; aged members are evicted as cost-cleanup (correctness comes from the score range,
 * not eviction timing — FR-22a).
 *
 * <p>{@link com.codeheadsystems.velocity.spi.SeedSupport} is intentionally NOT implemented in v1:
 * ADR 0008's per-bucket seed model is a tumbling concept, and the mix-in is optional.
 */
public final class RedisVelocityBackend
    implements CountStore, SumStore, DistinctStore, SlidingSupport {

  /** COUNT apply: unique-member append, evict {@code <= now-D}, return the window's cardinality. */
  private static final String COUNT_APPLY =
      """
      local seq = redis.call('INCR', KEYS[2])
      redis.call('ZADD', KEYS[1], ARGV[1], seq)
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
      return redis.call('ZCARD', KEYS[1])""";

  /** SUM apply: append {@code seq:cents} scored by now, evict, return the in-window members. */
  private static final String SUM_APPLY =
      """
      local seq = redis.call('INCR', KEYS[2])
      redis.call('ZADD', KEYS[1], ARGV[1], seq..':'..ARGV[3])
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
      return redis.call('ZRANGEBYSCORE', KEYS[1], '('..ARGV[2], ARGV[1])""";

  /** DISTINCT apply: upsert the member at score now (de-dupe), evict, return the cardinality. */
  private static final String DISTINCT_APPLY =
      """
      redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
      return redis.call('ZCARD', KEYS[1])""";

  /** The sliding windows this backend supports, all EXACT. First entry is the smallest. */
  private static final List<Window> SUPPORTED_WINDOWS =
      List.of(
          new Window(Duration.ofSeconds(5), WindowType.SLIDING),
          new Window(Duration.ofSeconds(30), WindowType.SLIDING),
          new Window(Duration.ofMinutes(1), WindowType.SLIDING),
          new Window(Duration.ofMinutes(30), WindowType.SLIDING));

  private final RedisCommands<String, String> redis;
  private final Clock clock;
  private final BackendCapabilities capabilities;

  /**
   * Creates a backend over a Lettuce connection with the system UTC clock.
   *
   * @param connection a Lettuce connection to Redis
   */
  public RedisVelocityBackend(final StatefulRedisConnection<String, String> connection) {
    this(connection, Clock.systemUTC());
  }

  /**
   * Creates a backend over a Lettuce connection with an injectable clock — the authority for every
   * window edge (FR-3). Inject a controllable clock in tests.
   *
   * @param connection a Lettuce connection to Redis
   * @param clock the backend clock
   */
  public RedisVelocityBackend(
      final StatefulRedisConnection<String, String> connection, final Clock clock) {
    this.redis = Objects.requireNonNull(connection, "connection").sync();
    this.clock = Objects.requireNonNull(clock, "clock");
    this.capabilities = buildCapabilities();
  }

  private static BackendCapabilities buildCapabilities() {
    final List<WindowSpec> specs = new ArrayList<>();
    for (final Window window : SUPPORTED_WINDOWS) {
      specs.add(new WindowSpec(window, Exactness.EXACT, window.duration()));
    }
    return new BackendCapabilities(
        Set.of(AggregationType.COUNT, AggregationType.SUM, AggregationType.DISTINCT),
        specs,
        /* distinctHllSliding= */ false,
        // Exact-only distinct (ADR 0005, sliding): the ZSET holds every member, so the clamp and
        // HLL
        // threshold are effectively unbounded — this backend never sketches.
        /* distinctExactCardinalityClamp= */ Long.MAX_VALUE,
        /* distinctHllThresholdDefault= */ Long.MAX_VALUE,
        /* maxRetention= */ Duration.ofMinutes(30),
        ReadYourWriteLevel.ATOMIC,
        /* idempotencySupported= */ false,
        /* seedSupported= */ false,
        /* maxAtomicFanOut= */ Integer.MAX_VALUE);
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

  // ---- COUNT ---------------------------------------------------------------------------------

  @Override
  public ApplyResult applyCount(final ApplyContext ctx, final List<CountIntent> intents) {
    return apply(
        ctx,
        intents,
        (zkey, nowMs, lowerMs, intent) -> {
          final Long count =
              redis.eval(
                  COUNT_APPLY,
                  ScriptOutputType.INTEGER,
                  new String[] {zkey, zkey + ":seq"},
                  Long.toString(nowMs),
                  Long.toString(lowerMs));
          return BigDecimal.valueOf(count);
        });
  }

  @Override
  public List<FeatureResult> queryCount(final QueryContext ctx, final List<QueryTuple> tuples) {
    return query(ctx, tuples, this::cardinality);
  }

  // ---- SUM -----------------------------------------------------------------------------------

  @Override
  public ApplyResult applySum(final ApplyContext ctx, final List<SumIntent> intents) {
    return apply(
        ctx,
        intents,
        (zkey, nowMs, lowerMs, intent) -> {
          final List<String> members =
              redis.eval(
                  SUM_APPLY,
                  ScriptOutputType.MULTI,
                  new String[] {zkey, zkey + ":seq"},
                  Long.toString(nowMs),
                  Long.toString(lowerMs),
                  ((SumIntent) intent).valueCents().toPlainString());
          return sum(members);
        });
  }

  @Override
  public List<FeatureResult> querySum(final QueryContext ctx, final List<QueryTuple> tuples) {
    return query(ctx, tuples, (zkey, nowMs, lowerMs) -> sum(rangeMembers(zkey, nowMs, lowerMs)));
  }

  // ---- DISTINCT ------------------------------------------------------------------------------

  @Override
  public ApplyResult applyDistinct(final ApplyContext ctx, final List<DistinctIntent> intents) {
    return apply(
        ctx,
        intents,
        (zkey, nowMs, lowerMs, intent) -> {
          final String member =
              Base64.getEncoder().encodeToString(((DistinctIntent) intent).member().token());
          final Long cardinality =
              redis.eval(
                  DISTINCT_APPLY,
                  ScriptOutputType.INTEGER,
                  new String[] {zkey},
                  Long.toString(nowMs),
                  member,
                  Long.toString(lowerMs));
          return BigDecimal.valueOf(cardinality);
        });
  }

  @Override
  public List<FeatureResult> queryDistinct(final QueryContext ctx, final List<QueryTuple> tuples) {
    return query(ctx, tuples, this::cardinality);
  }

  // ---- PURGE ---------------------------------------------------------------------------------

  @Override
  public void purge(final Namespace namespace, final @Nullable Subject subject) {
    Objects.requireNonNull(namespace, "namespace");
    final String pattern =
        subject == null
            ? "v:" + namespace.value() + ":*"
            : "v:" + namespace.value() + ":" + subject.type() + ":" + subject.value() + ":*";
    final List<String> keys = new ArrayList<>();
    final ScanIterator<String> it = ScanIterator.scan(redis, ScanArgs.Builder.matches(pattern));
    while (it.hasNext()) {
      keys.add(it.next());
    }
    if (!keys.isEmpty()) {
      redis.del(keys.toArray(new String[0]));
    }
  }

  // ---- shared apply / query ------------------------------------------------------------------

  private ApplyResult apply(
      final ApplyContext ctx, final List<? extends Intent> intents, final Writer writer) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(intents, "intents");
    final Namespace namespace = ctx.namespace();
    final long nowMs = clock.instant().toEpochMilli();
    final List<PerFeature> outcomes = new ArrayList<>();
    for (final Intent intent : intents) {
      final Feature feature = intent.feature();
      for (final Window window : feature.windows()) {
        if (!capabilities.supportsWindow(window)) {
          outcomes.add(
              new PerFeature(
                  feature,
                  ApplyStatus.FAILED,
                  FeatureResult.failure(FailureCode.UNSUPPORTED_WINDOW, unsupported(window))));
          continue;
        }
        final long lowerMs = nowMs - window.duration().toMillis();
        final String zkey = key(namespace, intent.subject(), feature.aggregation(), window);
        final BigDecimal value = writer.write(zkey, nowMs, lowerMs, intent);
        outcomes.add(
            new PerFeature(
                feature,
                ApplyStatus.APPLIED,
                FeatureResult.success(featureValue(feature, window, value, nowMs, lowerMs))));
      }
    }
    return new ApplyResult(outcomes);
  }

  private List<FeatureResult> query(
      final QueryContext ctx, final List<QueryTuple> tuples, final Reader reader) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(tuples, "tuples");
    final Namespace namespace = ctx.namespace();
    final long nowMs = clock.instant().toEpochMilli();
    final List<FeatureResult> results = new ArrayList<>();
    for (final QueryTuple tuple : tuples) {
      final Window window = tuple.window();
      if (!capabilities.supportsWindow(window)) {
        results.add(FeatureResult.failure(FailureCode.UNSUPPORTED_WINDOW, unsupported(window)));
        continue;
      }
      final long lowerMs = nowMs - window.duration().toMillis();
      final String zkey = key(namespace, tuple.subject(), tuple.aggregation(), window);
      final BigDecimal value = reader.read(zkey, nowMs, lowerMs);
      results.add(
          FeatureResult.success(
              featureValue(syntheticFeature(tuple), window, value, nowMs, lowerMs)));
    }
    return results;
  }

  // ---- redis helpers -------------------------------------------------------------------------

  /** Cardinality of members with score in {@code (lowerMs, nowMs]}. */
  private BigDecimal cardinality(final String zkey, final long nowMs, final long lowerMs) {
    final Long count = redis.zcount(zkey, windowRange(nowMs, lowerMs));
    return BigDecimal.valueOf(count == null ? 0L : count);
  }

  /** The {@code seq:cents} members with score in {@code (lowerMs, nowMs]}. */
  private List<String> rangeMembers(final String zkey, final long nowMs, final long lowerMs) {
    return redis.zrangebyscore(zkey, windowRange(nowMs, lowerMs));
  }

  private static Range<Long> windowRange(final long nowMs, final long lowerMs) {
    // (lowerMs, nowMs] — leading edge exclusive (a sliding window is (now-D, now]).
    return Range.from(Range.Boundary.excluding(lowerMs), Range.Boundary.including(nowMs));
  }

  private static BigDecimal sum(final List<String> members) {
    BigDecimal total = BigDecimal.ZERO;
    for (final String member : members) {
      final int idx = member.indexOf(':');
      total = total.add(new BigDecimal(member.substring(idx + 1)));
    }
    return total;
  }

  // ---- key / value helpers -------------------------------------------------------------------

  private static String key(
      final Namespace namespace,
      final Subject subject,
      final Aggregation aggregation,
      final Window window) {
    final String dimension = aggregation.dimension() == null ? "" : aggregation.dimension();
    return "v:"
        + namespace.value()
        + ':'
        + subject.type()
        + ':'
        + subject.value()
        + ':'
        + aggregation.type().name()
        + ':'
        + dimension
        + ":S:"
        + window.duration().toMillis();
  }

  private static FeatureValue featureValue(
      final Feature feature,
      final Window window,
      final BigDecimal value,
      final long nowMs,
      final long lowerMs) {
    return new FeatureValue(
        feature,
        window,
        value,
        Exactness.EXACT,
        ReadYourWriteLevel.ATOMIC,
        /* definitionVersionHash= */ null,
        new WindowBounds(Instant.ofEpochMilli(lowerMs), Instant.ofEpochMilli(nowMs)),
        Instant.ofEpochMilli(nowMs));
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
        + " not supported by velocity-backend-redis";
  }

  /** Writes one intent into its sorted set and returns the post-write windowed value. */
  @FunctionalInterface
  private interface Writer {
    BigDecimal write(String zkey, long nowMs, long lowerMs, Intent intent);
  }

  /** Reads the current windowed value of a sorted set (0 when empty). */
  @FunctionalInterface
  private interface Reader {
    BigDecimal read(String zkey, long nowMs, long lowerMs);
  }
}
