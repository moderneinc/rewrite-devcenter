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

import static io.moderne.devcenter.KotlinVersionUpgrade.Measure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class KotlinVersionUpgradeTest implements RewriteTest {

    private static Stream<Arguments> kotlinVersions() {
        return Stream.of(
          Arguments.of("2.1", "1.3.72", Kotlin14Plus),
          Arguments.of("2.1", "1.5.31", Kotlin14Plus),
          Arguments.of("2.1", "1.7.22", Kotlin16Plus),
          Arguments.of("2.1", "1.9.24", Kotlin18Plus),
          Arguments.of("2.1", "2.0.21", Kotlin20Plus),
          Arguments.of("2.1", "2.1.0", Completed),
          Arguments.of("2.1", "2.2.0", Completed),
          Arguments.of("2.0", "1.9.24", Kotlin18Plus),
          Arguments.of("2.0", "2.0.0", Completed)
        );
    }

    @MethodSource("kotlinVersions")
    @ParameterizedTest
    void detectsKotlinVersion(String targetVersion, String currentVersion, KotlinVersionUpgrade.Measure measure) {
        var recipe = new KotlinVersionUpgrade(targetVersion, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Kotlin " + targetVersion,
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
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(currentVersion),
            spec -> spec.after(after -> {
                assertThat(after).isNotNull();
                return after;
            })
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of("1.6", List.of(Kotlin14Plus, Completed)),
          Arguments.of("2.0", List.of(Kotlin14Plus, Kotlin16Plus, Kotlin18Plus, Completed)),
          Arguments.of("2.1", List.of(Kotlin14Plus, Kotlin16Plus, Kotlin18Plus, Kotlin20Plus, Completed)),
          Arguments.of("2.2", List.of(Kotlin14Plus, Kotlin16Plus, Kotlin18Plus, Kotlin20Plus, Kotlin21Plus, Completed))
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(String targetVersion,
                                                      List<KotlinVersionUpgrade.Measure> expectedMeasures) {
        var recipe = new KotlinVersionUpgrade(targetVersion, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);
    }
}
