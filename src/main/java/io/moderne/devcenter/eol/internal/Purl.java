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

import lombok.Value;

/**
 * A reduced <a href="https://github.com/package-url/purl-spec">package-URL</a> used as the common
 * matching key between a detected artifact/runtime and a product in the {@link EolFeed}.
 * <p>
 * Only the parts needed to match are retained: the {@code type} (e.g. {@code maven}, {@code npm},
 * {@code nuget}, {@code generic}) and a single {@code identifier}. The identifier is intentionally
 * coarse so that one feed entry covers a whole product:
 * <ul>
 *   <li>{@code maven}: the <em>group id</em> (the artifact is treated as a wildcard within it).</li>
 *   <li>{@code npm} / {@code nuget}: the package name (e.g. {@code express}, {@code @angular/core},
 *       {@code Newtonsoft.Json}).</li>
 *   <li>{@code generic}: a language runtime name ({@code openjdk}, {@code node}, {@code dotnet},
 *       {@code python}).</li>
 * </ul>
 */
@Value
public class Purl {

    String type;
    String identifier;

    /** The stable lookup key shared by feed coordinates and detected artifacts. */
    public String key() {
        return type + '|' + identifier;
    }

    /** The {@code pkg:type/identifier} string form used in feeds and reports. */
    public String coordinate() {
        return "pkg:" + type + '/' + identifier;
    }

    /**
     * Parse a feed coordinate such as {@code pkg:maven/org.springframework.boot} or
     * {@code pkg:npm/@angular/core}. Everything after the first slash is the identifier, so
     * scoped/multi-segment names are preserved verbatim.
     */
    public static Purl parse(String coordinate) {
        String body = coordinate.startsWith("pkg:") ? coordinate.substring(4) : coordinate;
        int slash = body.indexOf('/');
        if (slash <= 0 || slash == body.length() - 1) {
            throw new IllegalArgumentException("Not a recognizable package-URL coordinate: " + coordinate);
        }
        return new Purl(body.substring(0, slash), body.substring(slash + 1));
    }

    public static Purl maven(String groupId) {
        return new Purl("maven", groupId);
    }

    public static Purl npm(String packageName) {
        return new Purl("npm", packageName);
    }

    public static Purl nuget(String packageId) {
        return new Purl("nuget", packageId);
    }

    /** A language runtime, e.g. {@code openjdk}, {@code node}, {@code dotnet}, {@code python}. */
    public static Purl runtime(String runtimeName) {
        return new Purl("generic", runtimeName);
    }

    /**
     * A container image, identified by its {@code namespace/name} (official images use the
     * {@code library} namespace), e.g. {@code library/node}, {@code bitnami/node}.
     */
    public static Purl docker(String namespaceAndName) {
        return new Purl("docker", namespaceAndName);
    }
}
