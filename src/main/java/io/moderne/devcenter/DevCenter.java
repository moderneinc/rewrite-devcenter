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
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DevCenter {
    private final Recipe recipe;

    private transient @Nullable List<Card> upgradesAndMigrations;
    private transient @Nullable AtomicReference<Card> securityIssues;

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
        Map<String, Integer> countByName = new HashMap<>();
        for (Card c : upgradesAndMigrations) {
            countByName.merge(c.getName(), 1, Integer::sum);
        }
        for (Card c : security) {
            countByName.merge(c.getName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : countByName.entrySet()) {
            if (entry.getValue() > 1) {
                validationErrors.add("Card names must be unique. The name '" + entry.getKey() + "' is included multiple times.");
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new DevCenterValidationException(validationErrors);
        }
    }

    public List<Card> getUpgradesAndMigrations() {
        if (upgradesAndMigrations == null) {
            upgradesAndMigrations = getUpgradesAndMigrationsRecursive(recipe, new ArrayList<>());
        }
        return upgradesAndMigrations;
    }

    public @Nullable Card getSecurity() {
        if (securityIssues == null) {
            List<Card> allSecurity = getSecurityRecursive(recipe, new ArrayList<>());
            //noinspection DataFlowIssue
            securityIssues = new AtomicReference<>(allSecurity.isEmpty() ? null : allSecurity.get(0));
        }
        return securityIssues.get();
    }

    public List<Card> getCards() {
        List<Card> cards = new ArrayList<>(getUpgradesAndMigrations());
        Card securityCard = getSecurity();
        if (securityCard != null) {
            cards.add(securityCard);
        }
        return Collections.unmodifiableList(cards);
    }

    public Card getCard(String name) {
        for (Card card : getCards()) {
            if (card.getName().equals(name)) {
                return card;
            }
        }
        throw new IllegalArgumentException("No card found with name: " + name);
    }

    private List<Card> getUpgradesAndMigrationsRecursive(Recipe recipe, List<Card> upgradesAndMigrations) {
        try {
            String className = "io.moderne.devcenter.UpgradeMigrationCard";
            if (instanceOfByFqn(recipe.getClass(), className)) {
                Class<?> upgradeMigrationClass = Class.forName(className, true, recipe.getClass().getClassLoader());
                String fixRecipeId = (String) upgradeMigrationClass
                        .getMethod("getFixRecipeId")
                        .invoke(recipe);

                //noinspection unchecked
                List<DevCenterMeasure> measures = (List<DevCenterMeasure>) upgradeMigrationClass
                        .getMethod("getMeasures")
                        .invoke(recipe);
                upgradesAndMigrations.add(new Card(
                        recipe.getInstanceName(),
                        recipe.getDescription(),
                        fixRecipeId,
                        measures,
                        Aggregation.PER_REPOSITORY));
            }
            for (Recipe subRecipe : recipe.getRecipeList()) {
                getUpgradesAndMigrationsRecursive(subRecipe, upgradesAndMigrations);
            }
            return upgradesAndMigrations;
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Card> getSecurityRecursive(Recipe recipe, List<Card> allSecurity) {
        try {
            for (Recipe subRecipe : recipe.getRecipeList()) {
                String className = "io.moderne.devcenter.ReportAsSecurityIssues";
                if (instanceOfByFqn(subRecipe.getClass(), className)) {
                    Class<?> reportAsSecurityIssuesClass = Class.forName(
                            className,
                            true,
                            subRecipe.getClass().getClassLoader()
                    );
                    String fixRecipe = (String) reportAsSecurityIssuesClass
                            .getMethod("getFixRecipe")
                            .invoke(subRecipe);

                    List<DevCenterMeasure> measures = new ArrayList<>();
                    List<Recipe> recipeList = recipe.getRecipeList();
                    for (int i = 0; i < recipeList.size(); i++) {
                        Recipe r = recipeList.get(i);
                        int ordinal = i;
                        DevCenterMeasure devCenterMeasure = new DevCenterMeasure() {
                            @Override
                            public String getName() {
                                return r.getInstanceName();
                            }

                            @Override
                            public String getDescription() {
                                return r.getDescription();
                            }

                            @Override
                            public int ordinal() {
                                return ordinal;
                            }
                        };
                        measures.add(devCenterMeasure);
                    }

                    allSecurity.add(new Card(
                            recipe.getInstanceName(),
                            recipe.getDescription(),
                            fixRecipe,
                            measures,
                            Aggregation.PER_OCCURRENCE));
                    return allSecurity;
                }
            }
            for (Recipe subRecipe : recipe.getRecipeList()) {
                getSecurityRecursive(subRecipe, allSecurity);
            }
            return allSecurity;

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Value
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    public static class Card {
        @EqualsAndHashCode.Include
        @NlsRewrite.DisplayName
        String name;

        @NlsRewrite.Description
        String description;

        @Nullable
        String fixRecipeId;

        List<DevCenterMeasure> measures;

        Aggregation aggregation;
    }

    public enum Aggregation {
        PER_OCCURRENCE,
        PER_REPOSITORY
    }

    private boolean instanceOfByFqn(@Nullable Class<?> current, String expectedClassName) {
        if (current == null) {
            return false;
        }
        if (current.getName().equals(expectedClassName)) {
            return true;
        }
        return instanceOfByFqn(current.getSuperclass(), expectedClassName);
    }
}
