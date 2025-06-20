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

import io.moderne.devcenter.table.SecurityIssues;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class ReportAsSecurityIssuesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite")
          .scanYamlResources()
          .build()
          // In src/main/resources/original-security.yml
          .activateRecipes("io.moderne.devcenter.SecurityOriginalStarter"));
    }

    @DocumentExample
    @Test
    void reportSecret() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(
              new SecurityIssues.Row(0, "Find secrets")
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
