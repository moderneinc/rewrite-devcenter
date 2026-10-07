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

import org.junit.jupiter.api.Test;
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;
import org.openrewrite.text.PlainText;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class ResolvedDependencyVersionsTest {

    private static final String[][] PATTERNS = {
      {"org.example", "*"},
      {"org.example", "lib-1"},
      {"*", "lib-*"},
      {"com.other", "core"},
      {"*", "*"}
    };

    @Test
    void sameVersionsAsWalkingEachRootSeparately() {
        Random random = new Random(42);
        for (int i = 0; i < 300; i++) {
            GradleProject project = randomProject(random);
            for (String[] pattern : PATTERNS) {
                assertThat(ResolvedDependencyVersions.findVersions(withMarker(project), pattern[0], pattern[1]))
                  .containsExactlyElementsOf(versionsPerRoot(project, pattern[0], pattern[1]));
            }
        }
    }

    @Test
    void cycles() {
        List<ResolvedDependency> aDependencies = new ArrayList<>();
        ResolvedDependency a = dependency("org.example", "a", "1.0", aDependencies);
        ResolvedDependency b = dependency("org.example", "b", "1.0",
          List.of(a, dependency("org.example", "lib-1", "2.0", emptyList())));
        aDependencies.add(b);
        GradleProject project = project(List.of(a));

        assertThat(ResolvedDependencyVersions.findVersions(withMarker(project), "org.example", "lib-*"))
          .containsExactly("2.0")
          .containsExactlyElementsOf(versionsPerRoot(project, "org.example", "lib-*"));
    }

    @Test
    void excludedDependenciesAreNotFound() {
        ResolvedDependency excluding = ResolvedDependency.builder()
          .gav(new ResolvedGroupArtifactVersion(null, "org.example", "excluding", "1.0", null))
          .requested(Dependency.builder()
            .gav(new GroupArtifactVersion("org.example", "excluding", "1.0"))
            .exclusions(List.of(new GroupArtifact("org.example", "lib-1")))
            .build())
          .dependencies(List.of(dependency("org.example", "lib-1", "1.0", emptyList())))
          .build();
        ResolvedDependency other = dependency("org.example", "other", "1.0",
          List.of(dependency("org.example", "lib-1", "2.0", emptyList())));
        GradleProject project = project(List.of(excluding, other));

        assertThat(ResolvedDependencyVersions.findVersions(withMarker(project), "org.example", "lib-1"))
          .containsExactly("2.0")
          .containsExactlyElementsOf(versionsPerRoot(project, "org.example", "lib-1"));
    }

    @Test
    void walksSharedSubtreesOnce() {
        List<ResolvedDependency> leaves = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            leaves.add(dependency("org.example", "leaf-" + i, "1.0", emptyList()));
        }
        AtomicInteger walks = new AtomicInteger();
        ResolvedDependency hub = dependency("org.example", "hub", "1.0", new AbstractList<>() {
            @Override
            public ResolvedDependency get(int index) {
                if (index == 0) {
                    walks.incrementAndGet();
                }
                return leaves.get(index);
            }

            @Override
            public int size() {
                return leaves.size();
            }
        });
        List<ResolvedDependency> roots = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            roots.add(dependency("org.example", "root-" + i, "1.0", List.of(hub)));
        }
        GradleProject project = project(roots, roots);
        // Configurations flatten their dependency graph on first use, walking it along the way
        project.getConfigurations().forEach(GradleDependencyConfiguration::getResolved);
        walks.set(0);

        assertThat(ResolvedDependencyVersions.findVersions(withMarker(project), "org.example", "leaf-*"))
          .containsExactly("1.0");
        assertThat(walks).hasValue(1);
    }

    private static Set<String> versionsPerRoot(GradleProject project, String groupIdPattern, String artifactIdPattern) {
        Set<String> versions = new LinkedHashSet<>();
        for (GradleDependencyConfiguration c : project.getConfigurations()) {
            for (ResolvedDependency root : c.getResolved()) {
                for (ResolvedDependency match : root.findDependencies(groupIdPattern, artifactIdPattern)) {
                    versions.add(match.getVersion());
                }
            }
        }
        return versions;
    }

    private static GradleProject randomProject(Random random) {
        List<List<ResolvedDependency>> configurations = new ArrayList<>();
        int configurationCount = 1 + random.nextInt(3);
        for (int c = 0; c < configurationCount; c++) {
            if (c > 0 && random.nextBoolean()) {
                // Share dependency instances with the previous configuration
                configurations.add(configurations.get(c - 1));
                continue;
            }
            int size = 1 + random.nextInt(40);
            List<List<ResolvedDependency>> children = new ArrayList<>();
            List<ResolvedDependency> nodes = new ArrayList<>();
            for (int n = 0; n < size; n++) {
                List<ResolvedDependency> dependencies = new ArrayList<>();
                children.add(dependencies);
                nodes.add(dependency(
                  random.nextInt(4) == 0 ? "com.other" : "org.example",
                  random.nextInt(4) == 0 ? "core" : "lib-" + random.nextInt(6),
                  new String[]{"1.0", "1.1", "2.0", "2.0.1"}[random.nextInt(4)],
                  dependencies));
            }
            // Edges only point at later nodes, keeping the graph acyclic but with plenty of shared subtrees
            for (int n = 0; n < size; n++) {
                int edges = random.nextInt(4);
                for (int e = 0; e < edges && n + 1 < size; e++) {
                    children.get(n).add(nodes.get(n + 1 + random.nextInt(size - n - 1)));
                }
            }
            configurations.add(new ArrayList<>(nodes.subList(0, Math.min(size, 1 + random.nextInt(5)))));
        }
        return projectWithConfigurations(configurations);
    }

    @SafeVarargs
    private static GradleProject project(List<ResolvedDependency>... directResolvedByConfiguration) {
        return projectWithConfigurations(Arrays.asList(directResolvedByConfiguration));
    }

    private static GradleProject projectWithConfigurations(List<List<ResolvedDependency>> directResolvedByConfiguration) {
        Map<String, GradleDependencyConfiguration> configurations = new LinkedHashMap<>();
        for (List<ResolvedDependency> directResolved : directResolvedByConfiguration) {
            String name = "configuration" + configurations.size();
            configurations.put(name, GradleDependencyConfiguration.builder()
              .name(name)
              .extendsFrom(emptyList())
              .requested(emptyList())
              .directResolved(directResolved)
              .build());
        }
        return GradleProject.builder().nameToConfiguration(configurations).build();
    }

    private static ResolvedDependency dependency(String groupId, String artifactId, String version,
                                                 List<ResolvedDependency> dependencies) {
        return ResolvedDependency.builder()
          .gav(new ResolvedGroupArtifactVersion(null, groupId, artifactId, version, null))
          .requested(Dependency.builder().gav(new GroupArtifactVersion(groupId, artifactId, version)).build())
          .dependencies(dependencies)
          .build();
    }

    private static SourceFile withMarker(GradleProject project) {
        return PlainText.builder()
          .sourcePath(Path.of("build.gradle"))
          .markers(Markers.build(List.of(project)))
          .text("")
          .build();
    }
}
