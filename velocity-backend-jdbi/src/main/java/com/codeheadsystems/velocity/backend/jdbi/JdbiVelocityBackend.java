// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.DistinctStore;
import com.codeheadsystems.velocity.spi.SeedSupport;
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
import com.codeheadsystems.velocity.spi.model.SeedAggregate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Postgres/JDBI v1 tumbling reference {@link com.codeheadsystems.velocity.spi.VelocityBackend}.
 *
 * <p>Implements every aggregation mix-in — {@link CountStore}, {@link SumStore}, {@link
 * DistinctStore} — over {@link TumblingSupport tumbling} windows, plus {@link SeedSupport}. Values
 * are exact and produced under {@link ReadYourWriteLevel#ATOMIC} read-your-write: each apply is one
 * transaction whose per-bucket upsert ({@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING})
 * returns the post-write value atomically, so concurrent applies to the same bucket never lose an
 * increment (NFR-6, acceptance #1).
 *
 * <p><strong>Backend owns bucketing and the clock (ADR 0003, FR-3).</strong> A tumbling window of
 * duration {@code D} resolves to the current aligned bucket {@code [floor(nowMs/Dms)·Dms, +Dms)},
 * matching {@code velocity-testkit}'s {@code WindowMath} so a JDBI-backed value equals the
 * reference in-memory value. The clock is injectable; the default is {@link Clock#systemUTC()} and
 * tests inject a controllable clock.
 *
 * <p><strong>Storage identity mirrors the reference backend.</strong> A bucket is keyed by {@code
 * (namespace, subject, aggregation, window, bucket_start)} — NOT by feature name: a {@link
 * QueryTuple} carries no feature, so a read resolves to the same bucket a write of the same
 * aggregation touched (exactly as the in-memory reference keys by {@code (namespace, subject,
 * aggregation)}). The aggregation's DISTINCT {@code dimension} is part of the key; COUNT/SUM use an
 * empty dimension.
 *
 * <p><strong>Schema.</strong> Three per-aggregation tables, keyed by {@code (namespace,
 * subject_type, subject_value, dimension, window_type, window_duration_ms, bucket_start_ms)}:
 * {@code velocity_counts} (+ {@code count}), {@code velocity_sums} (+ {@code sum_cents
 * NUMERIC(38,0)}), and {@code velocity_distinct_members} (+ opaque {@code member BYTEA}, the key
 * extended by {@code member} so a re-applied member de-dupes and cardinality is a {@code
 * COUNT(*)}). Call {@link #migrate()} once (idempotent {@code CREATE ... IF NOT EXISTS}) before
 * use.
 */
public final class JdbiVelocityBackend
    implements CountStore, SumStore, DistinctStore, TumblingSupport, SeedSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(JdbiVelocityBackend.class);

  private static final String KEY_COLUMNS =
      "namespace, subject_type, subject_value, dimension, window_type, window_duration_ms,"
          + " bucket_start_ms";
  private static final String KEY_CONFLICT =
      "(namespace, subject_type, subject_value, dimension, window_type, window_duration_ms,"
          + " bucket_start_ms)";
  private static final String KEY_PREDICATE =
      "namespace = :ns AND subject_type = :st AND subject_value = :sv AND dimension = :dim AND"
          + " window_type = :wt AND window_duration_ms = :wd AND bucket_start_ms = :bs";
  private static final String KEY_VALUES = ":ns, :st, :sv, :dim, :wt, :wd, :bs";

  /**
   * The idempotent schema DDL. Primary keys double as the upsert conflict target and the read
   * lookup index; the distinct table's key is extended by {@code member} so re-applying a member is
   * a no-op and {@code COUNT(*)} over the bucket-key prefix (served by the {@code _bucket} index)
   * is the exact cardinality.
   */
  private static final List<String> SCHEMA_DDL =
      List.of(
          "CREATE TABLE IF NOT EXISTS velocity_counts ("
              + " namespace TEXT NOT NULL,"
              + " subject_type TEXT NOT NULL,"
              + " subject_value TEXT NOT NULL,"
              + " dimension TEXT NOT NULL,"
              + " window_type TEXT NOT NULL,"
              + " window_duration_ms BIGINT NOT NULL,"
              + " bucket_start_ms BIGINT NOT NULL,"
              + " count BIGINT NOT NULL,"
              + " seeded BOOLEAN NOT NULL DEFAULT FALSE,"
              + " PRIMARY KEY "
              + KEY_CONFLICT
              + ")",
          "CREATE TABLE IF NOT EXISTS velocity_sums ("
              + " namespace TEXT NOT NULL,"
              + " subject_type TEXT NOT NULL,"
              + " subject_value TEXT NOT NULL,"
              + " dimension TEXT NOT NULL,"
              + " window_type TEXT NOT NULL,"
              + " window_duration_ms BIGINT NOT NULL,"
              + " bucket_start_ms BIGINT NOT NULL,"
              + " sum_cents NUMERIC(38,0) NOT NULL,"
              + " seeded BOOLEAN NOT NULL DEFAULT FALSE,"
              + " PRIMARY KEY "
              + KEY_CONFLICT
              + ")",
          "CREATE TABLE IF NOT EXISTS velocity_distinct_members ("
              + " namespace TEXT NOT NULL,"
              + " subject_type TEXT NOT NULL,"
              + " subject_value TEXT NOT NULL,"
              + " dimension TEXT NOT NULL,"
              + " window_type TEXT NOT NULL,"
              + " window_duration_ms BIGINT NOT NULL,"
              + " bucket_start_ms BIGINT NOT NULL,"
              + " member BYTEA NOT NULL,"
              + " seeded BOOLEAN NOT NULL DEFAULT FALSE,"
              + " PRIMARY KEY (namespace, subject_type, subject_value, dimension, window_type,"
              + " window_duration_ms, bucket_start_ms, member))",
          "CREATE INDEX IF NOT EXISTS velocity_distinct_members_bucket"
              + " ON velocity_distinct_members (namespace, subject_type, subject_value, dimension,"
              + " window_type, window_duration_ms, bucket_start_ms)");

  /** The tumbling windows this backend supports, all EXACT. First entry is the smallest. */
  private static final List<Window> SUPPORTED_WINDOWS =
      List.of(
          new Window(Duration.ofMinutes(1), WindowType.TUMBLING),
          new Window(Duration.ofHours(1), WindowType.TUMBLING),
          new Window(Duration.ofDays(1), WindowType.TUMBLING));

  private final Jdbi jdbi;
  private final Clock clock;
  private final BackendCapabilities capabilities;

  /**
   * Creates a backend over a {@link DataSource} (e.g. a HikariCP pool) with the system UTC clock.
   *
   * @param dataSource the pooled Postgres data source
   */
  public JdbiVelocityBackend(final DataSource dataSource) {
    this(Jdbi.create(Objects.requireNonNull(dataSource, "dataSource")), Clock.systemUTC());
  }

  /**
   * Creates a backend over a {@link Jdbi} handle-source with an injectable clock — the authority
   * for every window edge (FR-3). Inject a controllable clock in tests.
   *
   * @param jdbi the JDBI instance bound to a pooled Postgres data source
   * @param clock the backend clock
   */
  public JdbiVelocityBackend(final Jdbi jdbi, final Clock clock) {
    this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.capabilities = buildCapabilities();
  }

  private static BackendCapabilities buildCapabilities() {
    final List<WindowSpec> specs = new ArrayList<>();
    for (final Window window : SUPPORTED_WINDOWS) {
      // Tumbling stores at bucket granularity == the window duration (current-bucket-only value).
      specs.add(new WindowSpec(window, Exactness.EXACT, window.duration()));
    }
    return new BackendCapabilities(
        Set.of(AggregationType.COUNT, AggregationType.SUM, AggregationType.DISTINCT),
        specs,
        /* distinctHllSliding= */ false,
        // Exact-only distinct: HLL degradation (ADR 0006) is a documented follow-up, so the exact
        // clamp and the HLL threshold are effectively unbounded — this backend never sketches.
        /* distinctExactCardinalityClamp= */ Long.MAX_VALUE,
        /* distinctHllThresholdDefault= */ Long.MAX_VALUE,
        /* maxRetention= */ Duration.ofDays(90),
        ReadYourWriteLevel.ATOMIC,
        /* idempotencySupported= */ false,
        /* seedSupported= */ true,
        /* maxAtomicFanOut= */ Integer.MAX_VALUE);
  }

  /**
   * Creates the schema idempotently ({@code CREATE TABLE/INDEX IF NOT EXISTS}). Safe to call
   * repeatedly and concurrently; call once before the first apply/query.
   *
   * @return this backend, for chaining
   */
  public JdbiVelocityBackend migrate() {
    jdbi.useTransaction(
        handle -> {
          for (final String ddl : SCHEMA_DDL) {
            handle.execute(ddl);
          }
        });
    LOGGER.info("velocity-backend-jdbi schema migrated ({} statements)", SCHEMA_DDL.size());
    return this;
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
        (handle, key, intent) ->
            BigDecimal.valueOf(
                handle
                    .createQuery(
                        "INSERT INTO velocity_counts ("
                            + KEY_COLUMNS
                            + ", count) VALUES ("
                            + KEY_VALUES
                            + ", 1) ON CONFLICT "
                            + KEY_CONFLICT
                            + " DO UPDATE SET count = velocity_counts.count + 1 RETURNING count")
                    .bindMap(key.binding())
                    .mapTo(Long.class)
                    .one()));
  }

  @Override
  public List<FeatureResult> queryCount(final QueryContext ctx, final List<QueryTuple> tuples) {
    return query(
        ctx,
        tuples,
        (handle, key) ->
            handle
                .createQuery("SELECT count FROM velocity_counts WHERE " + KEY_PREDICATE)
                .bindMap(key.binding())
                .mapTo(Long.class)
                .findOne()
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO));
  }

  // ---- SUM -----------------------------------------------------------------------------------

  @Override
  public ApplyResult applySum(final ApplyContext ctx, final List<SumIntent> intents) {
    return apply(
        ctx,
        intents,
        (handle, key, intent) ->
            handle
                .createQuery(
                    "INSERT INTO velocity_sums ("
                        + KEY_COLUMNS
                        + ", sum_cents) VALUES ("
                        + KEY_VALUES
                        + ", :val) ON CONFLICT "
                        + KEY_CONFLICT
                        + " DO UPDATE SET sum_cents = velocity_sums.sum_cents + :val"
                        + " RETURNING sum_cents")
                .bindMap(key.binding())
                .bind("val", ((SumIntent) intent).valueCents())
                .mapTo(BigDecimal.class)
                .one());
  }

  @Override
  public List<FeatureResult> querySum(final QueryContext ctx, final List<QueryTuple> tuples) {
    return query(
        ctx,
        tuples,
        (handle, key) ->
            handle
                .createQuery("SELECT sum_cents FROM velocity_sums WHERE " + KEY_PREDICATE)
                .bindMap(key.binding())
                .mapTo(BigDecimal.class)
                .findOne()
                .orElse(BigDecimal.ZERO));
  }

  // ---- DISTINCT ------------------------------------------------------------------------------

  @Override
  public ApplyResult applyDistinct(final ApplyContext ctx, final List<DistinctIntent> intents) {
    return apply(
        ctx,
        intents,
        (handle, key, intent) -> {
          handle
              .createUpdate(
                  "INSERT INTO velocity_distinct_members ("
                      + KEY_COLUMNS
                      + ", member) VALUES ("
                      + KEY_VALUES
                      + ", :member) ON CONFLICT DO NOTHING")
              .bindMap(key.binding())
              .bind("member", ((DistinctIntent) intent).member().token())
              .execute();
          return cardinality(handle, key);
        });
  }

  @Override
  public List<FeatureResult> queryDistinct(final QueryContext ctx, final List<QueryTuple> tuples) {
    return query(ctx, tuples, JdbiVelocityBackend::cardinality);
  }

  private static BigDecimal cardinality(final Handle handle, final BucketKey key) {
    return BigDecimal.valueOf(
        handle
            .createQuery("SELECT count(*) FROM velocity_distinct_members WHERE " + KEY_PREDICATE)
            .bindMap(key.binding())
            .mapTo(Long.class)
            .one());
  }

  // ---- shared apply / query ------------------------------------------------------------------

  private ApplyResult apply(
      final ApplyContext ctx, final List<? extends Intent> intents, final BucketWriter writer) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(intents, "intents");
    final Namespace namespace = ctx.namespace();
    final Instant now = clock.instant();
    return new ApplyResult(
        jdbi.inTransaction(
            handle -> {
              final List<PerFeature> outcomes = new ArrayList<>();
              for (final Intent intent : intents) {
                final Feature feature = intent.feature();
                for (final Window window : feature.windows()) {
                  if (!capabilities.supportsWindow(window)) {
                    outcomes.add(
                        new PerFeature(
                            feature,
                            ApplyStatus.FAILED,
                            FeatureResult.failure(
                                FailureCode.UNSUPPORTED_WINDOW, unsupported(window))));
                    continue;
                  }
                  final BucketKey key =
                      keyFor(namespace, intent.subject(), feature.aggregation(), window, now);
                  final BigDecimal value = writer.write(handle, key, intent);
                  outcomes.add(
                      new PerFeature(
                          feature,
                          ApplyStatus.APPLIED,
                          FeatureResult.success(featureValue(feature, window, value, key, now))));
                }
              }
              return outcomes;
            }));
  }

  private List<FeatureResult> query(
      final QueryContext ctx, final List<QueryTuple> tuples, final BucketReader reader) {
    Objects.requireNonNull(ctx, "ctx");
    Objects.requireNonNull(tuples, "tuples");
    final Namespace namespace = ctx.namespace();
    final Instant now = clock.instant();
    return jdbi.withHandle(
        handle -> {
          final List<FeatureResult> results = new ArrayList<>();
          for (final QueryTuple tuple : tuples) {
            final Window window = tuple.window();
            if (!capabilities.supportsWindow(window)) {
              results.add(
                  FeatureResult.failure(FailureCode.UNSUPPORTED_WINDOW, unsupported(window)));
              continue;
            }
            final BucketKey key =
                keyFor(namespace, tuple.subject(), tuple.aggregation(), window, now);
            final BigDecimal value = reader.read(handle, key);
            results.add(
                FeatureResult.success(
                    featureValue(syntheticFeature(tuple), window, value, key, now)));
          }
          return results;
        });
  }

  // ---- SEED ----------------------------------------------------------------------------------

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
    jdbi.useTransaction(
        handle -> {
          for (final BucketValue bucket : buckets) {
            final Duration span = Duration.between(bucket.bounds().start(), bucket.bounds().end());
            final Window window = matchingSupportedWindow(feature, span);
            if (window == null) {
              throw new IllegalArgumentException(
                  "seed bucket of "
                      + span
                      + " matches no supported window of feature '"
                      + feature.name()
                      + "'");
            }
            final BucketKey key =
                keyAt(
                    namespace,
                    subject,
                    feature.aggregation(),
                    window,
                    bucket.bounds().start(),
                    span.toMillis());
            seedBucket(handle, key, bucket.aggregate());
          }
        });
  }

  private static void seedBucket(
      final Handle handle, final BucketKey key, final SeedAggregate aggregate) {
    switch (aggregate) {
      case CountValue count ->
          handle
              .createUpdate(
                  "INSERT INTO velocity_counts ("
                      + KEY_COLUMNS
                      + ", count, seeded) VALUES ("
                      + KEY_VALUES
                      + ", :count, TRUE) ON CONFLICT "
                      + KEY_CONFLICT
                      + " DO UPDATE SET count = velocity_counts.count + :count, seeded = TRUE")
              .bindMap(key.binding())
              .bind("count", count.count())
              .execute();
      case SumValue sum ->
          handle
              .createUpdate(
                  "INSERT INTO velocity_sums ("
                      + KEY_COLUMNS
                      + ", sum_cents, seeded) VALUES ("
                      + KEY_VALUES
                      + ", :val, TRUE) ON CONFLICT "
                      + KEY_CONFLICT
                      + " DO UPDATE SET sum_cents = velocity_sums.sum_cents + :val, seeded = TRUE")
              .bindMap(key.binding())
              .bind("val", sum.cents())
              .execute();
      case ExactDistinct exact -> {
        for (final DistinctMember member : exact.members()) {
          handle
              .createUpdate(
                  "INSERT INTO velocity_distinct_members ("
                      + KEY_COLUMNS
                      + ", member, seeded) VALUES ("
                      + KEY_VALUES
                      + ", :member, TRUE) ON CONFLICT DO NOTHING")
              .bindMap(key.binding())
              .bind("member", member.token())
              .execute();
        }
      }
      case HllDistinct hll ->
          throw new IllegalArgumentException(
              "velocity-backend-jdbi is exact-only distinct; rejecting HLL seed "
                  + hll
                  + " (ADR 0005/0006)");
    }
  }

  private @Nullable Window matchingSupportedWindow(final Feature feature, final Duration span) {
    for (final Window window : feature.windows()) {
      if (window.duration().equals(span) && capabilities.supportsWindow(window)) {
        return window;
      }
    }
    return null;
  }

  // ---- PURGE ---------------------------------------------------------------------------------

  @Override
  public void purge(final Namespace namespace, final @Nullable Subject subject) {
    Objects.requireNonNull(namespace, "namespace");
    jdbi.useTransaction(
        handle -> {
          for (final String table :
              List.of("velocity_counts", "velocity_sums", "velocity_distinct_members")) {
            if (subject == null) {
              handle
                  .createUpdate("DELETE FROM " + table + " WHERE namespace = :ns")
                  .bind("ns", namespace.value())
                  .execute();
            } else {
              handle
                  .createUpdate(
                      "DELETE FROM "
                          + table
                          + " WHERE namespace = :ns AND subject_type = :st AND subject_value = :sv")
                  .bind("ns", namespace.value())
                  .bind("st", subject.type())
                  .bind("sv", subject.value())
                  .execute();
            }
          }
        });
  }

  // ---- helpers -------------------------------------------------------------------------------

  private BucketKey keyFor(
      final Namespace namespace,
      final Subject subject,
      final Aggregation aggregation,
      final Window window,
      final Instant now) {
    final long durationMs = window.duration().toMillis();
    final long bucketStartMs = Math.floorDiv(now.toEpochMilli(), durationMs) * durationMs;
    return keyAt(
        namespace, subject, aggregation, window, Instant.ofEpochMilli(bucketStartMs), durationMs);
  }

  private static BucketKey keyAt(
      final Namespace namespace,
      final Subject subject,
      final Aggregation aggregation,
      final Window window,
      final Instant bucketStart,
      final long durationMs) {
    final long bucketStartMs = bucketStart.toEpochMilli();
    return new BucketKey(
        namespace.value(),
        subject.type(),
        subject.value(),
        dimensionOf(aggregation),
        window.type().name(),
        durationMs,
        bucketStartMs,
        new WindowBounds(
            Instant.ofEpochMilli(bucketStartMs), Instant.ofEpochMilli(bucketStartMs + durationMs)));
  }

  /**
   * COUNT/SUM have no dimension; DISTINCT carries the named dimension. Empty string keys the row.
   */
  private static String dimensionOf(final Aggregation aggregation) {
    final String dimension = aggregation.dimension();
    return dimension == null ? "" : dimension;
  }

  private static FeatureValue featureValue(
      final Feature feature,
      final Window window,
      final BigDecimal value,
      final BucketKey key,
      final Instant now) {
    return new FeatureValue(
        feature,
        window,
        value,
        Exactness.EXACT,
        ReadYourWriteLevel.ATOMIC,
        /* definitionVersionHash= */ null,
        key.bounds(),
        now);
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
        + " not supported by velocity-backend-jdbi";
  }

  /** Writes one intent into its bucket row and returns the post-write value (read-your-write). */
  @FunctionalInterface
  private interface BucketWriter {
    BigDecimal write(Handle handle, BucketKey key, Intent intent);
  }

  /** Reads the current value of a bucket (0/empty when absent). */
  @FunctionalInterface
  private interface BucketReader {
    BigDecimal read(Handle handle, BucketKey key);
  }

  /**
   * The fully-resolved storage key of a single tumbling bucket, plus the window bounds the value
   * covers. {@link #binding()} exposes the seven key columns as named JDBI parameters.
   */
  private record BucketKey(
      String namespace,
      String subjectType,
      String subjectValue,
      String dimension,
      String windowType,
      long windowDurationMs,
      long bucketStartMs,
      WindowBounds bounds) {

    Map<String, Object> binding() {
      final Map<String, Object> map = new HashMap<>();
      map.put("ns", namespace);
      map.put("st", subjectType);
      map.put("sv", subjectValue);
      map.put("dim", dimension);
      map.put("wt", windowType);
      map.put("wd", windowDurationMs);
      map.put("bs", bucketStartMs);
      return map;
    }
  }
}
