package io.moderne.devcenter;

import io.moderne.devcenter.table.SecurityIssues;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

public class ReportAsSecurityIssuesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite")
          .scanYamlResources()
          .build()
          // In src/main/resources/default.yml
          .activateRecipes("io.moderne.devcenter.SecurityStarter"));
    }

    @Test
    void reportSecret() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(
              new SecurityIssues.Row("Find secrets")
            )),
          //language=java
          java(
            """
              class Test {
                String secret = "gho_16C7e42F292c6912E7710c838347Ae178B4a";
              }
              """,
            """
              class Test {
                String secret = /*~~(GitHub)~~>*/"gho_16C7e42F292c6912E7710c838347Ae178B4a";
              }
              """
          )
        );
    }
}
