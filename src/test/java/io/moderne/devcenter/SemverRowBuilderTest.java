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

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemverRowBuilderTest {

    @CsvSource({
      // Releases
      "4.1.0, 2.7.18, Major",
      "4.1.0, 1.5.22.RELEASE, Major",
      "4.1.0, 4.0.3, Minor",
      "4.1.0, 4.1.0, Completed",
      "4.1.0, 4.1.2, Completed",
      "4.1.0, 4.2.0, Completed",
      "3.4.5, 3.4.3, Patch",
      "40, 39, Major",
      "40, 40, Completed",
      "9.0.0, 8.14, Major",
      "9.0.0, 9.1, Completed",
      "4.0.x, 3.20.1, Major",
      "4.0.x, 2.16.12.Final, Major",
      // Pre-releases are as far behind as their numbers say
      "4.1.0, 2.0.0.M7, Major",
      "4.1.0, 3.0.0-M3, Major",
      "4.1.0, 3.2.0-RC1, Major",
      "4.1.0, 3.5.0-SNAPSHOT, Major",
      "4.1.0, 4.0.0-M1, Minor",
      "4.1.0, 4.0.0-SNAPSHOT, Minor",
      "3.4.5, 3.4.4-SNAPSHOT, Patch",
      "9.0.0, 8.0-rc-1, Major",
      "4.0.0, 3.9.0-alpha-1, Major",
      "4.0.x, 3.0.0.CR1, Major",
      "19.0.0, 18.3.0-canary-1, Major",
      "2.14.0, 2.0b3, Minor",
      // A pre-release of the target itself is not there yet
      "4.1.0, 4.1.0-RC1, Patch",
      "4.1.0, 4.1.0-SNAPSHOT, Patch",
      "9.0.0, 9.0-milestone-1, Patch",
      "4.0.0, 4.0.0-rc-4, Patch",
      // A pre-release past the target is done
      "4.1.0, 4.2.0-RC1, Completed",
      "4.1.0, 4.1.1-SNAPSHOT, Completed",
      "4.0.x, 999-SNAPSHOT, Completed",
      // Qualifiers that aren't pre-releases don't hold a version back
      "33.0.0, 33.0.0-jre, Completed",
      "33.0.0, 32.1.3-jre, Major",
    })
    @ParameterizedTest
    void measure(String target, String current, SemverMeasure expected) {
        assertThat(new SemverRowBuilder("card", target).getRow(current))
          .isEqualTo(new UpgradesAndMigrations.Row("card", expected.ordinal(), expected.toString(), current));
    }
}
