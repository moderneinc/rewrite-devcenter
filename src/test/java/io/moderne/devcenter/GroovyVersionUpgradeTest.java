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

import static io.moderne.devcenter.GroovyVersionUpgrade.Measure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class GroovyVersionUpgradeTest implements RewriteTest {

    private static Stream<Arguments> groovyVersions() {
        return Stream.of(
          Arguments.of(4, "groovy", "1.0", Groovy1Plus),
          Arguments.of(4, "org.codehaus.groovy", "2.5.14", Groovy2Plus),
          Arguments.of(4, "org.codehaus.groovy", "3.0.19", Groovy3Plus),
          Arguments.of(4, "org.apache.groovy", "4.0.24", Completed),
          Arguments.of(5, "org.apache.groovy", "4.0.24", Groovy4Plus),
          Arguments.of(5, "org.apache.groovy", "5.0.0", Completed)
        );
    }

    @MethodSource("groovyVersions")
    @ParameterizedTest
    void detectsGroovyVersion(int targetVersion, String groupId, String currentVersion,
                              GroovyVersionUpgrade.Measure measure) {
        var recipe = new GroovyVersionUpgrade(targetVersion, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Groovy " + targetVersion,
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
                        <artifactId>groovy</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(groupId, currentVersion),
            spec -> spec.after(after -> {
                assertThat(after).isNotNull();
                return after;
            })
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(2, List.of(Groovy1Plus, Completed)),
          Arguments.of(3, List.of(Groovy1Plus, Groovy2Plus, Completed)),
          Arguments.of(4, List.of(Groovy1Plus, Groovy2Plus, Groovy3Plus, Completed)),
          Arguments.of(5, List.of(Groovy1Plus, Groovy2Plus, Groovy3Plus, Groovy4Plus, Completed))
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetVersion,
                                                      List<GroovyVersionUpgrade.Measure> expectedMeasures) {
        var recipe = new GroovyVersionUpgrade(targetVersion, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);
    }
}
