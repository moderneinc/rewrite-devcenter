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

import org.jspecify.annotations.Nullable;

/**
 * Normalizes the loosely-specified version strings found in npm and .NET manifests into a plain
 * dotted version that {@link EolFeed}'s longest-prefix cycle matching can use.
 */
public final class Versions {

    private Versions() {
    }

    /**
     * Reduce an npm version range (e.g. {@code ^18.2.0}, {@code ~4.17.21}, {@code >=18}, {@code 18.x})
     * to its leading concrete version. Returns {@code null} for ranges with no usable anchor
     * ({@code *}, {@code latest}, {@code workspace:*}, git/url specifiers, empty).
     */
    public static @Nullable String normalizeNpmRange(@Nullable String range) {
        if (range == null) {
            return null;
        }
        String r = range.trim();
        if (r.isEmpty() || r.equals("*") || r.equalsIgnoreCase("latest") || r.contains(":") || r.contains("/")) {
            return null;
        }
        // Take the first comparator clause of compound ranges like ">=1.0.0 <2.0.0".
        int space = r.indexOf(' ');
        if (space > 0) {
            r = r.substring(0, space);
        }
        r = stripLeadingOperators(r);
        return r.isEmpty() || !Character.isDigit(r.charAt(0)) ? null : r;
    }

    /**
     * Reduce a {@code engines.node} specifier (e.g. {@code >=18.0.0}, {@code 18.x}, {@code ^20})
     * to its leading concrete version.
     */
    public static @Nullable String normalizeNodeEngine(@Nullable String engine) {
        return normalizeNpmRange(engine);
    }

    /**
     * Extract the leading dotted numeric version from a string, ignoring any suffix. Useful for
     * Docker tags ({@code 18.20-alpine} &rarr; {@code 18.20}, {@code 3.11-slim} &rarr; {@code 3.11})
     * and OS-style tags. Returns {@code null} when there is no leading digit (e.g. {@code latest},
     * {@code bullseye}, {@code alpine}).
     */
    public static @Nullable String leadingVersion(@Nullable String tag) {
        if (tag == null) {
            return null;
        }
        int end = 0;
        while (end < tag.length() && (Character.isDigit(tag.charAt(end)) || tag.charAt(end) == '.')) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        String v = tag.substring(0, end);
        // Trim a trailing dot left by tags like "18." (defensive).
        return v.endsWith(".") ? v.substring(0, v.length() - 1) : v;
    }

    private static String stripLeadingOperators(String r) {
        int i = 0;
        while (i < r.length()) {
            char c = r.charAt(i);
            if (c == '^' || c == '~' || c == '>' || c == '<' || c == '=' || c == 'v' || c == 'V' || c == ' ') {
                i++;
            } else {
                break;
            }
        }
        return r.substring(i);
    }

    /**
     * Map a .NET target-framework moniker to a {@link Purl} runtime + version, or {@code null} when
     * it does not denote a versioned runtime (e.g. {@code netstandard2.0}).
     */
    public static @Nullable Runtime targetFrameworkRuntime(@Nullable String tfm) {
        if (tfm == null) {
            return null;
        }
        String m = tfm.trim().toLowerCase();
        if (m.startsWith("netstandard")) {
            return null;
        }
        if (m.startsWith("netcoreapp")) {
            String v = m.substring("netcoreapp".length());
            return v.isEmpty() ? null : new Runtime(Purl.runtime("dotnet"), v, ".NET");
        }
        if (m.startsWith("net")) {
            String v = m.substring(3);
            if (v.isEmpty()) {
                return null;
            }
            if (v.contains(".")) {
                // Modern .NET (5+): "net8.0" -> dotnet 8.0. Strip any platform moniker so that
                // OS-targeted TFMs ("net8.0-windows", "net8.0-android33.0", "net8.0-ios") still
                // match the "8.0" release cycle instead of falling through unmatched.
                int dash = v.indexOf('-');
                if (dash >= 0) {
                    v = v.substring(0, dash);
                }
                return new Runtime(Purl.runtime("dotnet"), v, ".NET");
            }
            // Legacy .NET Framework: "net48" -> dotnetfx 4.8, "net472" -> 4.7.2
            return new Runtime(Purl.runtime("dotnetfx"), dotDigits(v), ".NET Framework");
        }
        return null;
    }

    private static String dotDigits(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /** A runtime identity derived from a target-framework moniker. */
    public static final class Runtime {
        public final Purl purl;
        public final String version;
        public final String ecosystem;

        Runtime(Purl purl, String version, String ecosystem) {
            this.purl = purl;
            this.version = version;
            this.ecosystem = ecosystem;
        }
    }
}
