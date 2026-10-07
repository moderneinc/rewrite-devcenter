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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static io.moderne.devcenter.SemverMeasure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

class LibraryUpgradeTest implements RewriteTest {

    @DocumentExample
    @Test
    void lowestVersionOfTheBestMeasure() {
        rewriteRun(
          spec -> spec
            .recipe(new LibraryUpgrade("Move to Spring Boot 3.5", "org.springframework.boot", "*", "3.5.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to Spring Boot 3.5", Minor.ordinal(), Minor.name(), "3.3.0"))),
          buildGradleWith(project(
            List.of(dependency("org.springframework.boot", "spring-boot", "3.5.0")),
            List.of(dependency("org.springframework.boot", "spring-boot", "3.4.1")),
            List.of(dependency("org.springframework.boot", "spring-boot", "3.3.0")),
            List.of(dependency("org.springframework.boot", "spring-boot", "3.5.1"))
          ))
        );
    }

    @Test
    void springBootArtifactsSharingOneVersion() {
        ResolvedDependency springBoot = dependency("org.springframework.boot", "spring-boot", "3.1.2");
        ResolvedDependency starter = dependency("org.springframework.boot", "spring-boot-starter", "3.1.2",
          springBoot, dependency("org.springframework.boot", "spring-boot-autoconfigure", "3.1.2", springBoot));
        ResolvedDependency web = dependency("org.springframework.boot", "spring-boot-starter-web", "3.1.2",
          starter, dependency("org.springframework.boot", "spring-boot-starter-json", "3.1.2", starter));
        var roots = List.of(web, dependency("commons-lang", "commons-lang", "2.6"));
        rewriteRun(
          spec -> spec
            .recipe(new LibraryUpgrade("Move to Spring Boot 4.0", "org.springframework.boot", "*", "4.0.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to Spring Boot 4.0", Major.ordinal(), Major.name(), "3.1.2"))),
          buildGradleWith(project(roots, roots))
        );
    }

    private static SourceSpecs buildGradleWith(GradleProject project) {
        //language=groovy
        return buildGradle(
          """
            plugins {
                id "java"
            }
            """,
          spec -> spec.markers(project)
        );
    }

    @SafeVarargs
    private static GradleProject project(List<ResolvedDependency>... directResolvedByConfiguration) {
        var configurations = new LinkedHashMap<String, GradleDependencyConfiguration>();
        for (List<ResolvedDependency> directResolved : directResolvedByConfiguration) {
            String name = "configuration" + configurations.size();
            configurations.put(name, GradleDependencyConfiguration.builder()
              .name(name)
              .extendsFrom(List.of())
              .requested(List.of())
              .directResolved(directResolved)
              .build());
        }
        return GradleProject.builder().nameToConfiguration(configurations).build();
    }

    private static ResolvedDependency dependency(String groupId, String artifactId, String version,
                                                 ResolvedDependency... dependencies) {
        return ResolvedDependency.builder()
          .gav(new ResolvedGroupArtifactVersion(null, groupId, artifactId, version, null))
          .requested(Dependency.builder().gav(new GroupArtifactVersion(groupId, artifactId, version)).build())
          .dependencies(List.of(dependencies))
          .build();
    }

    private static Stream<Arguments> jacksonVersions() {
        return Stream.of(
          Arguments.of("3.0", "2.12.3", Major),
          Arguments.of("2.16.0", "2.12.3", Minor),
          Arguments.of("2.12.4", "2.12.3", Patch),
          Arguments.of("2.12.4", "2.12.4", Completed),
          Arguments.of("2.12.4", "2.16.0", Completed)
        );
    }

    @MethodSource("jacksonVersions")
    @ParameterizedTest
    void minorUpgrade(String targetVersion, String currentVersion, SemverMeasure semverMeasure) {
        rewriteRun(
          spec ->
            spec
              .recipe(new LibraryUpgrade("Move Jackson",
                "com.fasterxml*", "*", targetVersion, null))
              .dataTable(UpgradesAndMigrations.Row.class, rows ->
                assertThat(rows).containsExactly(
                  new UpgradesAndMigrations.Row("Move Jackson",
                    semverMeasure.ordinal(), semverMeasure.name(), currentVersion)
                )),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.module</groupId>
                        <artifactId>jackson-module-parameter-names</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
              </project>
              """.formatted(currentVersion)
          )
        );
    }
}
