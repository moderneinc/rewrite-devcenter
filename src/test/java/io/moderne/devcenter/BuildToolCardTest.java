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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.test.RewriteTest;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class BuildToolCardTest implements RewriteTest {

    private static Stream<Arguments> buildTools() {
        return Stream.of(
          Arguments.of(BuildTool.Type.Gradle, "8.5", 8),
          Arguments.of(BuildTool.Type.Maven, "3.9.6", 3),
          Arguments.of(BuildTool.Type.Bazel, "7.0.0", 7),
          Arguments.of(BuildTool.Type.ModerneCli, "3.30.5", 3)
        );
    }

    @MethodSource("buildTools")
    @ParameterizedTest
    void detectsBuildTool(BuildTool.Type type, String version, int expectedMajorVersion) {
        BuildToolCard recipe = new BuildToolCard(null, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Build tool",
                  expectedMajorVersion,
                  type.name(),
                  version
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), type, version))
          )
        );
    }

    @Test
    void filtersByBuildTool() {
        BuildToolCard recipe = new BuildToolCard("Gradle", null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row(
                  "Build tool",
                  8,
                  "Gradle",
                  "8.5"
                )
              )),
          java(
            "class Test {}",
            spec -> spec.markers(new BuildTool(UUID.randomUUID(), BuildTool.Type.Gradle, "8.5"))
          )
        );
    }

    @Test
    void getMeasuresReturnsAllBuildToolTypes() {
        BuildToolCard recipe = new BuildToolCard(null, null);
        assertThat(recipe.getMeasures())
          .hasSameSizeAs(BuildTool.Type.values());
    }

    @Test
    void getMeasuresReturnsOnlyMatchingBuildTool() {
        BuildToolCard recipe = new BuildToolCard("Gradle", null);
        assertThat(recipe.getMeasures())
          .hasSize(1)
          .first()
          .isEqualTo(BuildToolCard.Measure.Gradle);
    }

    @Test
    void noFixRecipe() {
        BuildToolCard recipe = new BuildToolCard(null, null);
        assertThat(recipe.getFixRecipeId()).isNull();
    }

    @Test
    void argFixRecipe() {
        BuildToolCard recipe = new BuildToolCard(null, "com.example.Recipe");
        assertThat(recipe.getFixRecipeId()).isEqualTo("com.example.Recipe");
    }
}
