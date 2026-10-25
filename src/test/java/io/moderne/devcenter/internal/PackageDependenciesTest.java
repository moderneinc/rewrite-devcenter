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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PackageDependenciesTest {

    @CsvSource(nullValues = "null", value = {
      "^18.2.0, 18.2.0",
      "~1.2, 1.2.0",
      "18.x, 18.0.0",
      "'>=1.0.0 <2', 1.0.0",
      "<2.0.0, null",
      "*, null",
      "latest, null",
      "workspace:^1.0.0, null"
    })
    @ParameterizedTest
    void npmLowerBound(String constraint, @Nullable String expected) {
        assertThat(PackageDependencies.npmLowerBound(constraint)).isEqualTo(expected);
    }

    @CsvSource(nullValues = "null", value = {
      "'>=2.11,<3', 2.11",
      "==2.5.1, 2.5.1",
      "~=2.1, 2.1",
      "==2.*, 2",
      ">2.0, 2.0",
      "<3, null",
      "https://example.com/pkg.whl, null"
    })
    @ParameterizedTest
    void pypiLowerBound(String constraint, @Nullable String expected) {
        assertThat(PackageDependencies.pypiLowerBound(constraint)).isEqualTo(expected);
    }

    @CsvSource(nullValues = "null", value = {
      "'[1.0,2.0)', 1.0.0",
      "1.2.3, 1.2.3",
      "1.*, 1.0.0",
      "'(,2.0]', null",
      "10.0.0-preview.1, 10.0.0-preview.1"
    })
    @ParameterizedTest
    void nugetLowerBound(String constraint, @Nullable String expected) {
        assertThat(PackageDependencies.nugetLowerBound(constraint)).isEqualTo(expected);
    }

    @CsvSource({
      "v1.76.1, 1.76.1",
      "v2.0.0+incompatible, 2.0.0",
      "v0.0.0-20250101000000-abcdef123456, 0.0.0-20250101000000-abcdef123456"
    })
    @ParameterizedTest
    void goModuleVersion(String version, String expected) {
        assertThat(PackageDependencies.goModuleVersion(version)).isEqualTo(expected);
    }
}
