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

import io.moderne.devcenter.table.SecurityIssues;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DevCenter {
    private final Recipe recipe;

    public DevCenter(Recipe recipe) {
        this.recipe = recipe;
    }

    public static boolean isDevCenter(RecipeDescriptor recipe) {
        if (recipe.getOptions().stream().noneMatch(OptionDescriptor::isRequired)) {
            for (DataTableDescriptor dataTable : recipe.getDataTables()) {
                if (dataTable.getName().equals(UpgradesAndMigrations.class.getName()) ||
                    dataTable.getName().equals(SecurityIssues.class.getName())) {
                    return true;
                }
            }
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

    public @Nullable Card getSecurity() {
        List<Card> allSecurity = getSecurityRecursive(recipe, new ArrayList<>());
        return allSecurity.isEmpty() ? null : allSecurity.get(0);
    }

    private List<Card> getUpgradesAndMigrationsRecursive(Recipe recipe, List<Card> upgradesAndMigrations) {
        if (recipe instanceof UpgradeMigrationCard) {
            upgradesAndMigrations.add(new Card(
                    recipe.getInstanceName(),
                    recipe.getDescription(),
                    ((UpgradeMigrationCard) recipe).getFixRecipeId(),
                    ((UpgradeMigrationCard) recipe).getMeasures()));
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            getUpgradesAndMigrationsRecursive(subRecipe, upgradesAndMigrations);
        }
        return upgradesAndMigrations;
    }

    private List<Card> getSecurityRecursive(Recipe recipe, List<Card> allSecurity) {
        for (Recipe subRecipe : recipe.getRecipeList()) {
            if (subRecipe instanceof ReportAsSecurityIssues) {
                allSecurity.add(new Card(
                        recipe.getInstanceName(),
                        recipe.getDescription(),
                        ((ReportAsSecurityIssues) subRecipe).getFixRecipe(),
                        recipe.getRecipeList().stream().map(r -> new DevCenterMeasure() {
                            @Override
                            public String getName() {
                                return r.getInstanceName();
                            }

                            @Override
                            public String getDescription() {
                                return r.getDescription();
                            }
                        }).collect(Collectors.toList())));
                return allSecurity;
            }
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            getSecurityRecursive(subRecipe, allSecurity);
        }
        return allSecurity;
    }

    @Value
    public static class Card {
        @NlsRewrite.DisplayName
        String name;

        @NlsRewrite.Description
        String description;

        @Nullable
        String fixRecipeId;

        List<DevCenterMeasure> measures;
    }
}
