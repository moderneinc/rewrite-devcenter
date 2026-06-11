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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.openrewrite.docker.Assertions.docker;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

/**
 * Not a behavioral test: a generator that runs each card against representative fixtures using the
 * <em>bundled</em> feed (i.e. real endoflife.date data) and writes the resulting
 * {@link EndOfLifeReport} rows to {@code samples/extracts} as NDJSON and CSV. It is skipped during a
 * normal {@code test} run and only executes for {@code ./gradlew generateSamples}, which sets the
 * {@code eol.generateSamples} system property.
 */
@EnabledIfSystemProperty(named = "eol.generateSamples", matches = "true")
class SampleExtractsTest implements RewriteTest {

    // Pinned so regenerated samples are stable against the checked-in feed snapshot.
    private static final String AS_OF = "2026-06-09";

    @Test
    void jvm() {
        write("jvm", collect(new DependencyEndOfLife("JVM dependency end of life", null, AS_OF, null, null, null),
                //language=xml
                pomXml(
                        """
                          <project>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0-SNAPSHOT</version>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot</artifactId>
                                    <version>2.7.18</version>
                                </dependency>
                            </dependencies>
                          </project>
                          """)));
    }

    @Test
    void npm() {
        write("npm", collect(new NpmDependencyEndOfLife("npm dependency end of life", null, AS_OF, null, null, null),
                //language=json
                json(
                        """
                          {
                            "name": "app",
                            "dependencies": { "express": "^4.18.2", "vue": "^2.7.0" }
                          }
                          """,
                        spec -> spec.path("package.json"))));
    }

    @Test
    void nuget() {
        write("nuget", collect(new NuGetDependencyEndOfLife("NuGet dependency end of life", null, AS_OF, null, null, null),
                //language=xml
                xml(
                        """
                          <Project Sdk="Microsoft.NET.Sdk">
                            <ItemGroup>
                              <PackageReference Include="bootstrap" Version="4.6.0" />
                            </ItemGroup>
                          </Project>
                          """,
                        spec -> spec.path("app.csproj"))));
    }

    @Test
    void runtime() {
        write("runtime", collect(new RuntimeEndOfLife("Runtime end of life", null, AS_OF, null, null, null, null),
                version(java("public class A {}"), 8),
                //language=json
                json(
                        """
                          { "name": "app", "engines": { "node": ">=16.0.0" } }
                          """,
                        spec -> spec.path("package.json")),
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
                text("3.8.18\n", spec -> spec.path(".python-version"))));
    }

    @Test
    void dockerImages() {
        write("docker", collect(new DockerImageEndOfLife("Docker base image end of life", null, AS_OF, null, null, null),
                docker(
                        """
                          FROM node:16.20-alpine AS build
                          FROM python:3.8-slim
                          """,
                        spec -> spec.path("Dockerfile"))));
    }

    private List<EndOfLifeReport.Row> collect(org.openrewrite.Recipe recipe,
                                              org.openrewrite.test.SourceSpecs... sources) {
        List<EndOfLifeReport.Row> rows = new ArrayList<>();
        rewriteRun(spec -> spec.recipe(recipe)
                .dataTable(EndOfLifeReport.Row.class, rows::addAll), sources);
        return rows;
    }

    private static void write(String name, List<EndOfLifeReport.Row> rows) {
        // Sort so regenerated samples are stable regardless of data-table collection order.
        rows.sort(Comparator.comparing(EndOfLifeReport.Row::getEcosystem)
                .thenComparing(EndOfLifeReport.Row::getName)
                .thenComparing(EndOfLifeReport.Row::getVersion)
                .thenComparing(EndOfLifeReport.Row::getStatus));
        Path dir = Paths.get(System.getProperty("eol.samplesDir", "samples/extracts"));
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name + "-end-of-life.ndjson"), ndjson(rows).getBytes(StandardCharsets.UTF_8));
            Files.write(dir.resolve(name + "-end-of-life.csv"), csv(rows).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write sample extract: " + name, e);
        }
    }

    private static String ndjson(List<EndOfLifeReport.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (EndOfLifeReport.Row r : rows) {
            sb.append("{\"ecosystem\":").append(quote(r.getEcosystem()))
                    .append(",\"kind\":").append(quote(r.getKind()))
                    .append(",\"name\":").append(quote(r.getName()))
                    .append(",\"version\":").append(quote(r.getVersion()))
                    .append(",\"product\":").append(quote(r.getProduct()))
                    .append(",\"cycle\":").append(quote(r.getCycle()))
                    .append(",\"eolDate\":").append(quote(r.getEolDate()))
                    .append(",\"status\":").append(quote(r.getStatus()))
                    .append("}\n");
        }
        return sb.toString();
    }

    private static String csv(List<EndOfLifeReport.Row> rows) {
        StringBuilder sb = new StringBuilder("ecosystem,kind,name,version,product,cycle,eolDate,status\n");
        for (EndOfLifeReport.Row r : rows) {
            sb.append(cell(r.getEcosystem())).append(',').append(cell(r.getKind())).append(',')
                    .append(cell(r.getName())).append(',').append(cell(r.getVersion())).append(',')
                    .append(cell(r.getProduct())).append(',').append(cell(r.getCycle())).append(',')
                    .append(cell(r.getEolDate())).append(',').append(cell(r.getStatus())).append('\n');
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String cell(String s) {
        return s.contains(",") || s.contains("\"") ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
