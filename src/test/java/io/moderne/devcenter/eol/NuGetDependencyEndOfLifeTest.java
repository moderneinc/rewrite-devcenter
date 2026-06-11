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
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.xml.Assertions.xml;

class NuGetDependencyEndOfLifeTest implements RewriteTest {

    private NuGetDependencyEndOfLife card() {
        return new NuGetDependencyEndOfLife("NuGet dependency end of life", null, "2026-05-28",
                "/feeds/test-feed.yaml", null, null);
    }

    @DocumentExample
    @Test
    void classifiesPackageReferenceByDeclaredVersion() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "NuGet",
                                        "dependency",
                                        "Newtonsoft.Json",
                                        "12.0.3",
                                        "newtonsoft-json",
                                        "12",
                                        "2020-01-01",
                                        EolStatus.EndOfLife.name()))),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <ItemGroup>
                              <PackageReference Include="Newtonsoft.Json" Version="12.0.3" />
                            </ItemGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj")
                )
        );
    }

    @Test
    void readsVersionFromChildElementRatherThanAttribute() {
        // MSBuild also accepts the version as a nested <Version> element instead of an attribute.
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "NuGet",
                                        "dependency",
                                        "Newtonsoft.Json",
                                        "12.0.3",
                                        "newtonsoft-json",
                                        "12",
                                        "2020-01-01",
                                        EolStatus.EndOfLife.name()))),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <ItemGroup>
                              <PackageReference Include="Newtonsoft.Json">
                                <Version>12.0.3</Version>
                              </PackageReference>
                            </ItemGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj")
                )
        );
    }
}
