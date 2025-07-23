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
          // In src/main/resources/META-INF/rewrite/devcenter-starter.yml
          .activateRecipes("io.moderne.devcenter.SecurityStarter"));
    }

    @DocumentExample
    @Test
    void reportSecret() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(
              new SecurityIssues.Row(3, "Remediate OWASP A08:2021 Software and data integrity failures")
            )),
          //language=java
          java(
            """
              import java.io.File;

              class Foo {
                void bar() {
                  File tmp = File.createTempFile("prefix", "suffix");
                }
              }
              """,
            """
              import java.io.File;
              import java.nio.file.Files;

              class Foo {
                void bar() {
                  File tmp = Files.createTempFile("prefix", "suffix").toFile();
                }
              }
              """
          )
        );
    }
}
