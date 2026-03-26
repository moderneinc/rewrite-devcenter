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
package io.moderne.devcenter.table;

import io.moderne.devcenter.ParentPomUpgrade;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static io.moderne.devcenter.SemverMeasure.Major;
import static io.moderne.devcenter.SemverMeasure.Minor;
import static io.moderne.devcenter.table.UpgradesAndMigrations.bestRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradesAndMigrationsTest implements RewriteTest {

    @Test
    void leastOrdinalRetained() {
        rewriteRun(
          spec -> spec
            .recipe(new ParentPomUpgrade("Spring Boot", "org.springframework.boot",
              "spring-boot-parent", "3.4.5", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Spring Boot", Major.ordinal(), "Major", "2.7.0")
              )),
          pomXml(pom("2.7.0"), expected("2.7.0"),
            spec -> spec.path(Path.of("module1/pom.xml"))),
          pomXml(pom("3.2.0"), expected("3.2.0"),
            spec -> spec.path(Path.of("module2/pom.xml")))
        );
    }

    @Test
    void leastVersionRetainedAtSameOrdinal() {
        rewriteRun(
          spec -> spec
            .recipe(new ParentPomUpgrade("Spring Boot", "org.springframework.boot",
              "spring-boot-parent", "3.4.5", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Spring Boot", Minor.ordinal(), "Minor", "3.1.0")
              )),
          pomXml(pom("3.1.0"), expected("3.1.0"),
            spec -> spec.path(Path.of("module1/pom.xml"))),
          pomXml(pom("3.2.0"), expected("3.2.0"),
            spec -> spec.path(Path.of("module2/pom.xml")))
        );
    }

    @Test
    void nullVersionDoesNotCauseNpe() {
        var nullVersion = new UpgradesAndMigrations.Row("card", Minor.ordinal(), "Minor", null);
        var withVersion = new UpgradesAndMigrations.Row("card", Minor.ordinal(), "Minor", "2.2.0");

        assertThat(bestRow(nullVersion, withVersion)).isEqualTo(withVersion);
    }

    private static String pom(String version) {
        return """
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
          """.formatted(version);
    }

    private static String expected(String version) {
        return """
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
          """.formatted(version);
    }
}
