/*
 * Copyright 2025 the original author or authors.
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
package io.moderne.devcenter.result;

import io.moderne.devcenter.DevCenter;
import io.moderne.devcenter.SemverMeasure;
import io.moderne.organizations.Organization;
import io.moderne.organizations.OrganizationReader;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class DevCenterResultReducerTest {
    Environment environment = Environment.builder()
      .scanRuntimeClasspath("org.openrewrite")
      .scanYamlResources()
      .build();
    DevCenter devCenter = new DevCenter(environment.activateRecipes("io.moderne.devcenter.DevCenterStarter"));
    Organization<Object> root = new OrganizationReader<>().fromCsv(requireNonNull(getClass()
      .getResourceAsStream("/repos-Default.csv")));

    @Test
    void upgradesAndSecurityIssues() {
        DevCenterResultReducer reducer = DevCenterResultReducer.fromDataTables(
          devCenter,
          root,
          new InputStreamReader(requireNonNull(getClass().getResourceAsStream(
            "/UpgradesAndMigrations-Default.csv"))),
          new InputStreamReader(requireNonNull(getClass().getResourceAsStream(
            "/SecurityIssues-Default.csv")))
        );

        Set<String> orgsChecked = new TreeSet<>();
        root.forEachOrganization(org -> {
            orgsChecked.add(org.getName());
            DevCenterResult result = reducer.reduce(org);
            hasSpringBoot35Results(result);
            hasSecurityResults(result);
        });

        assertThat(orgsChecked).containsExactly("ALL", "Default");
    }

    @Test
    void emptyDataTable() {
        DevCenterResultReducer reducer = DevCenterResultReducer.fromDataTables(
          devCenter,
          root,
          new StringReader(""),
          new StringReader("")
        );

        assertThat(reducer.reduce(root).getResultsByCard()).isEmpty();
    }

    @Test
    void emptySecurity() {
        DevCenterResultReducer reducer = DevCenterResultReducer.fromDataTables(
          devCenter,
          root,
          new InputStreamReader(requireNonNull(getClass().getResourceAsStream(
            "/UpgradesAndMigrations-Default.csv"))),
          new StringReader("")
        );

        assertThat(reducer.reduce(root).getResultsByCard().keySet())
          .map(DevCenter.Card::getName)
          .containsExactly("Move to Spring Boot 3.5.0");
    }

    private void hasSecurityResults(DevCenterResult result) {
        result.forEach(devCenter.getSecurity(), (measure, count) -> {
            assertThat(measure.getName()).isEqualTo("Remediate OWASP A08:2021 Software and data integrity failures");
            assertThat(count).isEqualTo(20);
        });
    }

    private void hasSpringBoot35Results(DevCenterResult result) {
        result.forEach(devCenter.getCard("Move to Spring Boot 3.5.0"), (measure, count) -> {
            assertThat(count)
              .describedAs(measure.getName())
              .isEqualTo((int) switch (measure) {
                  case SemverMeasure.Minor -> 3;
                  case SemverMeasure.Completed -> 1;
                  default -> fail("Unexpected measure");
              });
        });
    }
}
