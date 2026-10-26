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

import io.moderne.devcenter.internal.PackageDependencies;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class PackageMigration extends UpgradeMigrationCard {
    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card.",
            example = "Move from xUnit to TUnit")
    String cardName;

    @Option(displayName = "From package pattern",
            description = "Glob pattern matching the npm, PyPI or NuGet packages or Go modules to move off. " +
                          "PyPI and NuGet package names match case-insensitively.",
            example = "xunit*")
    String fromPackagePattern;

    @Option(displayName = "To package pattern",
            description = "Glob pattern matching the npm, PyPI or NuGet packages or Go modules to move to. " +
                          "PyPI and NuGet package names match case-insensitively.",
            example = "TUnit*")
    String toPackagePattern;

    @Option(displayName = "Migration recipe",
            description = "The recipe to use to migrate.",
            required = false,
            example = "OpenRewrite.Recipes.CSharp.Migration.TUnit.FromXUnit.MigrateFromXUnit")
    @Nullable
    String migrationRecipe;

    String displayName = "Package migration";

    @Override
    public String getInstanceName() {
        return getCardName();
    }

    String description = "Determine the current state of a repository relative to a move from one npm, PyPI or NuGet package, or Go module, to another. " +
                         "Only direct dependencies count, as those are what a project's own code uses.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile source = (SourceFile) tree;
                String from = PackageDependencies.findDirectDependency(source, fromPackagePattern);
                String to = PackageDependencies.findDirectDependency(source, toPackagePattern);
                if (from != null) {
                    upgradesAndMigrations.insertRow(ctx, PackageMigration.this,
                            to == null ? Measure.NotStarted : Measure.InProgress, from);
                } else if (to != null) {
                    upgradesAndMigrations.insertRow(ctx, PackageMigration.this, Measure.Completed, to);
                }
                return tree;
            }
        };
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return migrationRecipe;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(Measure.values());
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        NotStarted("Not started", "Depends on the package to move off, and not yet on the one to move to."),
        InProgress("In progress", "Depends on both the package to move off and the one to move to."),
        Completed("Completed", "Depends only on the package to move to.");

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
    }
}
