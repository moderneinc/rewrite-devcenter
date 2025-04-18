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

import io.moderne.devcenter.internal.DataTableRowWatcher;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.java.dependencies.DependencyInsight;
import org.openrewrite.java.dependencies.internal.Version;
import org.openrewrite.java.dependencies.internal.VersionParser;
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class LibraryUpgrade extends Recipe {
    transient UpgradesAndMigrations upgradesAndMigrations = new UpgradesAndMigrations(this);

    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card")
    String cardName;

    @Option(displayName = "Group pattern",
            description = "Group glob pattern used to match dependencies.",
            example = "com.fasterxml.jackson.module")
    String groupIdPattern;

    @Option(displayName = "Artifact pattern",
            description = "Artifact glob pattern used to match dependencies.",
            example = "jackson-module-*")
    String artifactIdPattern;

    @Option(displayName = "Target version",
            description = "The target version of the upgrade. " +
                          "Specify the version out to the desired patch version.",
            example = "3.4.1")
    String version;

    @Override
    public String getDisplayName() {
        return "Library upgrade";
    }

    @Override
    public String getInstanceName() {
        return getCardName();
    }

    @Override
    public String getDescription() {
        return "Determine the current state of a repository relative to a desired library upgrade.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                DependencyInsight dependencyInsight = new DependencyInsight(groupIdPattern, artifactIdPattern, null, null);
                DataTableRowWatcher<DependenciesInUse.Row> dataTableWatcher = new DataTableRowWatcher<>(dependencyInsight.getDependenciesInUse(), ctx);
                dataTableWatcher.start();

                UpgradeRowBuilder rowBuilder = new UpgradeRowBuilder(cardName, version);
                Tree t = dependencyInsight.getVisitor().visitNonNull(tree, ctx);

                for (DependenciesInUse.Row dependencyInUse : dataTableWatcher.stop()) {
                    UpgradesAndMigrations.Row row = rowBuilder.getRow(dependencyInUse.getVersion());
                    upgradesAndMigrations.insertRow(ctx, row);
                }

                return t;
            }
        });
    }

    public enum Measure {
        Major,
        Minor,
        Patch,
        Completed
    }

    private static class UpgradeRowBuilder {
        private static final VersionParser parser = new VersionParser();

        private final String cardName;
        private long major;
        private long minor;
        private long patch;

        public UpgradeRowBuilder(String cardName, String version) {
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
                        return;
                    case 2:
                        this.patch = part.intValue();
                        return;
                    default:
                        return;
                }
            }
        }

        public UpgradesAndMigrations.Row getRow(String v) {
            VersionComparator majorComparator = requireNonNull(Semver.validate(
                    0 + "-" + (major - 1) + ".999", null).getValue());
            if (majorComparator.isValid(null, v)) {
                return new UpgradesAndMigrations.Row(cardName, Measure.Major.ordinal(), Measure.Major.toString(), v);
            }

            VersionComparator minorComparator = requireNonNull(Semver.validate(
                    major + "-" + major + "." + (minor - 1) + ".999", null).getValue());
            if (minorComparator.isValid(null, v)) {
                return new UpgradesAndMigrations.Row(cardName, Measure.Minor.ordinal(), Measure.Minor.toString(), v);
            }

            VersionComparator patchComparator = requireNonNull(Semver.validate(
                    (major + "." + minor + ".0") + "-" + (major + "." + minor + "." + (patch - 1)),
                    null).getValue());
            if (patchComparator.isValid(null, v)) {
                return new UpgradesAndMigrations.Row(cardName, Measure.Patch.ordinal(), Measure.Patch.toString(), v);
            }

            return new UpgradesAndMigrations.Row(cardName, Measure.Completed.ordinal(), Measure.Completed.toString(), v);
        }
    }
}
