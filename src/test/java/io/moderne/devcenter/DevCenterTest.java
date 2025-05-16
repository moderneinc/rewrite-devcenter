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
package io.moderne.devcenter;

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static io.moderne.devcenter.JUnitUpgrade.Measure.JUnit4;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java8Plus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

public class DevCenterTest implements RewriteTest {
    Recipe starterDevCenter;

    @BeforeEach
    void before() {
        starterDevCenter = Environment.builder()
          .scanRuntimeClasspath("org.openrewrite")
          .scanYamlResources()
          .build()
          .activateRecipes("io.moderne.devcenter.DevCenterStarter");
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void valid() throws DevCenterValidationException {
        DevCenter devCenter = new DevCenter(starterDevCenter);
        devCenter.validate();

        assertThat(devCenter.getUpgradesAndMigrations()).hasSize(3);
        assertThat(devCenter.getUpgradesAndMigrations().stream()
          .map(u -> u.getCard().getMeasures()))
          .contains(List.of("Major", "Minor", "Patch", "Completed"));

        assertThat(devCenter.getSecurity()).isNotNull();
        assertThat(devCenter.getSecurity().getMeasures())
          .contains("Zip slip");
    }

    @Test
    void noCards() {
        DevCenter devCenter = new DevCenter(Recipe.builder("No cards", "A DevCenter with no cards.")
          .build("io.moderne.devcenter.DevCenterNoCards"));

        assertThatThrownBy(devCenter::validate)
          .isInstanceOf(DevCenterValidationException.class)
          .hasMessageContaining("No recipes included that provide upgrades and migrations or security advice.");
    }

    @Test
    void twoUpgradeCardsFromOneRepository() {
        rewriteRun(
          spec -> spec.recipe(starterDevCenter)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactlyInAnyOrder(
                new UpgradesAndMigrations.Row(
                  "Move to Java 21", Java8Plus.ordinal(), Java8Plus.getDisplayName(), "8"),
                new UpgradesAndMigrations.Row("Move to JUnit 5",
                  JUnit4.ordinal(), "JUnit 4", "JUnit 4")
              )),
          version(
            //language=java
            java(
              """
                public class MyTest {
                    @org.junit.Test
                    public void mine() {}
                }
                """,
              """
                public class MyTest {
                    /*~~>*/@org.junit.Test
                    public void mine() {}
                }
                """
            ),
            8
          )
        );
    }
}
