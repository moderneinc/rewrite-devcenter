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
package io.moderne.devcenter.eol;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.Validated;
import org.openrewrite.config.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the declarative DevCenter recipes shipped in {@code META-INF/rewrite}. Activating each
 * one parses its YAML and resolves every referenced recipe — the cards (including the per-runtime
 * {@code RuntimeEndOfLife} instances and their {@code runtime} enum values),
 * {@code FindOrganizationStatistics}, and {@code FindCommitters} — so a typo'd id, an invalid enum
 * value, or a missing required option fails here rather than only at install/run time.
 */
class DeclarativeRecipesTest {

    private static final Environment ENVIRONMENT = Environment.builder().scanRuntimeClasspath().build();

    @ParameterizedTest
    @ValueSource(strings = {
            "io.moderne.devcenter.eol.DevCenterEndOfLife",
            "io.moderne.devcenter.eol.DevCenterJvmEndOfLife",
            "io.moderne.devcenter.eol.DevCenterNpmEndOfLife",
            "io.moderne.devcenter.eol.DevCenterNuGetEndOfLife",
            "io.moderne.devcenter.eol.DevCenterRuntimeEndOfLife",
            "io.moderne.devcenter.eol.DevCenterDockerEndOfLife"
    })
    void declarativeRecipeLoadsResolvesAndValidates(String recipeId) {
        Recipe recipe = ENVIRONMENT.activateRecipes(recipeId);
        assertThat(recipe.getName()).isEqualTo(recipeId);
        assertThat(recipe.getRecipeList()).isNotEmpty();
        // validateAll returns a Validated per recipe in the tree; every one must be valid.
        assertThat(recipe.validateAll()).allMatch(Validated::isValid);
    }
}
