package io.moderne.devcenter.result;

import io.moderne.devcenter.DevCenter;
import io.moderne.devcenter.SemverMeasure;
import io.moderne.organizations.Organization;
import io.moderne.organizations.OrganizationReader;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;

import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class DevCenterResultReducerTest {

    @Test
    void results() {
        Environment environment = Environment.builder()
          .scanRuntimeClasspath("org.openrewrite")
          .scanYamlResources()
          .build();
        DevCenter devCenter = new DevCenter(environment.activateRecipes("io.moderne.devcenter.DevCenterStarter"));
        Organization<Object> root = new OrganizationReader().fromCsv(requireNonNull(getClass()
          .getResourceAsStream("/repos-Default.csv")));

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

            result.forEach(devCenter.getCard("Move to Spring Boot 3.5.0"), (measure, count) -> {
                assertThat(count)
                  .describedAs(measure.getName())
                  .isEqualTo((int) switch (measure) {
                      case SemverMeasure.Minor -> 3;
                      case SemverMeasure.Completed -> 1;
                      default -> fail("Unexpected measure");
                  });
            });

            result.forEach(devCenter.getSecurity(), (measure, count) -> {
                assertThat(measure.getName()).isEqualTo("Remediate OWASP A08:2021 Software and data integrity failures");
                assertThat(count).isEqualTo(20);
            });
        });

        assertThat(orgsChecked).containsExactly("ALL", "Default", "ε");
    }
}
