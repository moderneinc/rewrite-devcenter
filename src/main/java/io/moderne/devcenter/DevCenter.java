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
import java.util.stream.Collectors;

public class DevCenter {
    public static final String DEVCENTER_TAG = "DevCenter:card";
    public static final String FIX_RECIPE_PREFIX = "DevCenter:fix:";

    private final Recipe recipe;

    public DevCenter(Recipe recipe) {
        this.recipe = recipe;
    }

    public static boolean isDevCenter(RecipeDescriptor recipe) {
        if (recipe.getTags().contains(DEVCENTER_TAG)) {
            return true;
        }
        for (RecipeDescriptor subRecipe : recipe.getRecipeList()) {
            if (isDevCenter(subRecipe)) {
                return true;
            }
        }
        return false;
    }

    public void validate() throws DevCenterValidationException {
        List<Card> upgradesAndMigrations = getUpgradesAndMigrations();
        List<Card> security = getSecurityRecursive(recipe, new ArrayList<>());

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

    public List<Card> getUpgradesAndMigrations() {
        return getUpgradesAndMigrationsRecursive(recipe, new ArrayList<>());
    }

    @Nullable
    public Card getSecurity() {
        List<Card> allSecurity = getSecurityRecursive(recipe, new ArrayList<>());
        return allSecurity.isEmpty() ? null : allSecurity.get(0);
    }

    private List<Card> getUpgradesAndMigrationsRecursive(Recipe recipe, List<Card> upgradesAndMigrations) {
        if (recipe.getDescriptor().getTags().contains(DEVCENTER_TAG)) {
            String fixRecipe = fixRecipe(recipe.getDescriptor());
            DevCenterMeasurer devCenterMeasurer = findDevCenterCardRecursive(recipe);
            if (devCenterMeasurer != null && hasUpgradesAndMigrations(recipe)) {
                upgradesAndMigrations.add(new Card(
                        devCenterMeasurer.getInstanceName(),
                        devCenterMeasurer.getName(),
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

    private boolean hasUpgradesAndMigrations(Recipe recipe) {
        for (DataTableDescriptor dataTable : recipe.getDataTableDescriptors()) {
            if (dataTable.getName().equals(UpgradesAndMigrations.class.getName())) {
                return true;
            }
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            if (hasUpgradesAndMigrations(subRecipe)) {
                return true;
            }
        }
        return false;
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

    private List<Card> getSecurityRecursive(Recipe recipe, List<Card> allSecurity) {
        for (String tag : recipe.getTags()) {
            if (tag.startsWith(DEVCENTER_TAG)) {
                for (Recipe subRecipe : recipe.getRecipeList()) {
                    if (subRecipe.getName().equals(ReportAsSecurityIssues.class.getName())) {
                        allSecurity.add(new Card(
                                recipe.getInstanceName(),
                                recipe.getName(),
                                fixRecipe(recipe.getDescriptor()),
                                recipe.getRecipeList().stream().map(Recipe::getInstanceName).collect(Collectors.toList())));
                        return allSecurity;
                    }
                }
            }
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            getSecurityRecursive(subRecipe, allSecurity);
        }
        return allSecurity;
    }

    @Value
    public static class Card {
        String displayName;
        String recipeId;

        @Nullable
        String fixRecipeId;

        List<String> measures;
    }
}
