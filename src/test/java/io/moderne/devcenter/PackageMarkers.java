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

import org.jspecify.annotations.Nullable;
import org.openrewrite.csharp.marker.MSBuildProject;
import org.openrewrite.golang.marker.GoResolutionResult;
import org.openrewrite.golang.marker.GoResolutionResult.ResolutionStatus;
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.python.marker.PythonResolutionResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PackageMarkers {

    private PackageMarkers() {
    }

    static NodeResolutionResult.Dependency npmDependency(String name, String versionConstraint, @Nullable String resolvedVersion) {
        return new NodeResolutionResult.Dependency(name, versionConstraint, resolvedVersion == null ? null :
          new NodeResolutionResult.ResolvedDependency(name, resolvedVersion, List.of(), List.of(), List.of(), List.of(), null, null));
    }

    static NodeResolutionResult npm(List<NodeResolutionResult.Dependency> dependencies,
                                    List<NodeResolutionResult.Dependency> devDependencies) {
        return new NodeResolutionResult(
          UUID.randomUUID(), "test-project", "1.0.0", null, ".",
          null, dependencies, devDependencies, List.of(), List.of(), List.of(), List.of(),
          null, null, null
        );
    }

    static PythonResolutionResult.Dependency pypiDependency(String name, String versionConstraint, @Nullable String resolvedVersion) {
        return new PythonResolutionResult.Dependency(name, versionConstraint, List.of(), null, resolvedVersion == null ? null :
          new PythonResolutionResult.ResolvedDependency(name, resolvedVersion, null, List.of()));
    }

    static PythonResolutionResult pypi(List<PythonResolutionResult.Dependency> dependencies,
                                       PythonResolutionResult.ResolvedDependency... resolved) {
        return new PythonResolutionResult(
          UUID.randomUUID(), "test-project", "1.0.0", null, null,
          ".", ">=3.12", null,
          List.of(), dependencies, Map.of(), Map.of(),
          List.of(), List.of(), List.of(resolved), null, null
        );
    }

    static PythonResolutionResult.ResolvedDependency pypiResolved(String name, String version) {
        return new PythonResolutionResult.ResolvedDependency(name, version, null, List.of());
    }

    static MSBuildProject.PackageReference nugetReference(String include, String requestedVersion, @Nullable String resolvedVersion) {
        return new MSBuildProject.PackageReference(include, requestedVersion, resolvedVersion);
    }

    static MSBuildProject.ResolvedPackage nugetResolved(String name, String version) {
        return MSBuildProject.ResolvedPackage.builder().name(name).resolvedVersion(version).build();
    }

    static MSBuildProject nuget(List<MSBuildProject.PackageReference> packageReferences,
                                MSBuildProject.ResolvedPackage... resolved) {
        return MSBuildProject.builder()
          .id(UUID.randomUUID())
          .targetFrameworks(List.of(MSBuildProject.TargetFramework.builder()
            .targetFramework("net10.0")
            .packageReferences(packageReferences)
            .resolvedPackages(List.of(resolved))
            .build()))
          .build();
    }

    static GoResolutionResult.Require goRequire(String modulePath, String version, boolean indirect) {
        return new GoResolutionResult.Require(modulePath, version, indirect);
    }

    static GoResolutionResult go(GoResolutionResult.Require... requires) {
        return new GoResolutionResult(
          UUID.randomUUID(), "github.com/test/project", "1.26", null, ".",
          List.of(requires), List.of(), List.of(), List.of(), List.of(), List.of(),
          ResolutionStatus.RESOLVED, List.of(), null
        );
    }
}
