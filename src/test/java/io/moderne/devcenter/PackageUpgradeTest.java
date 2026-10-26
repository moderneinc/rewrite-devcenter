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

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.marker.Marker;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.List;

import static io.moderne.devcenter.PackageMarkers.*;
import static io.moderne.devcenter.SemverMeasure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class PackageUpgradeTest implements RewriteTest {

    @DocumentExample
    @Test
    void pypiPackage() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to LangChain 1.0", "langchain", "1.0.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to LangChain 1.0", Major.ordinal(), Major.name(), "0.3.27"))),
          project(pypi(List.of(pypiDependency("langchain", ">=0.3", "0.3.27"))))
        );
    }

    @Test
    void pypiNamesMatchTheWayPypiComparesThem() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to typing-extensions 4.15", "typing-extensions", "4.15.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to typing-extensions 4.15", Minor.ordinal(), Minor.name(), "4.12.2"))),
          project(pypi(List.of(), pypiResolved("Typing_Extensions", "4.12.2")))
        );
    }

    @Test
    void pypiConstraintWithoutResolution() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to Pydantic 2.13", "pydantic", "2.13.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to Pydantic 2.13", Minor.ordinal(), Minor.name(), "2.11"))),
          project(pypi(List.of(pypiDependency("pydantic", ">=2.11,<3", null))))
        );
    }

    @Test
    void npmLowestDirectVersion() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to Angular 21", "@angular/*", "21.0.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to Angular 21", Major.ordinal(), Major.name(), "19.2.0"))),
          project(npm(
            List.of(npmDependency("@angular/core", "^21.0.0", "21.0.3")),
            List.of(npmDependency("@angular/cli", "^19.2.0", null), npmDependency("typescript", "^5.9.0", "5.9.3"))))
        );
    }

    @Test
    void nugetResolvedPackagesIncludingTransitive() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to EF Core 10", "Microsoft.EntityFrameworkCore*", "10.0.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to EF Core 10", Major.ordinal(), Major.name(), "9.0.4"))),
          project(nuget(
            List.of(nugetReference("Microsoft.EntityFrameworkCore.SqlServer", "10.0.0", "10.0.0")),
            nugetResolved("Microsoft.EntityFrameworkCore.SqlServer", "10.0.0"),
            nugetResolved("microsoft.entityframeworkcore.relational", "9.0.4")))
        );
    }

    @Test
    void goModule() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to gRPC 1.75", "google.golang.org/grpc", "1.75.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to gRPC 1.75", Completed.ordinal(), Completed.name(), "1.76.1"))),
          project(go(goRequire("google.golang.org/grpc", "v1.76.1", true)))
        );
    }

    @Test
    void targetBeforeOne() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageUpgrade("Move to go.uber.org/mock 0.6", "go.uber.org/mock", "0.6.0", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to go.uber.org/mock 0.6", Minor.ordinal(), Minor.name(), "0.5.2"))),
          project(go(goRequire("go.uber.org/mock", "v0.5.2", false)))
        );
    }

    @Test
    void noMatchingPackage() {
        rewriteRun(
          spec -> spec.recipe(new PackageUpgrade("Move to React 19", "react", "19.0.0", null)),
          project(npm(List.of(npmDependency("react-dom", "^19.0.0", "19.1.0")), List.of()))
        );
    }

    private static SourceSpecs project(Marker marker) {
        return java("class Test {}", spec -> spec.markers(marker));
    }
}
