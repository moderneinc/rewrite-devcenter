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
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;

import java.util.ArrayList;
import java.util.List;

public final class ResolvedDependencyVersions {

    private ResolvedDependencyVersions() {
    }

    public static List<String> findVersions(SourceFile source, String groupIdPattern, String artifactIdPattern) {
        List<String> versions = new ArrayList<>();
        source.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
            for (ResolvedDependency d : mrr.findDependencies(groupIdPattern, artifactIdPattern, null)) {
                versions.add(d.getVersion());
            }
        });
        source.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
            for (GradleDependencyConfiguration c : gp.getConfigurations()) {
                for (ResolvedDependency root : c.getResolved()) {
                    for (ResolvedDependency match : root.findDependencies(groupIdPattern, artifactIdPattern)) {
                        versions.add(match.getVersion());
                    }
                }
            }
        });
        return versions;
    }
}
