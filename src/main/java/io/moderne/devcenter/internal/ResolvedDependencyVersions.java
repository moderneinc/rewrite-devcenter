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

import org.openrewrite.SourceFile;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.newSetFromMap;
import static org.openrewrite.internal.StringUtils.matchesGlob;

public final class ResolvedDependencyVersions {

    private ResolvedDependencyVersions() {
    }

    /**
     * @return the distinct versions of matching dependencies, in the order they are first found.
     */
    public static List<String> findVersions(SourceFile source, String groupIdPattern, String artifactIdPattern) {
        Set<String> versions = new LinkedHashSet<>();
        source.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
            for (ResolvedDependency d : mrr.findDependencies(groupIdPattern, artifactIdPattern, null)) {
                versions.add(d.getVersion());
            }
        });
        source.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
            Set<String> found = new LinkedHashSet<>();
            // Configurations list every transitive dependency as well, so walk each shared subtree only once
            Set<ResolvedDependency> visited = newSetFromMap(new IdentityHashMap<>());
            for (GradleDependencyConfiguration c : gp.getConfigurations()) {
                for (ResolvedDependency root : c.getResolved()) {
                    if (!collectVersions(root, groupIdPattern, artifactIdPattern, visited, found)) {
                        addVersionsPerRoot(gp, groupIdPattern, artifactIdPattern, versions);
                        return;
                    }
                }
            }
            versions.addAll(found);
        });
        return new ArrayList<>(versions);
    }

    /**
     * @return false when a dependency with exclusions is reached, since what they exclude depends on the path
     * a dependency is reached by and so can't be decided with a shared visited set.
     */
    private static boolean collectVersions(ResolvedDependency dependency, String groupIdPattern, String artifactIdPattern,
                                           Set<ResolvedDependency> visited, Set<String> found) {
        if (!visited.add(dependency)) {
            return true;
        }
        if (matchesGlob(dependency.getGroupId(), groupIdPattern) && matchesGlob(dependency.getArtifactId(), artifactIdPattern)) {
            found.add(dependency.getVersion());
        }
        List<ResolvedDependency> dependencies = dependency.getDependencies();
        if (dependencies.isEmpty()) {
            return true;
        }
        Dependency requested = dependency.getRequested();
        List<GroupArtifact> exclusions = requested == null ? null : requested.getExclusions();
        if (exclusions != null && !exclusions.isEmpty()) {
            return false;
        }
        for (ResolvedDependency d : dependencies) {
            if (!collectVersions(d, groupIdPattern, artifactIdPattern, visited, found)) {
                return false;
            }
        }
        return true;
    }

    private static void addVersionsPerRoot(GradleProject gp, String groupIdPattern, String artifactIdPattern, Set<String> versions) {
        for (GradleDependencyConfiguration c : gp.getConfigurations()) {
            for (ResolvedDependency root : c.getResolved()) {
                for (ResolvedDependency match : root.findDependencies(groupIdPattern, artifactIdPattern)) {
                    versions.add(match.getVersion());
                }
            }
        }
    }
}
