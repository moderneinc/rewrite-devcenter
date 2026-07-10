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
import org.openrewrite.golang.marker.GoResolutionResult;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.moderne.devcenter.GoVersionUpgrade.Measure.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class GoVersionUpgradeTest implements RewriteTest {

    private static GoResolutionResult goMarker(String goVersion) {
        return new GoResolutionResult(
                UUID.randomUUID(), "github.com/test/project", goVersion, null, ".",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static Stream<Arguments> goVersions() {
        return Stream.of(
          Arguments.of(26, "1.17", "1.17", GoPre118),
          Arguments.of(26, "1.18", "1.18", Go118Plus),
          Arguments.of(26, "1.20", "1.20", Go120Plus),
          Arguments.of(26, "1.21.5", "1.21", Go121Plus),
          Arguments.of(26, "1.24", "1.24", Go124Plus),
          Arguments.of(26, "1.25", "1.25", Go125Plus),
          Arguments.of(26, "1.26", "1.26", Completed),
          Arguments.of(26, "1.26.0", "1.26", Completed),
          Arguments.of(24, "1.24", "1.24", Completed)
        );
    }

    @MethodSource("goVersions")
    @ParameterizedTest
    void detectsGoVersion(int targetMinor, String goVersion,
                          String expectedVersion,
                          GoVersionUpgrade.Measure measure) {
        var recipe = new GoVersionUpgrade(targetMinor, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Go 1." + targetMinor,
                  recipe.ordinal(measure), measure.getName(), expectedVersion)
              )),
          java(
            "class Test {}",
            spec -> spec.markers(goMarker(goVersion))
          )
        );
    }

    @Test
    void skipsWhenNoGoVersion() {
        var recipe = new GoVersionUpgrade(26, null);
        rewriteRun(
          spec -> spec.recipe(recipe),
          java(
            "class Test {}",
            spec -> spec.markers(goMarker(null))
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(18, List.of(GoPre118, Completed), 1),
          Arguments.of(20, List.of(GoPre118, Go118Plus, Go119Plus, Completed), 3),
          Arguments.of(26, List.of(GoPre118, Go118Plus, Go119Plus, Go120Plus, Go121Plus,
            Go122Plus, Go123Plus, Go124Plus, Go125Plus, Completed), 9)
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetMinor,
                                                      List<GoVersionUpgrade.Measure> expectedMeasures,
                                                      int expectedCompletedOrdinal) {
        var recipe = new GoVersionUpgrade(targetMinor, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);

        assertThat(recipe.ordinal(GoVersionUpgrade.Measure.Completed)).isEqualTo(expectedCompletedOrdinal);
    }
}
