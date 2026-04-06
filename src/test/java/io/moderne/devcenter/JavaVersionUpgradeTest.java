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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Completed;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java11Plus;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java17Plus;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java21Plus;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java8Plus;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class JavaVersionUpgradeTest implements RewriteTest {

    private static Stream<Arguments> javaVersions() {
        return Stream.of(
          Arguments.of(21, 8, Java8Plus),
          Arguments.of(17, 17, Completed),
          Arguments.of(21, 21, Completed),
          Arguments.of(21, 24, Completed)
        );
    }

    @MethodSource("javaVersions")
    @ParameterizedTest
    void java8(int targetVersion, int actualVersion, JavaVersionUpgrade.Measure measure) {
        UpgradeMigrationCard recipe = new JavaVersionUpgrade(targetVersion, null);
        rewriteRun(
          spec -> spec
            .recipe(recipe)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Java " + targetVersion,
                  recipe.ordinal(measure), measure.getName(), Integer.toString(actualVersion))
              )),
          version(
            //language=java
            java("class Test {}"),
            actualVersion
          )
        );
    }

    private static Stream<Arguments> versionAndMeasures() {
        return Stream.of(
          Arguments.of(8, List.of(Completed), 0),
          Arguments.of(11, List.of(Java8Plus, Completed), 1),
          Arguments.of(17, List.of(Java8Plus, Java11Plus, Completed), 2),
          Arguments.of(18, List.of(Java8Plus, Java11Plus, Java17Plus, Completed), 3),
          Arguments.of(21, List.of(Java8Plus, Java11Plus, Java17Plus, Completed), 3),
          Arguments.of(24, List.of(Java8Plus, Java11Plus, Java17Plus, Java21Plus, Completed), 4)
        );
    }

    @MethodSource("versionAndMeasures")
    @ParameterizedTest
    void measuresShouldNotIncludeTargetVersionOrAbove(int targetVersion,
                                                      List<JavaVersionUpgrade.Measure> expectedMeasures,
                                                      int expectedCompletedOrdinal) {
        UpgradeMigrationCard recipe = new JavaVersionUpgrade(targetVersion, null);
        assertThat(recipe.getMeasures())
          .containsExactlyElementsOf(expectedMeasures);

        assertThat(recipe.ordinal(JavaVersionUpgrade.Measure.Completed)).isEqualTo(expectedCompletedOrdinal);
    }
}
