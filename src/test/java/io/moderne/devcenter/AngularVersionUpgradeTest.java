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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.javascript.marker.NodeResolutionResult.Dependency;
import org.openrewrite.javascript.marker.NodeResolutionResult.ResolvedDependency;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.moderne.devcenter.AngularVersionUpgrade.Measure.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class AngularVersionUpgradeTest implements RewriteTest {

    private static NodeResolutionResult angularMarker(String constraint, @Nullable String resolvedVersion) {
        List<Dependency> deps = List.of(new Dependency("@angular/core", constraint, null));
        List<ResolvedDependency> resolved = resolvedVersion == null ?
          List.of() :
          List.of(new ResolvedDependency("@angular/core", resolvedVersion,
            List.of(), List.of(), List.of(), List.of(), null, null));
        return new NodeResolutionResult(
          UUID.randomUUID(), "test-project", "1.0.0", null, ".",
          null, deps, List.of(), List.of(), List.of(), List.of(), resolved,
          null, null, null
        );
    }

    @DocumentExample
    @Test
    void skipsWhenNoAngularCore() {
        var recipe = new AngularVersionUpgrade(21, null);
        rewriteRun(
          spec -> spec.recipe(recipe),
          java(
            "class Test {}",
            spec -> spec.markers(new NodeResolutionResult(
              UUID.randomUUID(), "test-project", "1.0.0", null, ".",
              null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
              null, null, null
            ))
          )
        );
    }

    @Test
    void skipsAngularJsLegacyPackage() {
        var recipe = new AngularVersionUpgrade(21, null);
        List<Dependency> deps = List.of(new Dependency("angular", "^1.8.0", null));
        rewriteRun(
          spec -> spec.recipe(recipe),
          java(
            "class Test {}",
            spec -> spec.markers(new NodeResolutionResult(
              UUID.randomUUID(), "legacy-app", "1.0.0", null, ".",
              null, deps, List.of(), List.of(), List.of(), List.of(), List.of(),
              null, null, null
            ))
          )
        );
    }

    private static Stream<Arguments> angularVersions() {
        return Stream.of(
          // targetMajor, constraint, resolvedVersion, expectedMajor, expectedMeasure
          Arguments.of(21, "^5.2.0", "5.2.11", 5, Lag10PlusMajors),
          Arguments.of(21, "^7.2.0", "7.2.16", 7, Lag10PlusMajors),
          Arguments.of(21, "^8.0.0", "8.2.14", 8, Lag10PlusMajors),
          Arguments.of(21, "~9.1.0", "9.1.13", 9, Lag10PlusMajors),
          Arguments.of(21, "^10.0.0", "10.2.5", 10, Lag10PlusMajors),
          Arguments.of(21, "^12.0.0", "12.2.0", 12, Lag5to9Majors),
          Arguments.of(21, "^14.0.0", "14.3.0", 14, Lag5to9Majors),
          Arguments.of(21, "^16.0.0", "16.2.5", 16, Lag5to9Majors),
          Arguments.of(21, "^17.0.0", "17.3.12", 17, Lag2to4Majors),
          Arguments.of(21, "^19.0.0", "19.1.0", 19, Lag2to4Majors),
          Arguments.of(21, "20.1.0", "20.1.0", 20, Lag1Major),
          Arguments.of(21, "^21.0.0", "21.0.1", 21, Completed),
          Arguments.of(17, "^17.0.0", "17.3.12", 17, Completed),
          // Fallback path: no resolved version, only the constraint
          Arguments.of(21, "~16.2.5", null, 16, Lag5to9Majors),
          Arguments.of(21, ">=18.0.0", null, 18, Lag2to4Majors)
        );
    }

    @MethodSource("angularVersions")
    @ParameterizedTest
    void detectsAngularVersion(int targetMajor, String constraint, @Nullable String resolvedVersion,
                               int expectedMajor, AngularVersionUpgrade.Measure measure) {
        var recipe = new AngularVersionUpgrade(targetMajor, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Angular " + targetMajor,
                  recipe.ordinal(measure), measure.getName(), String.valueOf(expectedMajor))
              )),
          java(
            "class Test {}",
            spec -> spec.markers(angularMarker(constraint, resolvedVersion))
          )
        );
    }

    private static Stream<Arguments> targetAndMeasures() {
        return Stream.of(
          // Target 2: only one lag bucket is reachable (Lag1Major)
          Arguments.of(2, List.of(Lag1Major, Completed), 1),
          // Target 5: lag can reach 4
          Arguments.of(5, List.of(Lag2to4Majors, Lag1Major, Completed), 2),
          // Target 9: lag can reach 8, so Lag10PlusMajors is unreachable
          Arguments.of(9, List.of(Lag5to9Majors, Lag2to4Majors, Lag1Major, Completed), 3),
          // Target 11: max lag is 10, all five buckets become reachable
          Arguments.of(11, List.of(Lag10PlusMajors, Lag5to9Majors, Lag2to4Majors, Lag1Major, Completed), 4),
          Arguments.of(21, List.of(Lag10PlusMajors, Lag5to9Majors, Lag2to4Majors, Lag1Major, Completed), 4)
        );
    }

    @MethodSource("targetAndMeasures")
    @ParameterizedTest
    void measuresFilterUnreachableLagBuckets(int targetMajor,
                                             List<AngularVersionUpgrade.Measure> expectedMeasures,
                                             int expectedCompletedOrdinal) {
        var recipe = new AngularVersionUpgrade(targetMajor, null);
        assertThat(recipe.getMeasures()).containsExactlyElementsOf(expectedMeasures);
        assertThat(recipe.ordinal(AngularVersionUpgrade.Measure.Completed)).isEqualTo(expectedCompletedOrdinal);
    }
}
