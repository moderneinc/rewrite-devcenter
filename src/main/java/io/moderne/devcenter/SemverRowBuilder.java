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

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.openrewrite.java.dependencies.internal.Version;
import org.openrewrite.java.dependencies.internal.VersionParser;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

import static java.util.Objects.requireNonNull;

public class SemverRowBuilder {
    private static final VersionParser parser = new VersionParser();

    private final String cardName;
    private long major;
    private long minor;
    private long patch;

    public SemverRowBuilder(String cardName, String version) {
        this.cardName = cardName;
        Version parsed = parser.transform(version);
        Long[] numericParts = parsed.getNumericParts();
        for (int i = 0; i < numericParts.length; i++) {
            Long part = numericParts[i];
            //noinspection ConstantValue
            if (part == null) {
                return;
            }
            switch (i) {
                case 0:
                    this.major = part.intValue();
                    break;
                case 1:
                    this.minor = part.intValue();
                    break;
                case 2:
                    this.patch = part.intValue();
                    return;
                default:
                    return;
            }
        }
    }

    public UpgradesAndMigrations.Row getRow(String v) {
        VersionComparator majorComparator = requireNonNull(Semver.validate(0 + "-" + (major - 1) + ".999", null)
                .getValue());
        if (majorComparator.isValid(null, v)) {
            return new UpgradesAndMigrations.Row(cardName, SemverMeasure.Major.ordinal(), SemverMeasure.Major.toString(), v);
        }

        VersionComparator minorComparator = requireNonNull(Semver.validate(
                major + "-" + major + "." + (minor - 1) + ".999",
                null).getValue());
        if (minorComparator.isValid(null, v)) {
            return new UpgradesAndMigrations.Row(cardName, SemverMeasure.Minor.ordinal(), SemverMeasure.Minor.toString(), v);
        }

        VersionComparator patchComparator = requireNonNull(Semver.validate(
                (major + "." + minor + ".0") + "-" + (major + "." + minor + "." + (patch - 1)),
                null).getValue());
        if (patchComparator.isValid(null, v)) {
            return new UpgradesAndMigrations.Row(cardName, SemverMeasure.Patch.ordinal(), SemverMeasure.Patch.toString(), v);
        }

        return new UpgradesAndMigrations.Row(cardName, SemverMeasure.Completed.ordinal(), SemverMeasure.Completed.toString(), v);
    }
}
