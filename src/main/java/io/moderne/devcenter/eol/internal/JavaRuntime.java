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
import org.openrewrite.java.marker.JavaVersion;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads the Java runtime version from the {@link JavaVersion} marker the Java parser attaches to
 * each compilation unit (sourced from {@code maven.compiler.release}/{@code source}, Gradle's
 * {@code sourceCompatibility}/{@code release}, etc.). Keyed as {@code pkg:generic/openjdk} so it
 * matches a vendor-neutral OpenJDK schedule in the feed.
 */
public final class JavaRuntime {

    private JavaRuntime() {
    }

    public static Set<Detected> find(SourceFile source) {
        Set<Detected> detected = new LinkedHashSet<>();
        source.getMarkers().findFirst(JavaVersion.class).ifPresent(jv ->
                detected.add(new Detected(
                        Purl.runtime("openjdk"),
                        Integer.toString(jv.getMajorVersion()),
                        "Java",
                        "Java",
                        Kind.RUNTIME)));
        return detected;
    }
}
