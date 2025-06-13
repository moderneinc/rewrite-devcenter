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

import static io.moderne.devcenter.SemverMeasure.Completed;
import static io.moderne.devcenter.SemverMeasure.Major;
import static io.moderne.devcenter.SemverMeasure.Minor;
import static io.moderne.devcenter.SemverMeasure.Patch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class ParentPomUpgradeTest implements RewriteTest {

    private static Stream<Arguments> springBootParentVersions() {
        return Stream.of(
          Arguments.of("3.4.5", "2.7.16", Major),
          Arguments.of("3.4.5", "3.2.9", Minor),
          Arguments.of("3.4.5", "3.4.3", Patch),
          Arguments.of("3.4.5", "3.4.5", Completed),
          Arguments.of("3.4.5", "3.5.0", Completed)
        );
    }

    @ParameterizedTest
    @MethodSource("springBootParentVersions")
    void minorUpgrade(String targetVersion, String currentVersion, SemverMeasure semverMeasure) {
        rewriteRun(
          spec ->
            spec
              .recipe(new ParentPomUpgrade(
                "Move Spring Boot Parent POM",
                "org.springframework.boot",
                "spring-boot-parent",
                targetVersion,
                null))
              .dataTable(
                UpgradesAndMigrations.Row.class, rows ->
                  assertThat(rows).containsExactly(
                    new UpgradesAndMigrations.Row(
                      "Move Spring Boot Parent POM",
                      semverMeasure.ordinal(), semverMeasure.name(), currentVersion)
                  )),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                     <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-parent</artifactId>
                    <version>%s</version>
                </parent>
              </project>
              """.formatted(currentVersion),
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                     <!--~~>--><parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-parent</artifactId>
                    <version>%s</version>
                </parent>
              </project>
              """.formatted(currentVersion)
          )
        );
    }
}
