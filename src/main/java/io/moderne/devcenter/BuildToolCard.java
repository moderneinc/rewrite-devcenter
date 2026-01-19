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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.semver.Semver;

import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class BuildToolCard extends UpgradeMigrationCard {

    String displayName = "Build tool";

    String description = "Identify the build tool used by repositories.";

    @Option(
            displayName = "Fix Recipe ID",
            description = "The ID of the recipe to apply to upgrade build tools.",
            example = "org.openrewrite.gradle.MigrateToGradle9",
            required = false
    )
    @Nullable
    String fixRecipeId;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    tree.getMarkers().findFirst(BuildTool.class).ifPresent(buildTool -> {
                        String s = Semver.majorVersion(buildTool.getVersion());
                        int majorVersion = StringUtils.isNumeric(s) ? Integer.parseInt(s) : 0;
                        upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                                getInstanceName(),
                                majorVersion,
                                buildTool.getType().name(),
                                buildTool.getVersion()
                        ));
                    });
                }
                return tree;
            }
        };
    }

    @Override
    public List<DevCenterMeasure> getMeasures() {
        return Arrays.asList(Measure.values());
    }

    @Getter
    @RequiredArgsConstructor
    public enum Measure implements DevCenterMeasure {
        // Values should match BuildTool.Type enum names
        Gradle("Gradle", "Uses Gradle as build tool."),
        Maven("Maven", "Uses Maven as build tool."),
        Bazel("Bazel", "Uses Bazel as build tool."),
        ModerneCli("Moderne CLI", "Uses Moderne CLI as build tool.");

        private final @Language("markdown") String name;
        private final @Language("markdown") String description;
    }
}
