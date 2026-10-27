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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class PackageUpgrade extends UpgradeMigrationCard {
    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card.",
            example = "Move to LangChain 1.0")
    String cardName;

    @Option(displayName = "Package pattern",
            description = "Glob pattern used to match npm, PyPI and NuGet package names and Go module paths. " +
                          "PyPI and NuGet package names match case-insensitively.",
            example = "langchain")
    String packagePattern;

    @Option(displayName = "Target version",
            description = "The target version of the upgrade. " +
                          "Specify the version out to the desired patch version.",
            example = "1.0.0")
    String version;

    @Option(displayName = "Upgrade recipe",
            description = "The recipe to use to upgrade.",
            required = false,
            example = "org.openrewrite.python.migrate.langchain.UpgradeToLangChain1")
    @Nullable
    String upgradeRecipe;

    String displayName = "Package upgrade";

    @Override
    public String getInstanceName() {
        return getCardName();
    }

    String description = "Determine the current state of a repository relative to a desired upgrade of an npm, PyPI or NuGet package, or a Go module.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                List<String> versions = PackageDependencies.findVersions((SourceFile) tree, packagePattern);
                if (!versions.isEmpty()) {
                    SemverRowBuilder rowBuilder = new SemverRowBuilder(cardName, version);
                    for (String found : versions) {
                        upgradesAndMigrations.insertRow(ctx, rowBuilder.getRow(found));
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public @Nullable String getFixRecipeId() {
        return upgradeRecipe;
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(SemverMeasure.values());
    }
}
