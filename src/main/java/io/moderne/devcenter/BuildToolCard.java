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

import io.moderne.devcenter.table.UpgradesAndMigrations;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.marker.BuildTool;

import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class BuildToolCard extends UpgradeMigrationCard {

    @Option(displayName = "Card name",
            description = "The display name of the DevCenter card.",
            example = "Upgrade to Gradle 9")
    String cardName;

    @Option(displayName = "Build tool",
            description = "The build tool to track.",
            valid = {"Gradle", "Maven", "Bazel", "ModerneCli"})
    String buildTool;

    @Option(displayName = "Target version",
            description = "The target version of the build tool. Specify the version out to the desired patch version.",
            example = "9.0.0")
    String targetVersion;

    @Option(displayName = "Fix Recipe ID",
            description = "The recipe to use to upgrade the build tool.",
            example = "org.openrewrite.gradle.MigrateToGradle9",
            required = false)
    @Nullable
    String fixRecipeId;

    String displayName = "Build tool";
    String description = "Track build tool versions across repositories.";

    @Override
    public String getInstanceName() {
        return getCardName();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    tree.getMarkers().findFirst(BuildTool.class).ifPresent(bt -> {
                        if (bt.getType().name().equalsIgnoreCase(buildTool)) {
                            SemverRowBuilder rowBuilder = new SemverRowBuilder(cardName, targetVersion);
                            upgradesAndMigrations.insertRow(ctx, rowBuilder.getRow(bt.getVersion()));
                        }
                    });
                }
                return tree;
            }
        };
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(SemverMeasure.values());
    }
}
