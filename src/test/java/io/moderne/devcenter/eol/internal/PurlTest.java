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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurlTest {

    @Test
    void parsesTypeAndIdentifier() {
        Purl purl = Purl.parse("pkg:maven/org.springframework.boot");
        assertThat(purl.getType()).isEqualTo("maven");
        assertThat(purl.getIdentifier()).isEqualTo("org.springframework.boot");
    }

    @Test
    void preservesScopedNpmNamesAfterTheFirstSlash() {
        // Everything after the first slash is the identifier, so a scoped package keeps its slash.
        Purl purl = Purl.parse("pkg:npm/@angular/core");
        assertThat(purl.getType()).isEqualTo("npm");
        assertThat(purl.getIdentifier()).isEqualTo("@angular/core");
    }

    @Test
    void preservesMultiSegmentDockerNamespaces() {
        Purl purl = Purl.parse("pkg:docker/library/node");
        assertThat(purl.getType()).isEqualTo("docker");
        assertThat(purl.getIdentifier()).isEqualTo("library/node");
    }

    @Test
    void parsesCoordinateWithoutThePkgPrefix() {
        assertThat(Purl.parse("npm/express")).isEqualTo(Purl.npm("express"));
    }

    @Test
    void roundTripsThroughCoordinate() {
        Purl purl = Purl.npm("@angular/core");
        assertThat(Purl.parse(purl.coordinate())).isEqualTo(purl);
        assertThat(purl.coordinate()).isEqualTo("pkg:npm/@angular/core");
    }

    @Test
    void keyJoinsTypeAndIdentifier() {
        assertThat(Purl.maven("com.google.guava").key()).isEqualTo("maven|com.google.guava");
        assertThat(Purl.runtime("openjdk").key()).isEqualTo("generic|openjdk");
    }

    @Test
    void factoriesUseTheExpectedTypes() {
        assertThat(Purl.maven("g").getType()).isEqualTo("maven");
        assertThat(Purl.npm("n").getType()).isEqualTo("npm");
        assertThat(Purl.nuget("n").getType()).isEqualTo("nuget");
        assertThat(Purl.runtime("r").getType()).isEqualTo("generic");
        assertThat(Purl.docker("library/node").getType()).isEqualTo("docker");
    }

    @Test
    void rejectsCoordinatesWithoutAnIdentifier() {
        assertThatThrownBy(() -> Purl.parse("pkg:maven"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Purl.parse("pkg:maven/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Purl.parse("pkg:/no-type"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
