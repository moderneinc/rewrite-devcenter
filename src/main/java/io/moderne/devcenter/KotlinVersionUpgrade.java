/*
 * Copyright 2026 the original author or authors.
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.java.dependencies.DependencyInsight;
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.table.DependenciesInUse;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class KotlinVersionUpgrade extends UpgradeMigrationCard {

    @Option(displayName = "Target Kotlin version",
            description = "The target Kotlin version to upgrade to, in major.minor format.",
            example = "2.1")
    String version;

    @Option(example = "org.openrewrite.kotlin.migrate.UpgradeToKotlin2", displayName = "Upgrade recipe",
        description = "The recipe to use to upgrade.",
        required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Kotlin version";

    @Override
    public String getInstanceName() {
        return "Move to Kotlin " + version;
    }

    String description = "Determine the current state of a repository relative to a desired Kotlin version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                DependencyInsight dependencyInsight = new DependencyInsight("org.jetbrains.kotlin", "kotlin-stdlib", null, null);
                DataTableRowWatcher<DependenciesInUse.Row> dataTableWatcher = new DataTableRowWatcher<>(dependencyInsight.getDependenciesInUse(), ctx);
                dataTableWatcher.start();

                Tree t = dependencyInsight.getVisitor().visitNonNull(tree, ctx);

                List<DependenciesInUse.Row> dependenciesInUse = dataTableWatcher.stop();
                int[] target = parseVersion(version);
                for (DependenciesInUse.Row row : dependenciesInUse) {
                    int[] actual = parseVersion(row.getVersion());
                    Measure measure = Measure.Completed;
                    if (compareMajorMinor(actual, target) < 0) {
                        if (actual[0] >= 2 && actual[1] >= 1) {
                            measure = Measure.Kotlin21Plus;
                        } else if (actual[0] >= 2) {
                            measure = Measure.Kotlin20Plus;
                        } else if (actual[1] >= 8) {
                            measure = Measure.Kotlin18Plus;
                        } else if (actual[1] >= 6) {
                            measure = Measure.Kotlin16Plus;
                        } else {
                            measure = Measure.Kotlin14Plus;
                        }
                    }

                    upgradesAndMigrations.insertRow(ctx, KotlinVersionUpgrade.this,
                            measure, row.getVersion());
                }

                return t;
            }
        });
    }

    static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        return new int[]{
                Integer.parseInt(parts[0]),
                parts.length > 1 ? Integer.parseInt(parts[1]) : 0
        };
    }

    private static int compareMajorMinor(int[] a, int[] b) {
        if (a[0] != b[0]) {
            return Integer.compare(a[0], b[0]);
        }
        return Integer.compare(a[1], b[1]);
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        int[] target = parseVersion(version);
        return Stream.of(Measure.values())
                .filter(measure -> compareMajorMinor(
                        new int[]{measure.minimumMajor, measure.minimumMinor}, target) < 0)
                .collect(toList());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        Kotlin14Plus("Kotlin 1.4+", "Kotlin 1.4 and later", 1, 4),
        Kotlin16Plus("Kotlin 1.6+", "Kotlin 1.6 and later", 1, 6),
        Kotlin18Plus("Kotlin 1.8+", "Kotlin 1.8 and later", 1, 8),
        Kotlin20Plus("Kotlin 2.0+", "Kotlin 2.0 and later", 2, 0),
        Kotlin21Plus("Kotlin 2.1+", "Kotlin 2.1 and later", 2, 1),
        Completed("Completed", "The upgrade to the desired Kotlin version is already complete.", 0, 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMajor;
        private final int minimumMinor;
    }
}
