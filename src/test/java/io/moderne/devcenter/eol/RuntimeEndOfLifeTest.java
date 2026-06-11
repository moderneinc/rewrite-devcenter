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
package io.moderne.devcenter.eol;

import io.moderne.devcenter.eol.table.EndOfLifeReport;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.toml.Assertions.toml;
import static org.openrewrite.xml.Assertions.xml;

class RuntimeEndOfLifeTest implements RewriteTest {

    private RuntimeEndOfLife card() {
        // runtime = null tracks every runtime; the per-runtime narrowing is exercised by narrowedCard().
        return new RuntimeEndOfLife("Runtime end of life", null, "2026-05-28",
                "/feeds/test-feed.yaml", null, null, null);
    }

    @Test
    void classifiesJavaRuntimeFromMarker() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Java", "runtime", "Java", "8", "openjdk", "8",
                                        "2022-03-31", EolStatus.EndOfLife.name()))),
                version(java("public class A {}"), 8)
        );
    }

    @Test
    void classifiesNodeRuntimeFromEngines() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Node.js", "runtime", "Node.js", "16.0.0", "nodejs", "16",
                                        "2023-09-11", EolStatus.EndOfLife.name()))),
                //language=json
                json(
                        """
                          {
                            "name": "app",
                            "engines": { "node": ">=16.0.0" }
                          }
                          """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @DocumentExample
    @Test
    void classifiesDotNetRuntimeFromTargetFramework() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        ".NET", "runtime", ".NET", "6.0", "dotnet", "6.0",
                                        "2024-11-12", EolStatus.EndOfLife.name())))
                        // Per-runtime cards carry the runtime in the title, so the value is the raw version.
                        .dataTable(UpgradesAndMigrations.Row.class, rows ->
                                assertThat(rows).containsExactly(new UpgradesAndMigrations.Row(
                                        "Runtime end of life",
                                        EolStatus.EndOfLife.ordinal(),
                                        EolStatus.EndOfLife.name(),
                                        "6.0"))),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <PropertyGroup>
                              <TargetFramework>net6.0</TargetFramework>
                            </PropertyGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj")
                )
        );
    }

    @Test
    void classifiesPlatformSpecificDotNetTargetFramework() {
        // OS-targeted TFMs (WPF/WinForms/MAUI) carry a platform moniker; the version must still be
        // reduced to its release cycle ("net6.0-windows" -> .NET 6.0) rather than left unmatched.
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        ".NET", "runtime", ".NET", "6.0", "dotnet", "6.0",
                                        "2024-11-12", EolStatus.EndOfLife.name()))),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <PropertyGroup>
                              <TargetFramework>net6.0-windows</TargetFramework>
                            </PropertyGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj")
                )
        );
    }

    @Test
    void classifiesMultiTargetedDotNetAndIgnoresNetstandard() {
        // <TargetFrameworks> (plural) is a semicolon-separated list; the runnable runtime is reported
        // while the portability target (netstandard) produces no runtime.
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        ".NET", "runtime", ".NET", "6.0", "dotnet", "6.0",
                                        "2024-11-12", EolStatus.EndOfLife.name()))),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <PropertyGroup>
                              <TargetFrameworks>net6.0;netstandard2.0</TargetFrameworks>
                            </PropertyGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj")
                )
        );
    }

    @Test
    void classifiesPythonRuntimeFromRuntimeTxt() {
        // Buildpack-style runtime.txt carries a "python-" prefix before the version.
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Python", "runtime", "Python", "3.8.18", "python", "3.8",
                                        "2024-10-07", EolStatus.EndOfLife.name()))),
                text("python-3.8.18\n", spec -> spec.path("runtime.txt"))
        );
    }

    @Test
    void classifiesPythonRuntimeFromPythonVersionFile() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Python", "runtime", "Python", "3.8.18", "python", "3.8",
                                        "2024-10-07", EolStatus.EndOfLife.name()))),
                text("3.8.18\n", spec -> spec.path(".python-version"))
        );
    }

    @Test
    void classifiesPythonRuntimeFromPyprojectRequiresPython() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "Python", "runtime", "Python", "3.8", "python", "3.8",
                                        "2024-10-07", EolStatus.EndOfLife.name()))),
                //language=toml
                toml(
                        """
                          [project]
                          name = "app"
                          requires-python = ">=3.8"
                          """,
                        spec -> spec.path("pyproject.toml")
                )
        );
    }

    @Test
    void runtimeOptionNarrowsToOneEcosystem() {
        RuntimeEndOfLife dotNetOnly = new RuntimeEndOfLife("Runtime end of life", null, "2026-05-28",
                "/feeds/test-feed.yaml", null, RuntimeType.DotNet, null);
        rewriteRun(
                spec -> spec
                        .recipe(dotNetOnly)
                        // Only the .NET runtime is reported; the Node engine in the same run is ignored.
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        ".NET", "runtime", ".NET", "6.0", "dotnet", "6.0",
                                        "2024-11-12", EolStatus.EndOfLife.name()))),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <PropertyGroup>
                              <TargetFramework>net6.0</TargetFramework>
                            </PropertyGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj")),
                //language=json
                json(
                        """
                          { "name": "app", "engines": { "node": ">=16.0.0" } }
                          """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void fixRecipeDefaultsForJavaAndIsOverridable() {
        // The Java and .NET runtime cards default to an upgrade to the current major...
        assertThat(new RuntimeEndOfLife("c", null, null, null, null, RuntimeType.Java, null).getFixRecipeId())
                .isEqualTo("org.openrewrite.java.migrate.UpgradeToJava25");
        assertThat(new RuntimeEndOfLife("c", null, null, null, null, RuntimeType.DotNet, null).getFixRecipeId())
                .isEqualTo("OpenRewrite.Recipes.CSharp.Migration.Dotnet.Net10.UpgradeToDotNet10");
        // ...Node.js/Python are report-only unless given a fix recipe...
        assertThat(new RuntimeEndOfLife("c", null, null, null, null, RuntimeType.NodeJs, null).getFixRecipeId())
                .isNull();
        // ...and an explicit fixRecipe always wins.
        assertThat(new RuntimeEndOfLife("c", null, null, null, null, RuntimeType.Java, "com.example.UpgradeJava")
                .getFixRecipeId()).isEqualTo("com.example.UpgradeJava");
    }
}
