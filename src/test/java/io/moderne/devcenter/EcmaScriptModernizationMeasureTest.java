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
package io.moderne.devcenter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EcmaScriptModernizationMeasureTest {

    @ParameterizedTest
    @CsvSource({
      "0, 5, Completed",
      "1, 11, LittleVar",
      "1, 10, PartlyVar",
      "5, 10, PartlyVar",
      "6, 11, MostlyVar",
      "1, 1, MostlyVar"
    })
    void bucketsShareOfVar(long var, long total, EcmaScriptModernization.Measure expected) {
        // when
        EcmaScriptModernization.Measure measure = EcmaScriptModernization.Measure.of(var, total);

        // then
        assertThat(measure).isEqualTo(expected);
    }
}
