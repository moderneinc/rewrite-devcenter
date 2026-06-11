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
package io.moderne.devcenter.eol.internal;

import io.moderne.devcenter.eol.internal.Detected.Kind;
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects every resolved Maven/Gradle dependency (direct and transitive) on a source file as a
 * {@link Detected} keyed by {@code pkg:maven/<groupId>}, so it can be matched against the
 * {@link EolFeed}. The artifact id is retained only for the report; matching is by group.
 */
public final class ResolvedDependencyCoordinates {

    private ResolvedDependencyCoordinates() {
    }

    public static Set<Detected> find(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        source.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
            for (List<ResolvedDependency> scope : mrr.getDependencies().values()) {
                for (ResolvedDependency d : scope) {
                    add(detected, d);
                }
            }
        });
        source.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
            Set<ResolvedDependency> seen = new LinkedHashSet<>();
            for (GradleDependencyConfiguration c : gp.getConfigurations()) {
                Deque<ResolvedDependency> queue = new ArrayDeque<>(c.getResolved());
                while (!queue.isEmpty()) {
                    ResolvedDependency d = queue.poll();
                    if (seen.add(d)) {
                        add(detected, d);
                        queue.addAll(d.getDependencies());
                    }
                }
            }
        });
        return detected;
    }

    private static void add(Set<Detected> detected, ResolvedDependency d) {
        if (d.getGroupId() == null || d.getArtifactId() == null || d.getVersion() == null) {
            return;
        }
        detected.add(new Detected(
                Purl.maven(d.getGroupId()),
                d.getVersion(),
                d.getGroupId() + ':' + d.getArtifactId(),
                "JVM",
                Kind.DEPENDENCY));
    }
}
