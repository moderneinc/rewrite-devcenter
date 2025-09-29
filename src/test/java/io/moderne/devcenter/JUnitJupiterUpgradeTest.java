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
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.function.Consumer;

import static io.moderne.devcenter.JUnitJupiterUpgrade.Measure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class JUnitJupiterUpgradeTest implements RewriteTest {

    //language=java
    SourceSpecs junit4Source = java(
      """
        import org.junit.Test;
        class TestWith4 {
          @Test
          void test() {
          }
        }
        """,
      """
        import org.junit.Test;
        class TestWith4 {
          /*~~>*/@Test
          void test() {
          }
        }
        """
    );

    //language=java
    SourceSpecs junit5Source = java(
      """
        import org.junit.jupiter.api.Test;
        class TestWith5 {
          @Test
          void test() {
          }
        }
        """,
      """
        import org.junit.jupiter.api.Test;
        class TestWith5 {
          /*~~>*/@Test
          void test() {
          }
        }
        """
    );

    //language=java
    SourceSpecs junit6Source =
      java(
        """
          import org.junit.jupiter.api.Test;
          class TestWith5 {
            @Test
            void test() {
            }
          }
          """,
        """
          import org.junit.jupiter.api.Test;
          class TestWith5 {
            /*~~>*/@Test
            void test() {
            }
          }
          """,
        spec -> spec.markers(
          JavaSourceSet.build("test", JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), "junit-jupiter-api"))));

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JUnitJupiterUpgrade());
    }

    @Test
    void junit4() {
        rewriteRun(
          assertUpgradeStatus(JUnit4, "JUnit 4"),
          junit4Source
        );
    }

    @Test
    void junit5() {
        rewriteRun(
          assertUpgradeStatus(JUnit5, "JUnit 5"),
          junit5Source
        );
    }

    @Test
    void junit6() {
        rewriteRun(
          assertUpgradeStatus(Completed, "JUnit 6"),
          mavenProject(
            "project",
            srcTestJava(junit6Source),
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>6.0.0-RC3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void bothJUnit4And5() {
        rewriteRun(
          assertUpgradeStatus(JUnit4, "JUnit 4"),
          junit4Source,
          junit5Source
        );
    }

    private static Consumer<RecipeSpec> assertUpgradeStatus(JUnitJupiterUpgrade.Measure measure, String dependencyVersion) {
        return spec -> spec.dataTable(UpgradesAndMigrations.Row.class, rows ->
          assertThat(rows)
            .containsExactly(
              new UpgradesAndMigrations.Row(
                "Move to JUnit 5",
                measure.ordinal(),
                measure.getName(),
                dependencyVersion)
            ));
    }
}
