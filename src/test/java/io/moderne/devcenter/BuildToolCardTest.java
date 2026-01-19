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
import org.junit.jupiter.api.Test;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.test.RewriteTest;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class BuildToolCardTest implements RewriteTest {

    @Test
    void detectsMajorVersionBehind() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Upgrade to Gradle 9",
                  SemverMeasure.Major.ordinal(),
                  SemverMeasure.Major.toString(),
                  "8.5.0"
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), BuildTool.Type.Gradle, "8.5.0"))
          )
        );
    }

    @Test
    void detectsMinorVersionBehind() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.3.0", null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Upgrade to Gradle 9",
                  SemverMeasure.Minor.ordinal(),
                  SemverMeasure.Minor.toString(),
                  "9.1.0"
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), BuildTool.Type.Gradle, "9.1.0"))
          )
        );
    }

    @Test
    void detectsPatchVersionBehind() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.3.2", null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Upgrade to Gradle 9",
                  SemverMeasure.Patch.ordinal(),
                  SemverMeasure.Patch.toString(),
                  "9.3.0"
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), BuildTool.Type.Gradle, "9.3.0"))
          )
        );
    }

    @Test
    void detectsCompleted() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Upgrade to Gradle 9",
                  SemverMeasure.Completed.ordinal(),
                  SemverMeasure.Completed.toString(),
                  "9.3.0"
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), BuildTool.Type.Gradle, "9.3.0"))
          )
        );
    }

    @Test
    void filtersByBuildToolCaseInsensitive() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "gradle", "9.0.0", null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Upgrade to Gradle 9",
                  SemverMeasure.Completed.ordinal(),
                  SemverMeasure.Completed.toString(),
                  "9.3.0"
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), BuildTool.Type.Gradle, "9.3.0"))
          )
        );
    }

    @Test
    void getMeasuresReturnsSemverMeasures() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", null);
        assertThat(recipe.getMeasures())
          .hasSameSizeAs(SemverMeasure.values());
    }

    @Test
    void instanceNameIsCardName() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", null);
        assertThat(recipe.getInstanceName()).isEqualTo("Upgrade to Gradle 9");
    }

    @Test
    void noFixRecipe() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", null);
        assertThat(recipe.getFixRecipeId()).isNull();
    }

    @Test
    void argFixRecipe() {
        BuildToolCard recipe = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", "org.openrewrite.gradle.MigrateToGradle9");
        assertThat(recipe.getFixRecipeId()).isEqualTo("org.openrewrite.gradle.MigrateToGradle9");
    }
}
