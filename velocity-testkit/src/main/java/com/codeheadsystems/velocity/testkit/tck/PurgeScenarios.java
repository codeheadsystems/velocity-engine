// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.NS_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_A;
import static com.codeheadsystems.velocity.testkit.tck.Tck.SUBJECT_B;
import static com.codeheadsystems.velocity.testkit.tck.Tck.assertValue;
import static com.codeheadsystems.velocity.testkit.tck.Tck.countFeature;

import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import java.util.List;
import java.util.Objects;

/**
 * The {@link com.codeheadsystems.velocity.spi.VelocityBackend#purge purge} contract (FR-23): {@code
 * purge(ns, subject)} erases only that subject within the namespace, and {@code purge(ns, null)}
 * erases the whole namespace — neither touches another namespace.
 */
public final class PurgeScenarios {

  private final CountStore store;
  private final Window window;

  /**
   * @param store a fresh count-capable backend under test
   * @param window a window the backend supports, exercised by these scenarios
   */
  public PurgeScenarios(final CountStore store, final Window window) {
    this.store = Objects.requireNonNull(store, "store");
    this.window = Objects.requireNonNull(window, "window");
  }

  /** {@code purge(ns, subject)} clears that subject only; other subjects survive. */
  public void purgeSubjectClearsThatSubjectOnly() {
    final Feature feature = countFeature(window);
    store.applyCount(Tck.apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
    store.applyCount(Tck.apply(NS_A), List.of(new CountIntent(feature, SUBJECT_B)));

    store.purge(NS_A, SUBJECT_A);

    assertValue(count(NS_A, SUBJECT_A), 0);
    assertValue(count(NS_A, SUBJECT_B), 1);
  }

  /** {@code purge(ns, null)} clears the whole namespace; another namespace is untouched. */
  public void purgeNamespaceClearsEntireNamespace() {
    final Feature feature = countFeature(window);
    store.applyCount(Tck.apply(NS_A), List.of(new CountIntent(feature, SUBJECT_A)));
    store.applyCount(Tck.apply(NS_A), List.of(new CountIntent(feature, SUBJECT_B)));
    store.applyCount(Tck.apply(NS_B), List.of(new CountIntent(feature, SUBJECT_A)));

    store.purge(NS_A, null);

    assertValue(count(NS_A, SUBJECT_A), 0);
    assertValue(count(NS_A, SUBJECT_B), 0);
    assertValue(count(NS_B, SUBJECT_A), 1);
  }

  private FeatureResult count(final Namespace namespace, final Subject subject) {
    return store
        .queryCount(Tck.query(namespace), List.of(Tck.tuple(subject, Aggregation.count(), window)))
        .get(0);
  }
}
