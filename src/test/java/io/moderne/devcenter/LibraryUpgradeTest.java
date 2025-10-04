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

import static io.moderne.devcenter.SemverMeasure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class LibraryUpgradeTest implements RewriteTest {

    private static Stream<Arguments> jacksonVersions() {
        return Stream.of(
          Arguments.of("3.0", "2.12.3", Major),
          Arguments.of("2.16.0", "2.12.3", Minor),
          Arguments.of("2.12.4", "2.12.3", Patch),
          Arguments.of("2.12.4", "2.12.4", Completed),
          Arguments.of("2.12.4", "2.16.0", Completed)
        );
    }

    @MethodSource("jacksonVersions")
    @ParameterizedTest
    void minorUpgrade(String targetVersion, String currentVersion, SemverMeasure semverMeasure) {
        rewriteRun(
          spec ->
            spec
              .recipe(new LibraryUpgrade("Move Jackson",
                "com.fasterxml*", "*", targetVersion, null))
              .dataTable(UpgradesAndMigrations.Row.class, rows ->
                assertThat(rows).containsExactly(
                  new UpgradesAndMigrations.Row("Move Jackson",
                    semverMeasure.ordinal(), semverMeasure.name(), currentVersion)
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
                        <groupId>com.fasterxml.jackson.module</groupId>
                        <artifactId>jackson-module-parameter-names</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(currentVersion),
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <!--~~>--><dependency>
                        <groupId>com.fasterxml.jackson.module</groupId>
                        <artifactId>jackson-module-parameter-names</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(currentVersion)
          )
        );
    }
}
