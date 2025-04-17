package io.moderne.devcenter;

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.function.Consumer;

import static io.moderne.devcenter.JUnitUpgrade.Measures.Completed;
import static io.moderne.devcenter.JUnitUpgrade.Measures.JUnit4;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

public class JUnitUpgradeTest implements RewriteTest {

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
        spec.recipe(new JUnitUpgrade());
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

    private static Consumer<RecipeSpec> assertUpgradeStatus(JUnitUpgrade.Measures JUnit4, String measureName, String dependencyVersion) {
        return spec -> spec.dataTable(UpgradesAndMigrations.Row.class, rows ->
          assertThat(rows).containsExactly(
            new UpgradesAndMigrations.Row("Move to JUnit 5",
              JUnit4.ordinal(), measureName, dependencyVersion)
          ));
    }
}
