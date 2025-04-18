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
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static io.moderne.devcenter.JUnitUpgrade.Measure.JUnit4;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java8Plus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

public class DevCenterTest implements RewriteTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void valid() throws DevCenterValidationException {
        Recipe starterDevCenter = Environment.builder()
          .scanRuntimeClasspath("org.openrewrite")
          .scanYamlResources()
          .build()
          .activateRecipes("io.moderne.devcenter.DevCenterStarter");

        DevCenter devCenter = new DevCenter(starterDevCenter.getDescriptor());
        devCenter.validate();
        assertThat(devCenter.getUpgradesAndMigrations()).hasSize(3);
        assertThat(devCenter.getSecurity()).isNotNull();
    }

    @Test
    void noCards() {
        DevCenter devCenter = new DevCenter(Recipe.builder("No cards", "A DevCenter with no cards.")
          .build("io.moderne.devcenter.DevCenterNoCards")
          .getDescriptor());

        assertThatThrownBy(devCenter::validate)
          .isInstanceOf(DevCenterValidationException.class)
          .hasMessageContaining("No recipes included that provide upgrades and migrations or security advice.");
    }

    @Test
    void twoUpgradeCardsFromOneRepository() {
        rewriteRun(
          spec -> spec
            .recipe(Environment.builder()
              .scanRuntimeClasspath("org.openrewrite")
              .scanYamlResources()
              .build()
              // In src/main/resources/devcenter-starter.yml
              .activateRecipes("io.moderne.devcenter.DevCenterStarter"))
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
