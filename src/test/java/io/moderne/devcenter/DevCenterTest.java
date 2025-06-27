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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.test.RewriteTest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static io.moderne.devcenter.JUnitJupiterUpgrade.Measure.JUnit4;
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

        // Since this recipe has options, it cannot be a standalone DevCenter.
        assertThat(DevCenter.isDevCenter(new JavaVersionUpgrade(8, null).getDescriptor())).isFalse();

        // This is the variant that has no fix, but it can stand alone as an upgrade card if desired.
        assertThat(DevCenter.isDevCenter(new JUnitJupiterUpgrade().getDescriptor())).isTrue();

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

        assertThat(devCenter.getUpgradesAndMigrations()).hasSize(3);
        assertThat(devCenter.getUpgradesAndMigrations().getFirst().getMeasures().stream())
          .map(DevCenterMeasure::getName)
          .containsExactly("Major", "Minor", "Patch", "Completed");

        assertThat(devCenter.getSecurity()).isNotNull();
        assertThat(devCenter.getSecurity().getMeasures().stream())
          .map(DevCenterMeasure::getName)
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
    void uniqueCardNames() {
        //language=yaml
        String recipe = """
          type: specs.openrewrite.org/v1beta/recipe
          name: io.moderne.devcenter.DoubleJava21Misconfiguration
          displayName: Starter DevCenter Java version upgrade card
          description: Upgrade Java version
          recipeList:
            - io.moderne.devcenter.JavaVersionUpgrade:
                majorVersion: 21
            - io.moderne.devcenter.JavaVersionUpgrade:
                majorVersion: 21
          """;
        Recipe r = Environment.builder()
          .load(new YamlResourceLoader(new ByteArrayInputStream(recipe.getBytes(StandardCharsets.UTF_8)),
            URI.create("rewrite.yml"), new Properties()))
          .build()
          .activateRecipes("io.moderne.devcenter.DoubleJava21Misconfiguration");
        DevCenter devCenter = new DevCenter(r);
        assertThatThrownBy(devCenter::validate).isInstanceOf(DevCenterValidationException.class)
          .hasMessageContaining("Card names must be unique. The name 'Move to Java 21' is included multiple times.");
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
                  "Move to Java 21", Java8Plus.ordinal(), Java8Plus.getName(), "8"),
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("recipesFromIsolatedClassLoader")
    void recipeFromDifferentClassLoaderIdentifiesCards(String type, Recipe recipe) throws Exception {
        // Create DevCenter instance and verify it correctly identifies the card
        // This test will fail with the current instanceof-based implementation,
        // demonstrating that the reflection-based approach is needed
        DevCenter devCenter = new DevCenter(recipe);
        devCenter.validate();
    }

    private static Stream<Arguments> recipesFromIsolatedClassLoader() throws ReflectiveOperationException {
        ClassLoader isolatedClassLoader = new ClassLoader("isolated", null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                // Load io.moderne.devcenter classes (including UpgradeMigrationCard) in this classloader
                // to ensure they are different from the ones in the test classloader
                if (name.startsWith("io.moderne.devcenter.") &&
                    !name.startsWith("io.moderne.devcenter.DevCenter")) {
                    // Get the resource path for the class
                    String resourcePath = name.replace('.', '/') + ".class";
                    try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (inputStream != null) {
                            byte[] classData = inputStream.readAllBytes();
                            return defineClass(name, classData, 0, classData.length);
                        }
                    } catch (Exception e) {
                        throw new ClassNotFoundException("Failed to load " + name, e);
                    }
                }
                // Delegate all other classes to the system classloader
                return ClassLoader.getSystemClassLoader().loadClass(name);
            }
        };

        Class<?> recipeClass = isolatedClassLoader.loadClass("io.moderne.devcenter.JavaVersionUpgrade");
        Recipe recipe = (Recipe) recipeClass.getConstructor(int.class, String.class)
          .newInstance(21, "org.openrewrite.java.migrate.Java21");

        return Stream.of(
          Arguments.of("Imperative recipe", recipe),
          Arguments.of("Declarative recipe", new Recipe() {
              @Override
              public String getDisplayName() {
                  return "Parent loaded";
              }

              @Override
              public String getDescription() {
                  return "Simulates `DeclarativeRecipe`, which is parent loaded and contains " +
                         "child loaded sub-recipes.";
              }

              @Override
              public List<Recipe> getRecipeList() {
                  return List.of(recipe);
              }
          })
        );
    }
}
