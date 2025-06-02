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

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Recipe;
import org.openrewrite.config.RecipeDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DevCenter {
    public static final String UPGRADE_OR_MIGRATION_TAG = "DevCenter:upgradeOrMigration";
    public static final String SECURITY_TAG = "DevCenter:security";
    public static final String FIX_RECIPE_PREFIX = "DevCenter:fix:";

    private final Recipe recipe;

    public DevCenter(Recipe recipe) {
        this.recipe = recipe;
    }

    public void validate() throws DevCenterValidationException {
        List<UpgradeOrMigration> upgradesAndMigrations = getUpgradesAndMigrations();
        List<Security> security = getSecurityRecursive(recipe, new ArrayList<>());

        List<String> validationErrors = new ArrayList<>();
        if (upgradesAndMigrations.isEmpty() && security.isEmpty()) {
            validationErrors.add("No recipes included that provide upgrades and migrations or security advice.");
        }
        if (security.size() > 1) {
            validationErrors.add("Only one security recipe can be included.");
        }
        if (!validationErrors.isEmpty()) {
            throw new DevCenterValidationException(validationErrors);
        }
    }

    public List<UpgradeOrMigration> getUpgradesAndMigrations() {
        return getUpgradesAndMigrationsRecursive(recipe, new ArrayList<>());
    }

    @Nullable
    public Security getSecurity() {
        List<Security> allSecurity = getSecurityRecursive(recipe, new ArrayList<>());
        return allSecurity.isEmpty() ? null : allSecurity.get(0);
    }

    private List<UpgradeOrMigration> getUpgradesAndMigrationsRecursive(Recipe recipe,
                                                                       List<UpgradeOrMigration> upgradesAndMigrations) {
        if (recipe.getDescriptor().getTags().contains(UPGRADE_OR_MIGRATION_TAG)) {
            String fixRecipe = fixRecipe(recipe.getDescriptor());
            DevCenterMeasurer devCenterMeasurer = findDevCenterCardRecursive(recipe);
            if (devCenterMeasurer != null) {
                upgradesAndMigrations.add(new UpgradeOrMigration(
                        devCenterMeasurer.getInstanceName(),
                        recipe.getName(),
                        fixRecipe,
                        devCenterMeasurer.getMeasures()));
            }
        } else {
            for (Recipe subRecipe : recipe.getRecipeList()) {
                getUpgradesAndMigrationsRecursive(subRecipe, upgradesAndMigrations);
            }
        }
        return upgradesAndMigrations;
    }

    private @Nullable DevCenterMeasurer findDevCenterCardRecursive(Recipe recipe) {
        if (recipe instanceof DevCenterMeasurer) {
            return (DevCenterMeasurer) recipe;
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            DevCenterMeasurer devCenterMeasurer = findDevCenterCardRecursive(subRecipe);
            if (devCenterMeasurer != null) {
                return devCenterMeasurer;
            }
        }
        return null;
    }

    @Nullable
    private String fixRecipe(RecipeDescriptor recipeDescriptor) {
        for (String tag : recipeDescriptor.getTags()) {
            if (tag.startsWith(FIX_RECIPE_PREFIX)) {
                return tag.substring(FIX_RECIPE_PREFIX.length());
            }
        }
        return null;
    }

    private List<Security> getSecurityRecursive(Recipe recipe, List<Security> allSecurity) {
        for (String tag : recipe.getTags()) {
            if (tag.startsWith(SECURITY_TAG)) {
                allSecurity.add(new Security(
                        recipe.getDisplayName(),
                        recipe.getRecipeList().stream().map(Recipe::getInstanceName).collect(Collectors.toList()),
                        fixRecipe(recipe.getDescriptor())));
            }
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            getSecurityRecursive(subRecipe, allSecurity);
        }
        return allSecurity;
    }

    @Value
    public static class UpgradeOrMigration {
        String displayName;
        String recipeId;

        @Nullable
        String fixRecipeId;

        List<String> measures;
    }

    @Value
    public static class Security {
        String recipeId;
        List<String> measures;

        @Nullable
        String fixRecipeId;
    }
}
