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
import org.openrewrite.python.marker.PythonResolutionResult;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.moderne.devcenter.PythonVersionUpgrade.Measure.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class PythonVersionUpgradeTest implements RewriteTest {

    private static PythonResolutionResult pythonMarker(String requiresPython) {
        return new PythonResolutionResult(
                UUID.randomUUID(), "test-project", "1.0.0", null, null,
                ".", requiresPython, null,
                List.of(), List.of(), Map.of(), Map.of(),
                List.of(), List.of(), List.of(), null, null
        );
    }

    private static Stream<Arguments> pythonVersions() {
        return Stream.of(
          Arguments.of(13, ">=2.7", "2.7", Python2),
          Arguments.of(13, ">=3.8", "3.8", Python38Plus),
          Arguments.of(13, ">=3.9", "3.9", Python39Plus),
          Arguments.of(13, ">=3.10", "3.10", Python310Plus),
          Arguments.of(13, ">=3.11", "3.11", Python311Plus),
          Arguments.of(13, ">=3.12", "3.12", Python312Plus),
          Arguments.of(13, ">=3.13", "3.13", Completed),
          Arguments.of(13, ">=3.14", "3.14", Completed),
          Arguments.of(12, ">=3.12", "3.12", Completed),
          Arguments.of(13, "~=3.10", "3.10", Python310Plus),
          Arguments.of(13, ">=3.8,<4", "3.8", Python38Plus)
        );
    }

    @MethodSource("pythonVersions")
    @ParameterizedTest
    void detectsPythonVersion(int targetMinor, String requiresPython,
                              String expectedVersion,
                              PythonVersionUpgrade.Measure measure) {
        var recipe = new PythonVersionUpgrade(targetMinor, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Python 3." + targetMinor,
                  recipe.ordinal(measure), measure.getName(), expectedVersion)
              )),
          java(
            "class Test {}",
            spec -> spec.markers(pythonMarker(requiresPython))
          )
        );
    }

    @Test
    void skipsWhenNoRequiresPython() {
        var recipe = new PythonVersionUpgrade(13, null);
        rewriteRun(
          spec -> spec.recipe(recipe),
          java(
            "class Test {}",
            spec -> spec.markers(new PythonResolutionResult(
              UUID.randomUUID(), "test-project", "1.0.0", null, null,
              ".", null, null,
              List.of(), List.of(), Map.of(), Map.of(),
              List.of(), List.of(), List.of(), null, null
            ))
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(8, List.of(Python2, Completed), 1),
          Arguments.of(9, List.of(Python2, Python38Plus, Completed), 2),
          Arguments.of(10, List.of(Python2, Python38Plus, Python39Plus, Completed), 3),
          Arguments.of(13, List.of(Python2, Python38Plus, Python39Plus, Python310Plus,
            Python311Plus, Python312Plus, Completed), 6),
          Arguments.of(14, List.of(Python2, Python38Plus, Python39Plus, Python310Plus,
            Python311Plus, Python312Plus, Python313Plus, Completed), 7)
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetMinor,
                                                      List<PythonVersionUpgrade.Measure> expectedMeasures,
                                                      int expectedCompletedOrdinal) {
        var recipe = new PythonVersionUpgrade(targetMinor, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);

        assertThat(recipe.ordinal(PythonVersionUpgrade.Measure.Completed)).isEqualTo(expectedCompletedOrdinal);
    }
}
