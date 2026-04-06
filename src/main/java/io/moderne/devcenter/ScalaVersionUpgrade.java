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
public class ScalaVersionUpgrade extends UpgradeMigrationCard {

    @Option(displayName = "Major version",
            description = "The major version of Scala to upgrade to.",
            example = "3")
    int majorVersion;

    @Option(example = "org.openrewrite.scala.migrate.UpgradeScala_2_12", displayName = "Upgrade recipe",
        description = "The recipe to use to upgrade.",
        required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Scala version";

    @Override
    public String getInstanceName() {
        return "Move to Scala " + majorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired Scala version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                // Check for Scala 3 first (scala3-library_3), then fall back to
                // scala-library for Scala 2. This order matters because scala3-library_3
                // transitively depends on scala-library, and we want to report
                // the Scala 3 version, not the transitive Scala 2 dependency.
                FindResult result = findScalaDependency(ctx, tree, "org.scala-lang", "scala3-library_3");
                if (!result.found) {
                    result = findScalaDependency(ctx, result.tree, "org.scala-lang", "scala-library");
                }

                return result.tree;
            }
        });
    }

    private static class FindResult {
        final Tree tree;
        final boolean found;

        FindResult(Tree tree, boolean found) {
            this.tree = tree;
            this.found = found;
        }
    }

    private FindResult findScalaDependency(ExecutionContext ctx, Tree tree, String groupId, String artifactId) {
        DependencyInsight dependencyInsight = new DependencyInsight(groupId, artifactId, null, null);
        DataTableRowWatcher<DependenciesInUse.Row> dataTableWatcher = new DataTableRowWatcher<>(dependencyInsight.getDependenciesInUse(), ctx);
        dataTableWatcher.start();

        Tree t = dependencyInsight.getVisitor().visitNonNull(tree, ctx);

        List<DependenciesInUse.Row> dependenciesInUse = dataTableWatcher.stop();
        for (DependenciesInUse.Row row : dependenciesInUse) {
            int actualMajor = parseMajorVersion(row.getVersion());
            Measure measure = Measure.Completed;
            if (actualMajor < majorVersion) {
                if (actualMajor >= 3) {
                    measure = Measure.Scala3Plus;
                } else if (actualMajor == 2) {
                    int actualMinor = parseMinorVersion(row.getVersion());
                    if (actualMinor >= 13) {
                        measure = Measure.Scala213Plus;
                    } else if (actualMinor >= 12) {
                        measure = Measure.Scala212Plus;
                    } else {
                        measure = Measure.Scala211Plus;
                    }
                } else {
                    measure = Measure.Scala211Plus;
                }
            }

            upgradesAndMigrations.insertRow(ctx, ScalaVersionUpgrade.this,
                    measure, row.getVersion());
        }
        return new FindResult(t, !dependenciesInUse.isEmpty());
    }

    static int parseMajorVersion(String version) {
        String[] parts = version.split("\\.");
        return Integer.parseInt(parts[0]);
    }

    static int parseMinorVersion(String version) {
        String[] parts = version.split("\\.");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Stream.of(Measure.values())
                .filter(measure -> measure.minimumMajorVersion < majorVersion)
                .collect(toList());
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        Scala211Plus("Scala 2.11+", "Scala 2.11 and later", 2),
        Scala212Plus("Scala 2.12+", "Scala 2.12 and later", 2),
        Scala213Plus("Scala 2.13+", "Scala 2.13 and later", 2),
        Scala3Plus("Scala 3+", "Scala 3 and later", 3),
        Completed("Completed", "The upgrade to the desired Scala version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMajorVersion;
    }
}
