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
package io.moderne.devcenter.internal;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.csharp.dependencies.internal.NugetVersion;
import org.openrewrite.csharp.dependencies.internal.NugetVersionRange;
import org.openrewrite.csharp.marker.MSBuildProject;
import org.openrewrite.golang.marker.GoResolutionResult;
import org.openrewrite.javascript.marker.NodeResolutionResult;
import org.openrewrite.javascript.semver.NodeSemver;
import org.openrewrite.python.internal.pep440.PythonVersionSpecifier;
import org.openrewrite.python.internal.pep440.PythonVersionSpecifierSet;
import org.openrewrite.python.marker.PythonResolutionResult;

import java.util.*;

import static org.openrewrite.internal.StringUtils.matchesGlob;

/**
 * Finds the npm, PyPI and NuGet packages and Go modules a project depends on. PyPI and NuGet names match
 * case-insensitively, the way those registries compare them.
 */
public final class PackageDependencies {

    private static final Set<String> PYPI_LOWER_BOUND_OPERATORS = new HashSet<>(Arrays.asList(">=", ">", "~=", "==", "==="));

    private PackageDependencies() {
    }

    /**
     * Includes transitive dependencies, except for npm, which installs several versions of a package side by side,
     * so that an outdated copy deep in the tree doesn't hold a project back.
     *
     * @return the distinct versions of matching packages, in the order they are first found. A dependency that
     * wasn't resolved contributes the lower bound of its version constraint.
     */
    public static List<String> findVersions(SourceFile source, String packagePattern) {
        Set<String> versions = new LinkedHashSet<>();
        for (Package found : findPackages(source, packagePattern)) {
            if (found.getVersion() != null) {
                versions.add(found.getVersion());
            }
        }
        return new ArrayList<>(versions);
    }

    /**
     * @return the name of the first matching package the project depends on directly, or {@code null} if there is none.
     */
    public static @Nullable String findDirectDependency(SourceFile source, String packagePattern) {
        for (Package found : findPackages(source, packagePattern)) {
            if (found.isDirect()) {
                return found.getName();
            }
        }
        return null;
    }

    private static List<Package> findPackages(SourceFile source, String packagePattern) {
        List<Package> packages = new ArrayList<>();
        source.getMarkers().findFirst(NodeResolutionResult.class).ifPresent(node -> {
            for (List<NodeResolutionResult.Dependency> scope : Arrays.asList(node.getDependencies(), node.getDevDependencies(),
                    node.getPeerDependencies(), node.getOptionalDependencies())) {
                for (NodeResolutionResult.Dependency d : scope) {
                    if (matchesGlob(d.getName(), packagePattern)) {
                        String version = d.getResolved() == null ? npmLowerBound(d.getVersionConstraint()) : d.getResolved().getVersion();
                        packages.add(new Package(d.getName(), version, true));
                    }
                }
            }
        });
        source.getMarkers().findFirst(PythonResolutionResult.class).ifPresent(python -> {
            String pattern = PythonResolutionResult.normalizeName(packagePattern);
            for (PythonResolutionResult.Dependency d : python.getAllDeclaredDependencies()) {
                if (matchesGlob(PythonResolutionResult.normalizeName(d.getName()), pattern)) {
                    String version = d.getResolved() == null ? pypiLowerBound(d.getVersionConstraint()) : d.getResolved().getVersion();
                    packages.add(new Package(d.getName(), version, true));
                }
            }
            for (PythonResolutionResult.ResolvedDependency r : python.getResolvedDependencies()) {
                if (matchesGlob(PythonResolutionResult.normalizeName(r.getName()), pattern)) {
                    packages.add(new Package(r.getName(), r.getVersion(), false));
                }
            }
        });
        source.getMarkers().findFirst(MSBuildProject.class).ifPresent(project -> {
            if (project.getTargetFrameworks() == null) {
                return;
            }
            String pattern = packagePattern.toLowerCase(Locale.ROOT);
            for (MSBuildProject.TargetFramework tf : project.getTargetFrameworks()) {
                for (MSBuildProject.PackageReference r : tf.getPackageReferences()) {
                    if (matchesGlob(r.getInclude().toLowerCase(Locale.ROOT), pattern)) {
                        String version = r.getResolvedVersion() == null ? nugetLowerBound(r.getRequestedVersion()) : r.getResolvedVersion();
                        packages.add(new Package(r.getInclude(), version, true));
                    }
                }
                for (MSBuildProject.ResolvedPackage r : tf.getResolvedPackages()) {
                    if (matchesGlob(r.getName().toLowerCase(Locale.ROOT), pattern)) {
                        packages.add(new Package(r.getName(), r.getResolvedVersion(), false));
                    }
                }
            }
        });
        source.getMarkers().findFirst(GoResolutionResult.class).ifPresent(go -> {
            for (GoResolutionResult.Require r : go.getRequires()) {
                if (matchesGlob(r.getModulePath(), packagePattern)) {
                    packages.add(new Package(r.getModulePath(), goModuleVersion(r.getVersion()), !r.isIndirect()));
                }
            }
        });
        return packages;
    }

    static @Nullable String npmLowerBound(@Nullable String constraint) {
        if (constraint == null || NodeSemver.validRange(constraint, NodeSemver.Options.DEFAULT) == null) {
            return null;
        }
        String min = NodeSemver.minVersion(constraint, NodeSemver.Options.DEFAULT);
        // Ranges without a lower bound, like `*` or `<2`, admit 0.0.0, which says nothing about the version in use
        return "0.0.0".equals(min) ? null : min;
    }

    static @Nullable String pypiLowerBound(@Nullable String constraint) {
        PythonVersionSpecifierSet specifiers = constraint == null ? null : PythonVersionSpecifierSet.parse(constraint);
        if (specifiers == null) {
            return null;
        }
        for (PythonVersionSpecifier specifier : specifiers.getSpecifiers()) {
            if (PYPI_LOWER_BOUND_OPERATORS.contains(specifier.getOperator())) {
                String version = specifier.getVersion();
                return version.endsWith(".*") ? version.substring(0, version.length() - 2) : version;
            }
        }
        return null;
    }

    static @Nullable String nugetLowerBound(@Nullable String constraint) {
        NugetVersionRange range = constraint == null ? null : NugetVersionRange.tryParse(constraint);
        NugetVersion min = range == null ? null : range.getMin();
        return min == null ? null : min.toNormalizedString();
    }

    static @Nullable String goModuleVersion(@Nullable String version) {
        if (version == null) {
            return null;
        }
        String semver = version.startsWith("v") ? version.substring(1) : version;
        return semver.endsWith("+incompatible") ? semver.substring(0, semver.length() - "+incompatible".length()) : semver;
    }

    @Value
    private static class Package {
        String name;

        @Nullable
        String version;

        boolean direct;
    }
}
