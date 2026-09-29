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

    @Test
    void zipSlipIsReportedUnderA01() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(
              new SecurityIssues.Row(0, "Remediate OWASP A01:2021 Broken access control")
            )),
          //language=java
          java(
            """
              import java.io.File;
              import java.io.FileOutputStream;
              import java.util.zip.ZipEntry;

              public class ZipTest {
                  public void m1(ZipEntry entry, File dir) throws Exception {
                      String name = entry.getName();
                      File file = new File(dir, name);
                      FileOutputStream os = new FileOutputStream(file);
                  }
              }
              """,
            """
              import java.io.File;
              import java.io.FileOutputStream;
              import java.io.IOException;
              import java.util.zip.ZipEntry;

              public class ZipTest {
                  public void m1(ZipEntry entry, File dir) throws Exception {
                      String name = entry.getName();
                      File file = new File(dir, name);
                      if (!file.toPath().normalize().startsWith(dir.toPath().normalize())) {
                          throw new IOException("Bad zip entry");
                      }
                      FileOutputStream os = new FileOutputStream(file);
                  }
              }
              """
          )
        );
    }

    @Test
    void regularExpressionDenialOfServiceIsReportedUnderA03() {
        rewriteRun(spec -> spec.dataTable(SecurityIssues.Row.class, rows ->
            assertThat(rows).containsExactly(
              new SecurityIssues.Row(2, "Remediate OWASP A03:2021 Injection")
            )),
          //language=java
          java(
            """
              import java.util.regex.Pattern;

              class Test {
                  private static final Pattern testRe = Pattern.compile("(\\\\?.)*");
              }
              """,
            """
              import java.util.regex.Pattern;

              class Test {
                  private static final Pattern testRe = Pattern.compile(".*");
              }
              """
          )
        );
    }
}
