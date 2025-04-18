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

import java.util.stream.Stream;

import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Completed;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java8Plus;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

public class JavaVersionUpgradeTest implements RewriteTest {

    private static Stream<Arguments> javaVersions() {
        return Stream.of(
          Arguments.of(21, 8, Java8Plus),
          Arguments.of(17, 17, Completed),
          Arguments.of(21, 21, Completed),
          Arguments.of(21, 24, Completed)
        );
    }

    @ParameterizedTest
    @MethodSource("javaVersions")
    void java8(int targetVersion, int actualVersion, JavaVersionUpgrade.Measure measure) {
        rewriteRun(
          spec -> spec
            .recipe(new JavaVersionUpgrade(targetVersion))
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Java " + targetVersion,
                  measure.ordinal(), measure.getDisplayName(), Integer.toString(actualVersion))
              )),
          version(
            //language=java
            java("class Test {}"),
            actualVersion
          )
        );
    }
}
