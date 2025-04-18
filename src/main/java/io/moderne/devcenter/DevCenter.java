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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Recipe;
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.config.RecipeDescriptor;

import java.util.ArrayList;
import java.util.List;

public class DevCenter {
    private final RecipeDescriptor recipeDescriptor;

    public DevCenter(RecipeDescriptor recipeDescriptor) {
        this.recipeDescriptor = recipeDescriptor;
    }

    public void validate() throws DevCenterValidationException {
        List<UpgradeOrMigration> upgradesAndMigrations = getUpgradesAndMigrations();
        List<Security> security = getSecurityRecursive(recipeDescriptor, new ArrayList<>());

        List<String> validationErrors = new ArrayList<>();
        if (upgradesAndMigrations.isEmpty() && security.isEmpty()) {
            validationErrors.add("No recipes included that provide upgrades and migrations or security advice.");
        }
        if (security.size() > 1) {
            validationErrors.add("Only one security recipe can be included.");
        }
        for (UpgradeOrMigration upgradesAndMigration : upgradesAndMigrations) {
            //noinspection ConstantValue
            if (upgradesAndMigration.getFixRecipeId() == null) {
                validationErrors.add("Recipe " + upgradesAndMigration.getRecipeName() +
                                     " is missing a fix recipe. Please add a tag `DevCenter:fix:<RECIPE_ID>` to the recipe.");
            }
        }

        throw new DevCenterValidationException(validationErrors);
    }

    public List<UpgradeOrMigration> getUpgradesAndMigrations() {
        return getUpgradesAndMigrationsRecursive(recipeDescriptor, new ArrayList<>());
    }

    @Nullable
    public Security getSecurity() {
        List<Security> allSecurity = getSecurityRecursive(recipeDescriptor, new ArrayList<>());
        return allSecurity.isEmpty() ? null : allSecurity.get(0);
    }

    private List<UpgradeOrMigration> getUpgradesAndMigrationsRecursive(RecipeDescriptor recipeDescriptor,
                                                                       List<UpgradeOrMigration> upgradesAndMigrations) {
        for (RecipeDescriptor recipe : recipeDescriptor.getRecipeList()) {
            for (DataTableDescriptor dataTable : recipe.getDataTables()) {
                if (dataTable.getName().equals(new UpgradesAndMigrations(Recipe.noop()).getDisplayName())) {
                    //noinspection DataFlowIssue
                    upgradesAndMigrations.add(new UpgradeOrMigration(
                            recipe.getInstanceName(), recipe.getName(), fixRecipe(recipeDescriptor)
                    ));
                }
            }
            for (RecipeDescriptor subRecipe : recipe.getRecipeList()) {
                getUpgradesAndMigrationsRecursive(subRecipe, upgradesAndMigrations);
            }
        }
        return upgradesAndMigrations;
    }

    @Nullable
    private String fixRecipe(RecipeDescriptor recipeDescriptor) {
        for (String tag : recipeDescriptor.getTags()) {
            if (tag.startsWith("DevCenter:fix:")) {
                return tag.substring("DevCenter:fix:".length());
            }
        }
        return null;
    }

    private List<Security> getSecurityRecursive(RecipeDescriptor recipeDescriptor, List<Security> allSecurity) {
        for (RecipeDescriptor recipe : recipeDescriptor.getRecipeList()) {
            for (String tag : recipe.getTags()) {
                if (tag.startsWith("DevCenter:security")) {
                    allSecurity.add(new Security(recipe.getName()));
                }
            }
            for (RecipeDescriptor subRecipe : recipe.getRecipeList()) {
                getSecurityRecursive(subRecipe, allSecurity);
            }
        }
        return allSecurity;
    }

    @Value
    public static class UpgradeOrMigration {
        String displayName;
        String recipeName;
        String fixRecipeId;
    }

    @Value
    public static class Security {
        String recipeId;
    }
}
