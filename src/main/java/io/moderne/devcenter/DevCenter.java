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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.moderne.devcenter.table.SecurityIssues;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.unmodifiableList;

@RequiredArgsConstructor
public class DevCenter {

    /**
     * ExecutionContext message key whose <em>presence</em> requests another scheduler cycle.
     * The key itself is never read and the value is never consumed. Recipes call
     * {@code ctx.putMessage(CYCLE_TRIGGER, true)} for the side effect of flipping
     * {@code WatchableExecutionContext.hasNewMessages}, which {@code RecipeRunCycle}
     * treats as "this recipe made a change" and combines with {@link Recipe#causesAnotherCycle()}
     * to enroll the recipe for the next cycle. The {@code io.moderne.} prefix is required
     * by {@code CursorValidatingExecutionContextView}'s allow-list for ExecutionContext mutations.
     */
    public static final String CYCLE_TRIGGER = "io.moderne.devcenter.cycleTrigger";

    private final Recipe recipe;

    private transient @Nullable List<Card> upgradesAndMigrations;
    private transient @Nullable AtomicReference<Card> securityIssues;

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
        return unmodifiableList(cards);
    }

    public Card getCard(String name) {
        for (Card card : getCards()) {
            if (card.getName().equals(name)) {
                return card;
            }
        }
        throw new IllegalArgumentException("No card found with name: " + name);
    }

    /**
     * Returns a stable JSON description of this DevCenter's structure for
     * cross-classloader consumption by tools (e.g. the Moderne CLI). The
     * format carries an {@code apiVersion} so consumers can detect schema
     * compatibility. Schema (v1):
     * <pre>{@code
     * {
     *   "apiVersion": "v1",
     *   "upgradesAndMigrations": [
     *     {"name": "...", "fixRecipeId": "...", "measures": ["...", ...]},
     *     ...
     *   ],
     *   "security": {"name": "...", "fixRecipeId": "...", "measures": [...]} | null
     * }
     * }</pre>
     * Card and measure ordering is preserved.
     */
    public String getSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "v1");

        List<Map<String, Object>> upgrades = new ArrayList<>();
        for (Card card : getUpgradesAndMigrations()) {
            upgrades.add(cardToSpec(card));
        }
        spec.put("upgradesAndMigrations", upgrades);

        Card securityCard = getSecurity();
        spec.put("security", securityCard == null ? null : cardToSpec(securityCard));

        try {
            return new ObjectMapper().writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize DevCenter spec", e);
        }
    }

    private static Map<String, Object> cardToSpec(Card card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", card.getName());
        map.put("fixRecipeId", card.getFixRecipeId());
        List<String> measureNames = new ArrayList<>();
        for (DevCenterMeasure m : card.getMeasures()) {
            measureNames.add(m.getName());
        }
        map.put("measures", measureNames);
        return map;
    }

    private List<Card> getUpgradesAndMigrationsRecursive(Recipe recipe, List<Card> upgradesAndMigrations) {
        try {
            Class<?> umcClass = Class.forName(
                    "io.moderne.devcenter.UpgradeMigrationCard",
                    false,
                    recipe.getClass().getClassLoader()
            );
            if (umcClass.isInstance(recipe)) {
                //noinspection unchecked
                upgradesAndMigrations.add(new Card(
                        recipe.getInstanceName(),
                        recipe.getDescription(),
                        (String) umcClass.getMethod("getFixRecipeId").invoke(recipe),
                        (List<DevCenterMeasure>) umcClass.getMethod("getMeasures").invoke(recipe),
                        Aggregation.PER_REPOSITORY
                ));
            }
        } catch (ClassNotFoundException e) {
            // Not an UpgradeMigrationCard - ignore
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access UpgradeMigrationCard", e);
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            getUpgradesAndMigrationsRecursive(subRecipe, upgradesAndMigrations);
        }
        return upgradesAndMigrations;
    }

    private static final String REPORT_AS_SECURITY_ISSUES = "io.moderne.devcenter.ReportAsSecurityIssues";

    private List<Card> getSecurityRecursive(Recipe recipe, List<Card> allSecurity) {
        for (Recipe subRecipe : recipe.getRecipeList()) {
            if (REPORT_AS_SECURITY_ISSUES.equals(subRecipe.getClass().getName())) {
                List<DevCenterMeasure> measures = new ArrayList<>();
                List<Recipe> recipeList = recipe.getRecipeList();
                for (int i = 0; i < recipeList.size(); i++) {
                    Recipe r = recipeList.get(i);
                    if (REPORT_AS_SECURITY_ISSUES.equals(r.getClass().getName())) {
                        continue;
                    }
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
                try {
                    allSecurity.add(new Card(
                            recipe.getInstanceName(),
                            recipe.getDescription(),
                            (String) subRecipe.getClass().getMethod("getFixRecipe").invoke(subRecipe),
                            measures,
                            Aggregation.PER_OCCURRENCE
                    ));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to access ReportAsSecurityIssues", e);
                }
                return allSecurity;
            }
        }
        for (Recipe subRecipe : recipe.getRecipeList()) {
            getSecurityRecursive(subRecipe, allSecurity);
        }
        return allSecurity;
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
}
