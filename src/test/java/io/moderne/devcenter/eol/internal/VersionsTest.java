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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VersionsTest {

    @ParameterizedTest
    @CsvSource({
            // range, expected leading concrete version
            "^18.2.0,    18.2.0",
            "~4.17.21,   4.17.21",
            ">=18,       18",
            "=1.2.3,     1.2.3",
            "v1.2.3,     1.2.3",   // a leading 'v' is an operator, not part of the version
            "18.x,       18.x",    // wildcards within the version are left for cycle matching
            "'>=1.0.0 <2.0.0', 1.0.0" // compound range: only the first comparator clause is kept
    })
    void normalizeNpmRangeTakesLeadingConcreteVersion(String range, String expected) {
        assertThat(Versions.normalizeNpmRange(range)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",            // empty
            "   ",         // blank
            "*",           // any
            "latest",      // dist-tag
            "LATEST",      // dist-tag, case-insensitive
            "workspace:*", // monorepo protocol (contains ':')
            "file:../lib", // local path protocol
            "git+https://example.com/a.git", // git url (contains ':' and '/')
            "npm:other@1.2.3",               // aliased package
            "^",           // operator with no version
            ">="           // operator with no version
    })
    void normalizeNpmRangeRejectsRangesWithoutAConcreteAnchor(String range) {
        assertThat(Versions.normalizeNpmRange(range)).isNull();
    }

    @Test
    void normalizeNodeEngineDelegatesToNpmRangeNormalization() {
        assertThat(Versions.normalizeNodeEngine(">=20.0.0")).isEqualTo("20.0.0");
        assertThat(Versions.normalizeNodeEngine("*")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            // tag, expected leading dotted version
            "18.20-alpine, 18.20",
            "3.11-slim,    3.11",
            "16,           16",
            "8,            8",
            "18.,          18" // a trailing dot left by a malformed tag is trimmed
    })
    void leadingVersionExtractsTheDottedNumericPrefix(String tag, String expected) {
        assertThat(Versions.leadingVersion(tag)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"latest", "bullseye", "alpine", "stable-slim"})
    void leadingVersionReturnsNullWithoutALeadingDigit(String tag) {
        assertThat(Versions.leadingVersion(tag)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            // tfm, expected runtime identifier, expected version, expected ecosystem
            "net6.0,              dotnet,   6.0,   .NET",
            "net8.0,              dotnet,   8.0,   .NET",
            "NET8.0,              dotnet,   8.0,   .NET",            // monikers are case-insensitive
            "net8.0-windows,      dotnet,   8.0,   .NET",           // OS-targeted TFM: platform stripped
            "net8.0-android33.0,  dotnet,   8.0,   .NET",           // platform + API level stripped
            "netcoreapp3.1,       dotnet,   3.1,   .NET",           // .NET Core era
            "net48,               dotnetfx, 4.8,   .NET Framework", // legacy framework: digits dotted
            "net472,              dotnetfx, 4.7.2, .NET Framework",
            "net40,               dotnetfx, 4.0,   .NET Framework"
    })
    void targetFrameworkRuntimeMapsMonikersToRuntimes(String tfm, String identifier, String version, String ecosystem) {
        Versions.Runtime runtime = Versions.targetFrameworkRuntime(tfm);
        assertThat(runtime).isNotNull();
        assertThat(runtime.purl.getType()).isEqualTo("generic");
        assertThat(runtime.purl.getIdentifier()).isEqualTo(identifier);
        assertThat(runtime.version).isEqualTo(version);
        assertThat(runtime.ecosystem).isEqualTo(ecosystem);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "netstandard2.0", // a portability target, not a runtime
            "netstandard",
            "netcoreapp",     // no version
            "net",            // no version
            "node18",         // not a .NET moniker at all
            "monogame"
    })
    void targetFrameworkRuntimeReturnsNullForNonRuntimeMonikers(String tfm) {
        assertThat(Versions.targetFrameworkRuntime(tfm)).isNull();
    }
}
