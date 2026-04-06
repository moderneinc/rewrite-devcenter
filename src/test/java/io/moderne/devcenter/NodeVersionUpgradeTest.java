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
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.moderne.devcenter.NodeVersionUpgrade.Measure.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;

class NodeVersionUpgradeTest implements RewriteTest {

    private static NodeResolutionResult nodeMarker(String nodeConstraint) {
        return new NodeResolutionResult(
                UUID.randomUUID(), "test-project", "1.0.0", null, ".",
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of("node", nodeConstraint), null
        );
    }

    private static Stream<Arguments> nodeVersions() {
        return Stream.of(
          Arguments.of(22, ">=14", 14, Node14Plus),
          Arguments.of(22, ">=16.0.0", 16, Node16Plus),
          Arguments.of(22, "^18.0.0", 18, Node18Plus),
          Arguments.of(22, ">=20", 20, Node20Plus),
          Arguments.of(22, ">=22", 22, Completed),
          Arguments.of(22, ">=24", 24, Completed),
          Arguments.of(20, ">=20", 20, Completed),
          Arguments.of(22, "~18.0.0", 18, Node18Plus),
          Arguments.of(22, ">16", 16, Node16Plus)
        );
    }

    @DocumentExample
    @Test
    void skipsWhenNoNodeEngine() {
        var recipe = new NodeVersionUpgrade(22, null);
        rewriteRun(
          spec -> spec.recipe(recipe),
          java(
            "class Test {}",
            spec -> spec.markers(new NodeResolutionResult(
              UUID.randomUUID(), "test-project", "1.0.0", null, ".",
              null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
              null, Map.of("npm", ">=9"), null
            ))
          )
        );
    }

    @MethodSource("nodeVersions")
    @ParameterizedTest
    void detectsNodeVersion(int targetMajor, String nodeConstraint,
                            int expectedVersion,
                            NodeVersionUpgrade.Measure measure) {
        var recipe = new NodeVersionUpgrade(targetMajor, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Node.js " + targetMajor,
                  recipe.ordinal(measure), measure.getName(), String.valueOf(expectedVersion))
              )),
          java(
            "class Test {}",
            spec -> spec.markers(nodeMarker(nodeConstraint))
          )
        );
    }

    @Test
    void skipsWhenNoEngines() {
        var recipe = new NodeVersionUpgrade(22, null);
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

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(14, List.of(Completed), 0),
          Arguments.of(16, List.of(Node14Plus, Completed), 1),
          Arguments.of(18, List.of(Node14Plus, Node16Plus, Completed), 2),
          Arguments.of(20, List.of(Node14Plus, Node16Plus, Node18Plus, Completed), 3),
          Arguments.of(22, List.of(Node14Plus, Node16Plus, Node18Plus, Node20Plus, Completed), 4),
          Arguments.of(24, List.of(Node14Plus, Node16Plus, Node18Plus, Node20Plus, Node22Plus, Completed), 5)
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetMajor,
                                                      List<NodeVersionUpgrade.Measure> expectedMeasures,
                                                      int expectedCompletedOrdinal) {
        var recipe = new NodeVersionUpgrade(targetMajor, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);

        assertThat(recipe.ordinal(NodeVersionUpgrade.Measure.Completed)).isEqualTo(expectedCompletedOrdinal);
    }
}
