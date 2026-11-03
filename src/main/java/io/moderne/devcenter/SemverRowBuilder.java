/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.devcenter;

import io.moderne.devcenter.internal.VersionParts;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.jspecify.annotations.Nullable;
import org.openrewrite.semver.VersionComparator;

/**
 * Measures how far a version is behind a target by comparing their major, minor and patch numbers. Qualifiers
 * don't move a version past the number in front of them, so {@code 3.5.0-SNAPSHOT} is as far behind {@code 4.1.0}
 * as {@code 3.5.0} is, and a pre-release of the target itself, such as {@code 4.1.0-RC1}, is a patch behind it.
 */
public class SemverRowBuilder {
    private final String cardName;
    private final long[] target = new long[3];

    public SemverRowBuilder(String cardName, String version) {
        this.cardName = cardName;
        numbers(version, target);
    }

    public UpgradesAndMigrations.Row getRow(String v) {
        SemverMeasure measure = measure(v);
        return new UpgradesAndMigrations.Row(cardName, measure.ordinal(), measure.toString(), v);
    }

    private SemverMeasure measure(String v) {
        long[] actual = new long[3];
        if (!numbers(v, actual)) {
            // Nothing to compare, such as an unresolved property
            return SemverMeasure.Completed;
        }
        SemverMeasure[] behind = {SemverMeasure.Major, SemverMeasure.Minor, SemverMeasure.Patch};
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] < target[i]) {
                return behind[i];
            }
            if (actual[i] > target[i]) {
                return SemverMeasure.Completed;
            }
        }
        return VersionComparator.PRE_RELEASE_ENDING.matcher(v).find() ? SemverMeasure.Patch : SemverMeasure.Completed;
    }

    /**
     * Fills {@code numbers} with the major, minor and patch numbers at the start of {@code version}, leaving those
     * it doesn't have at zero.
     *
     * @return false when {@code version} doesn't start with a number.
     */
    private static boolean numbers(String version, long[] numbers) {
        @Nullable Long[] parts = VersionParts.numeric(version);
        for (int i = 0; i < numbers.length && i < parts.length; i++) {
            Long part = parts[i];
            if (part == null) {
                break;
            }
            numbers[i] = part;
        }
        return parts.length > 0 && parts[0] != null;
    }
}
