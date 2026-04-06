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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.csharp.marker.MSBuildProject;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.moderne.devcenter.CSharpVersionUpgrade.Measure.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class CSharpVersionUpgradeTest implements RewriteTest {

    private static MSBuildProject msBuildMarker(String... tfms) {
        List<MSBuildProject.TargetFramework> targetFrameworks = Stream.of(tfms)
                .map(tfm -> MSBuildProject.TargetFramework.builder()
                        .targetFramework(tfm)
                        .build())
                .toList();
        return MSBuildProject.builder()
                .id(UUID.randomUUID())
                .targetFrameworks(targetFrameworks)
                .build();
    }

    private static Stream<Arguments> dotnetVersions() {
        return Stream.of(
          Arguments.of(10, "net6.0", "net6.0", DotNet6Plus),
          Arguments.of(10, "net7.0", "net7.0", DotNet7Plus),
          Arguments.of(10, "net8.0", "net8.0", DotNet8Plus),
          Arguments.of(10, "net9.0", "net9.0", DotNet9Plus),
          Arguments.of(10, "net10.0", "net10.0", Completed),
          Arguments.of(9, "net9.0", "net9.0", Completed),
          Arguments.of(10, "netcoreapp3.1", "netcoreapp3.1", DotNetFramework),
          Arguments.of(10, "net48", "net48", DotNetFramework),
          Arguments.of(10, "netstandard2.0", "netstandard2.0", DotNetFramework)
        );
    }

    @DocumentExample
    @Test
    void usesLowestVersionWhenMultiTargeting() {
        var recipe = new CSharpVersionUpgrade(9, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to .NET 9",
                  recipe.ordinal(DotNet6Plus), DotNet6Plus.getName(), "net6.0")
              )),
          java(
            "class Test {}",
            spec -> spec.markers(msBuildMarker("net6.0", "net8.0"))
          )
        );
    }

    @MethodSource("dotnetVersions")
    @ParameterizedTest
    void detectsDotNetVersion(int targetMajor, String tfm,
                              String expectedTfm,
                              CSharpVersionUpgrade.Measure measure) {
        var recipe = new CSharpVersionUpgrade(targetMajor, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to .NET " + targetMajor,
                  recipe.ordinal(measure), measure.getName(), expectedTfm)
              )),
          java(
            "class Test {}",
            spec -> spec.markers(msBuildMarker(tfm))
          )
        );
    }

    @Test
    void skipsWhenNoTargetFrameworks() {
        var recipe = new CSharpVersionUpgrade(9, null);
        rewriteRun(
          spec -> spec.recipe(recipe),
          java(
            "class Test {}",
            spec -> spec.markers(MSBuildProject.builder()
              .id(UUID.randomUUID())
              .build())
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(6, List.of(DotNetFramework, Completed), 1),
          Arguments.of(7, List.of(DotNetFramework, DotNet6Plus, Completed), 2),
          Arguments.of(8, List.of(DotNetFramework, DotNet6Plus, DotNet7Plus, Completed), 3),
          Arguments.of(9, List.of(DotNetFramework, DotNet6Plus, DotNet7Plus, DotNet8Plus, Completed), 4),
          Arguments.of(10, List.of(DotNetFramework, DotNet6Plus, DotNet7Plus, DotNet8Plus, DotNet9Plus, Completed), 5),
          Arguments.of(11, List.of(DotNetFramework, DotNet6Plus, DotNet7Plus, DotNet8Plus, DotNet9Plus, DotNet10Plus, Completed), 6)
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetMajor,
                                                      List<CSharpVersionUpgrade.Measure> expectedMeasures,
                                                      int expectedCompletedOrdinal) {
        var recipe = new CSharpVersionUpgrade(targetMajor, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);

        assertThat(recipe.ordinal(CSharpVersionUpgrade.Measure.Completed)).isEqualTo(expectedCompletedOrdinal);
    }
}
