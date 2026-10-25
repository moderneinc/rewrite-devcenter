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
import static io.moderne.devcenter.PackageMigration.Measure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class PackageMigrationTest implements RewriteTest {

    private static final PackageMigration XUNIT_TO_TUNIT = new PackageMigration(
      "Move from xUnit to TUnit", "xunit*", "TUnit*", null);

    private static final PackageMigration GOLANG_MOCK_TO_UBER_MOCK = new PackageMigration(
      "Move to go.uber.org/mock", "github.com/golang/mock", "go.uber.org/mock", null);

    @DocumentExample
    @Test
    void notStarted() {
        rewriteRun(
          spec -> spec
            .recipe(XUNIT_TO_TUNIT)
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from xUnit to TUnit", NotStarted.ordinal(), NotStarted.getName(), "xunit"))),
          project(nuget(List.of(
            nugetReference("xunit", "2.9.3", "2.9.3"),
            nugetReference("xunit.runner.visualstudio", "3.1.5", "3.1.5"))))
        );
    }

    @Test
    void inProgress() {
        rewriteRun(
          spec -> spec
            .recipe(XUNIT_TO_TUNIT)
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from xUnit to TUnit", InProgress.ordinal(), InProgress.getName(), "xunit"))),
          project(nuget(List.of(
            nugetReference("TUnit", "1.0.0", "1.0.0"),
            nugetReference("xunit", "2.9.3", "2.9.3"))))
        );
    }

    @Test
    void completed() {
        rewriteRun(
          spec -> spec
            .recipe(XUNIT_TO_TUNIT)
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from xUnit to TUnit", Completed.ordinal(), Completed.getName(), "TUnit"))),
          project(nuget(List.of(nugetReference("TUnit", "1.0.0", "1.0.0"))))
        );
    }

    @Test
    void nugetNamesMatchCaseInsensitively() {
        rewriteRun(
          spec -> spec
            .recipe(XUNIT_TO_TUNIT)
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from xUnit to TUnit", Completed.ordinal(), Completed.getName(), "tunit"))),
          project(nuget(List.of(nugetReference("tunit", "1.0.0", "1.0.0"))))
        );
    }

    @Test
    void transitiveNuGetPackagesDoNotCount() {
        rewriteRun(
          spec -> spec
            .recipe(XUNIT_TO_TUNIT)
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from xUnit to TUnit", Completed.ordinal(), Completed.getName(), "TUnit"))),
          project(nuget(
            List.of(nugetReference("TUnit", "1.0.0", "1.0.0")),
            nugetResolved("xunit.abstractions", "2.0.3")))
        );
    }

    @Test
    void indirectGoModulesDoNotCount() {
        rewriteRun(
          spec -> spec
            .recipe(GOLANG_MOCK_TO_UBER_MOCK)
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to go.uber.org/mock", Completed.ordinal(), Completed.getName(), "go.uber.org/mock"))),
          project(go(
            goRequire("go.uber.org/mock", "v0.6.0", false),
            goRequire("github.com/golang/mock", "v1.6.0", true)))
        );
    }

    @Test
    void npmDevDependencies() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageMigration("Move from Jest to Vitest", "jest", "vitest", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from Jest to Vitest", NotStarted.ordinal(), NotStarted.getName(), "jest"))),
          project(npm(List.of(), List.of(npmDependency("jest", "^30.0.0", "30.1.3"))))
        );
    }

    @Test
    void pypiDeclaredDependency() {
        rewriteRun(
          spec -> spec
            .recipe(new PackageMigration("Move from backoff to tenacity", "backoff", "tenacity", null))
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move from backoff to tenacity", NotStarted.ordinal(), NotStarted.getName(), "backoff"))),
          project(pypi(List.of(pypiDependency("backoff", ">=2.2", "2.2.1"))))
        );
    }

    @Test
    void neitherPackage() {
        rewriteRun(
          spec -> spec.recipe(XUNIT_TO_TUNIT),
          project(nuget(List.of(nugetReference("NUnit", "4.4.0", "4.4.0"))))
        );
    }

    private static SourceSpecs project(Marker marker) {
        return java("class Test {}", spec -> spec.markers(marker));
    }
}
