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
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.table.DependenciesInUse;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@EqualsAndHashCode(callSuper = false)
public class LibraryUpgrade extends Recipe implements DevCenterMeasurer {
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
    public Set<String> getTags() {
        return Collections.singleton(DevCenter.UPGRADE_OR_MIGRATION_TAG);
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

                SemverRowBuilder rowBuilder = new SemverRowBuilder(cardName, version);
                Tree t = dependencyInsight.getVisitor().visitNonNull(tree, ctx);

                List<DependenciesInUse.Row> dependenciesInUse = dataTableWatcher.stop();
                for (DependenciesInUse.Row dependencyInUse : dependenciesInUse) {
                    UpgradesAndMigrations.Row row = rowBuilder.getRow(dependencyInUse.getVersion());
                    upgradesAndMigrations.insertRow(ctx, row);
                }

                return t;
            }
        });
    }

    @Override
    public List<String> getMeasures() {
        return Stream.of(SemverMeasure.values()).map(SemverMeasure::name).collect(Collectors.toList());
    }
}
