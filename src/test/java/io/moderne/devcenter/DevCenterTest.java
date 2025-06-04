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
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.test.RewriteTest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static io.moderne.devcenter.JUnitUpgrade.Measure.JUnit4;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java8Plus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class DevCenterTest implements RewriteTest {
    Environment environment = Environment.builder()
      .scanRuntimeClasspath("org.openrewrite")
      .scanYamlResources()
      .build();
    Recipe starterDevCenter;
    Recipe starterOriginalSecurity;

    @BeforeEach
    void before() {
        starterDevCenter = environment.activateRecipes("io.moderne.devcenter.DevCenterStarter");
        starterOriginalSecurity = environment.activateRecipes("io.moderne.devcenter.SecurityOriginalStarter");
    }

    @Test
    void isDevCenter() {
        assertThat(DevCenter.isDevCenter(starterDevCenter.getDescriptor())).isTrue();
        assertThat(DevCenter.isDevCenter(starterOriginalSecurity.getDescriptor())).isTrue();

        // This is the variant that has no fix, but it can stand alone as an upgrade card if desired.
        assertThat(DevCenter.isDevCenter(new JavaVersionUpgrade(8).getDescriptor())).isTrue();

        // While the OwaspTopTen recipe may serve as a fix recipe for a security issues card, and some of its
        // sub-recipes may be reused in the definition of it, the recipe itself is not a DevCenter card.
        assertThat(DevCenter.isDevCenter(environment.activateRecipes("org.openrewrite.java.security.OwaspTopTen").getDescriptor()))
          .isFalse();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void valid() throws DevCenterValidationException {
        DevCenter devCenter = new DevCenter(starterDevCenter);
        devCenter.validate();

        assertThat(devCenter.getUpgradesAndMigrations())
          .hasSize(3)
          .map(DevCenter.Card::getMeasures)
          .flatMap(Arrays::asList)
          .map(DevCenterMeasure::getInstanceName)
          .contains("Major", "Minor", "Patch", "Completed");

        assertThat(devCenter.getSecurity()).isNotNull();
        assertThat(devCenter.getSecurity().getMeasures()[0].getInstanceName())
          .contains("Zip slip");
    }

    @Test
    void upgradeDoesntRequireFix() throws DevCenterValidationException {
        //language=yaml
        String recipe = """
          type: specs.openrewrite.org/v1beta/recipe
          name: io.moderne.devcenter.JavaVersionNoFix
          displayName: Starter DevCenter Java version upgrade card
          description: Upgrade Java version
          tags:
            - DevCenter:upgradeOrMigration
          recipeList:
            - io.moderne.devcenter.JavaVersionUpgrade:
                majorVersion: 21
          """;
        Recipe r = Environment.builder()
          .load(new YamlResourceLoader(new ByteArrayInputStream(recipe.getBytes(StandardCharsets.UTF_8)),
            URI.create("rewrite.yml"), new Properties()))
          .build()
          .activateRecipes("io.moderne.devcenter.JavaVersionNoFix");
        DevCenter devCenter = new DevCenter(r);
        devCenter.validate();
    }

    @Test
    void validateStandAloneDevCenterRecipe() throws DevCenterValidationException {
        DevCenter devCenter = new DevCenter(starterOriginalSecurity);
        devCenter.validate();
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
                  "Move to Java 21", Java8Plus.ordinal(), Java8Plus.getInstanceName(), "8"),
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
