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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static io.moderne.devcenter.ScalaVersionUpgrade.Measure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class ScalaVersionUpgradeTest implements RewriteTest {

    private static Stream<Arguments> scalaVersions() {
        return Stream.of(
          Arguments.of(3, "org.scala-lang", "scala-library", "2.11.12", Scala211Plus, 1),
          Arguments.of(3, "org.scala-lang", "scala-library", "2.12.18", Scala212Plus, 1),
          Arguments.of(3, "org.scala-lang", "scala-library", "2.13.12", Scala213Plus, 1),
          Arguments.of(3, "org.scala-lang", "scala3-library_3", "3.3.1", Completed, 2),
          Arguments.of(3, "org.scala-lang", "scala3-library_3", "3.5.0", Completed, 2),
          Arguments.of(4, "org.scala-lang", "scala3-library_3", "3.5.0", Scala3Plus, 2)
        );
    }

    @MethodSource("scalaVersions")
    @ParameterizedTest
    void detectsScalaVersion(int targetVersion, String groupId, String artifactId,
                             String currentVersion, ScalaVersionUpgrade.Measure measure,
                             int expectedCycles) {
        var recipe = new ScalaVersionUpgrade(targetVersion, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .expectedCyclesThatMakeChanges(expectedCycles)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Scala " + targetVersion,
                  recipe.ordinal(measure), measure.getName(), currentVersion)
              )),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>%s</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(groupId, artifactId, currentVersion),
            spec -> spec.after(after -> {
                assertThat(after).isNotNull();
                return after;
            })
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(3, List.of(Scala211Plus, Scala212Plus, Scala213Plus, Completed)),
          Arguments.of(4, List.of(Scala211Plus, Scala212Plus, Scala213Plus, Scala3Plus, Completed))
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetVersion,
                                                      List<ScalaVersionUpgrade.Measure> expectedMeasures) {
        var recipe = new ScalaVersionUpgrade(targetVersion, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);
    }
}
