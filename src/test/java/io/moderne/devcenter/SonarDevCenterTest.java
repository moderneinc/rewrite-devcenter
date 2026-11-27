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
package io.moderne.devcenter;

import io.moderne.devcenter.table.SecurityIssues;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class SonarDevCenterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        // In src/main/resources/META-INF/rewrite/sonar-devcenter.yml
        spec.recipeFromResources("io.moderne.devcenter.SonarIssues")
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"));
    }

    @Test
    void valid() throws Exception {
        Recipe recipe = Environment.builder()
          .scanRuntimeClasspath("org.openrewrite")
          .scanYamlResources()
          .build()
          .activateRecipes("io.moderne.devcenter.SonarDevCenter");
        assertThat(DevCenter.isDevCenter(recipe.getDescriptor())).isTrue();

        var devCenter = new DevCenter(recipe);
        devCenter.validate();

        assertThat(devCenter.getUpgradesAndMigrations()).isEmpty();
        DevCenter.Card security = devCenter.getSecurity();
        assertThat(security).isNotNull();
        assertThat(security.getName()).isEqualTo("Sonar issues by severity");
        assertThat(security.getFixRecipeId()).isEqualTo("io.moderne.devcenter.FixSonarIssues");
        assertThat(security.getMeasures().stream())
          .map(DevCenterMeasure::getName)
          .containsExactly("Sonar blocker issues", "Sonar critical issues", "Sonar major issues",
            "Sonar minor issues", "Sonar info issues");
        assertThat(recipe.getDescription()).contains("**" + security.getName() + "**");
    }

    @DocumentExample
    @Test
    void fallThroughIsReportedAsBlocker() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(new SecurityIssues.Row(0, "Sonar blocker issues"))),
          //language=java
          java(
            """
              class Test {
                  int f(int i) {
                      int x = 0;
                      switch (i) {
                          case 1:
                              x = 1;
                          case 2:
                              x = 2;
                              break;
                          default:
                              break;
                      }
                      return x;
                  }
              }
              """,
            """
              class Test {
                  int f(int i) {
                      int x = 0;
                      switch (i) {
                          case 1:
                              x = 1;
                              break;
                          case 2:
                              x = 2;
                              break;
                          default:
                              break;
                      }
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void publicTestIsReportedAsInfo() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(new SecurityIssues.Row(4, "Sonar info issues"))),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class AdditionTest {
                  @Test
                  public void adds() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              class AdditionTest {
                  @Test
                  void adds() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void ruleOfTwoSeveritiesIsCountedUnderTheHigherOne() {
        // HiddenField is in both the blocker and the major Sonar rules of rewrite-static-analysis.
        // The blocker rules run first and fix the issue, so the major rules find nothing left to count.
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(new SecurityIssues.Row(0, "Sonar blocker issues"))),
          //language=java
          java(
            """
              class Test {
                  private int count;

                  int add(int count) {
                      return this.count + count;
                  }
              }
              """,
            """
              class Test {
                  private int count;

                  int add(int count1) {
                      return this.count + count1;
                  }
              }
              """
          )
        );
    }

    @Test
    void sonarDevCenterReportsTheCardOfItsSonarIssues() {
        rewriteRun(spec -> spec
            .recipeFromResources("io.moderne.devcenter.SonarDevCenter")
            .dataTable(SecurityIssues.Row.class, rows ->
              assertThat(rows).containsExactly(new SecurityIssues.Row(4, "Sonar info issues"))),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class SubtractionTest {
                  @Test
                  public void subtracts() {
                      assertEquals(1, 2 - 1);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              class SubtractionTest {
                  @Test
                  void subtracts() {
                      assertEquals(1, 2 - 1);
                  }
              }
              """
          )
        );
    }
}
