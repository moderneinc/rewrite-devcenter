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
public class GroovyVersionUpgrade extends UpgradeMigrationCard {

    @Option(displayName = "Major version",
            description = "The major version of Groovy to upgrade to.",
            example = "4")
    int majorVersion;

    @Option(displayName = "Upgrade recipe",
        description = "The recipe to use to upgrade.",
        required = false)
    @Nullable
    String upgradeRecipe;

    String displayName = "Move to a later Groovy version";

    @Override
    public String getInstanceName() {
        return "Move to Groovy " + majorVersion;
    }

    String description = "Determine the current state of a repository relative to a desired Groovy version upgrade.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                Tree t = findGroovyDependency(ctx, tree, "groovy", "groovy");
                t = findGroovyDependency(ctx, t, "org.codehaus.groovy", "groovy");
                return findGroovyDependency(ctx, t, "org.apache.groovy", "groovy");
            }
        });
    }

    private Tree findGroovyDependency(ExecutionContext ctx, Tree tree, String groupId, String artifactId) {
        DependencyInsight dependencyInsight = new DependencyInsight(groupId, artifactId, null, null);
        DataTableRowWatcher<DependenciesInUse.Row> dataTableWatcher = new DataTableRowWatcher<>(dependencyInsight.getDependenciesInUse(), ctx);
        dataTableWatcher.start();

        Tree t = dependencyInsight.getVisitor().visitNonNull(tree, ctx);

        List<DependenciesInUse.Row> dependenciesInUse = dataTableWatcher.stop();
        for (DependenciesInUse.Row row : dependenciesInUse) {
            int actualMajor = parseMajorVersion(row.getVersion());
            Measure measure = Measure.Completed;
            if (actualMajor < majorVersion) {
                if (actualMajor >= 5) {
                    measure = Measure.Groovy5Plus;
                } else if (actualMajor >= 4) {
                    measure = Measure.Groovy4Plus;
                } else if (actualMajor >= 3) {
                    measure = Measure.Groovy3Plus;
                } else if (actualMajor >= 2) {
                    measure = Measure.Groovy2Plus;
                } else {
                    measure = Measure.Groovy1Plus;
                }
            }

            upgradesAndMigrations.insertRow(ctx, GroovyVersionUpgrade.this,
                    measure, row.getVersion());
        }
        return t;
    }

    static int parseMajorVersion(String version) {
        String[] parts = version.split("\\.");
        return Integer.parseInt(parts[0]);
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
        Groovy1Plus("Groovy 1+", "Groovy 1 and later", 1),
        Groovy2Plus("Groovy 2+", "Groovy 2 and later", 2),
        Groovy3Plus("Groovy 3+", "Groovy 3 and later", 3),
        Groovy4Plus("Groovy 4+", "Groovy 4 and later", 4),
        Groovy5Plus("Groovy 5+", "Groovy 5 and later", 5),
        Completed("Completed", "The upgrade to the desired Groovy version is already complete.", 0);

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
        private final int minimumMajorVersion;
    }
}
