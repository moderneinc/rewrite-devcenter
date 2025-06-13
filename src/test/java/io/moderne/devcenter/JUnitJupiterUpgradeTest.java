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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.function.Consumer;

import static io.moderne.devcenter.JUnitJupiterUpgrade.Measure.Completed;
import static io.moderne.devcenter.JUnitJupiterUpgrade.Measure.JUnit4;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class JUnitJupiterUpgradeTest implements RewriteTest {

    //language=java
    SourceSpecs junit4 = java(
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
    SourceSpecs junit5 = java(
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

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JUnitJupiterUpgrade());
    }

    @Test
    void junit4() {
        rewriteRun(
          assertUpgradeStatus(JUnit4, "JUnit 4", "JUnit 4"),
          junit4
        );
    }

    @Test
    void junit5() {
        rewriteRun(
          assertUpgradeStatus(Completed, Completed.name(), "JUnit 5"),
          junit5
        );
    }

    @Test
    void bothJUnit4And5() {
        rewriteRun(
          assertUpgradeStatus(JUnit4, "JUnit 4", "JUnit 4"),
          junit4,
          junit5
        );
    }

    private static Consumer<RecipeSpec> assertUpgradeStatus(JUnitJupiterUpgrade.Measure JUnit4, String measureName, String dependencyVersion) {
        return spec -> spec.dataTable(UpgradesAndMigrations.Row.class, rows ->
          assertThat(rows).containsExactly(
            new UpgradesAndMigrations.Row("Move to JUnit 5",
              JUnit4.ordinal(), measureName, dependencyVersion)
          ));
    }
}
