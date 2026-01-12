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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class JUnitJupiterUpgradeTest implements RewriteTest {

    //language=java
    SourceSpecs junit3Source = java(
      """
        import junit.framework.TestCase;
        class TestWith3 extends TestCase {
          void test() {
              fail("Not yet implemented");
          }
        }
        """,
      """
        import junit.framework.TestCase;
        class /*~~>*/TestWith3 extends /*~~>*/TestCase {
          void test() {
              fail("Not yet implemented");
          }
        }
        """
    );

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
    SourceSpecs junit6Source = java(
      """
        import org.junit.jupiter.api.Test;
        class TestWith6 {
          @Test
          void test() {
          }
        }
        """,
      """
        import org.junit.jupiter.api.Test;
        class TestWith6 {
          /*~~>*/@Test
          void test() {
          }
        }
        """,
      spec -> spec.markers(JavaSourceSet.build("test",
        JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-6"))));

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JUnitJupiterUpgrade());
    }

    @Test
    void junit3() {
        rewriteRun(
          spec -> assertUpgradeStatus(JUnitJupiterUpgrade.Measure.JUnit3, "JUnit 3", spec)
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4")),
          junit3Source
        );
    }

    @Test
    void junit4() {
        rewriteRun(
          spec -> assertUpgradeStatus(JUnitJupiterUpgrade.Measure.JUnit4, "JUnit 4", spec),
          junit4Source
        );
    }

    @Test
    void junit5() {
        rewriteRun(
          spec -> assertUpgradeStatus(JUnitJupiterUpgrade.Measure.JUnit5, "JUnit 5", spec),
          junit5Source
        );
    }

    @Test
    void junit6() {
        rewriteRun(
          spec -> assertUpgradeStatus(JUnitJupiterUpgrade.Measure.Completed, "JUnit 6", spec),
          junit6Source
        );
    }

    @Test
    void bothJUnit4And5() {
        rewriteRun(
          spec -> assertUpgradeStatus(JUnitJupiterUpgrade.Measure.JUnit4, "JUnit 4", spec),
          junit4Source,
          junit5Source
        );
    }

    private static RecipeSpec assertUpgradeStatus(JUnitJupiterUpgrade.Measure measure, String dependencyVersion, RecipeSpec spec) {
        return spec.dataTable(UpgradesAndMigrations.Row.class, rows ->
          assertThat(rows)
            .containsExactly(
              new UpgradesAndMigrations.Row(
                "Move to JUnit 6",
                measure.ordinal(),
                measure.getName(),
                dependencyVersion)
            ));
    }
}
