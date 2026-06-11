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
import org.junit.jupiter.api.Test;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;
import org.openrewrite.text.PlainText;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of the Gradle branch of {@link ResolvedDependencyCoordinates}. The Maven branch is
 * covered end-to-end by {@code DependencyEndOfLifeTest}; the Gradle branch needs a {@link GradleProject}
 * marker carrying resolved dependencies, which the test harness does not populate from a parsed
 * {@code build.gradle}, so the marker is built directly here.
 */
class ResolvedDependencyCoordinatesTest {

    private static ResolvedDependency dep(String group, String artifact, String version,
                                          ResolvedDependency... transitives) {
        return ResolvedDependency.builder()
                .gav(new ResolvedGroupArtifactVersion(null, group, artifact, version, null))
                .dependencies(asList(transitives))
                .build();
    }

    private static PlainText gradleSource(Map<String, GradleDependencyConfiguration> configurations) {
        GradleProject project = GradleProject.builder()
                .id(UUID.randomUUID())
                .name("project")
                .path(":")
                .nameToConfiguration(configurations)
                .build();
        return PlainText.builder()
                .id(UUID.randomUUID())
                .sourcePath(Paths.get("build.gradle"))
                .markers(Markers.build(singletonList(project)))
                .charsetName("UTF-8")
                .text("")
                .build();
    }

    private static GradleDependencyConfiguration configuration(String name, ResolvedDependency... resolved) {
        return GradleDependencyConfiguration.builder()
                .name(name)
                .directResolved(asList(resolved))
                .build();
    }

    @Test
    void collectsDirectAndTransitiveDependenciesByGroup() {
        ResolvedDependency guava = dep("com.google.guava", "guava", "33.0.0-jre",
                dep("com.google.guava", "failureaccess", "1.0.1"),
                dep("com.google.code.findbugs", "jsr305", "3.0.2"));

        Set<Detected> detected = ResolvedDependencyCoordinates.find(
                gradleSource(singletonMap("runtimeClasspath", configuration("runtimeClasspath", guava))));

        // Every resolved dependency (direct + transitive) is collected, keyed by group; feed matching
        // happens later in the card.
        assertThat(detected).containsExactlyInAnyOrder(
                new Detected(Purl.maven("com.google.guava"), "33.0.0-jre",
                        "com.google.guava:guava", "JVM", Kind.DEPENDENCY),
                new Detected(Purl.maven("com.google.guava"), "1.0.1",
                        "com.google.guava:failureaccess", "JVM", Kind.DEPENDENCY),
                new Detected(Purl.maven("com.google.code.findbugs"), "3.0.2",
                        "com.google.code.findbugs:jsr305", "JVM", Kind.DEPENDENCY));
    }

    @Test
    void dedupsDependenciesSharedAcrossConfigurations() {
        ResolvedDependency guava = dep("com.google.guava", "guava", "33.0.0-jre");

        Map<String, GradleDependencyConfiguration> configurations = new LinkedHashMap<>();
        configurations.put("compileClasspath", configuration("compileClasspath", guava));
        configurations.put("runtimeClasspath", configuration("runtimeClasspath", guava));

        // The same artifact appearing in two configurations must yield a single coordinate.
        assertThat(ResolvedDependencyCoordinates.find(gradleSource(configurations)))
                .containsExactly(new Detected(Purl.maven("com.google.guava"), "33.0.0-jre",
                        "com.google.guava:guava", "JVM", Kind.DEPENDENCY));
    }

    @Test
    void skipsDependenciesMissingCoordinates() {
        ResolvedDependency incomplete = dep("com.example", "artifact", null);

        assertThat(ResolvedDependencyCoordinates.find(
                gradleSource(singletonMap("runtimeClasspath", configuration("runtimeClasspath", incomplete)))))
                .isEmpty();
    }

    @Test
    void returnsNothingWhenNoResolutionMarkerIsPresent() {
        PlainText noMarker = PlainText.builder()
                .id(UUID.randomUUID())
                .sourcePath(Paths.get("build.gradle"))
                .markers(Markers.EMPTY)
                .charsetName("UTF-8")
                .text("")
                .build();

        assertThat(ResolvedDependencyCoordinates.find(noMarker)).isEmpty();
    }
}
