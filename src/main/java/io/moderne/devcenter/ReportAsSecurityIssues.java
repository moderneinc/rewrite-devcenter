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
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.marker.RecipesThatMadeChanges;

import java.util.List;

@SuppressWarnings("unused")
@Value
@EqualsAndHashCode(callSuper = false)
public class ReportAsSecurityIssues extends Recipe {
    private final transient SecurityIssues securityIssues = new SecurityIssues(this);

    @Option(displayName = "Fix recipe",
            description = "The recipe to use to fix these issues.",
            example = "org.openrewrite.java.security.OwaspTopTen",
            required = false)
    @Nullable
    String fixRecipe;

    @Override
    public String getDisplayName() {
        return "Report as security issues";
    }

    @Override
    public String getInstanceName() {
        return "Security DevCenter";
    }

    @Override
    public String getDescription() {
        return "Look for results produced by recipes in the same recipe list that this recipe is part of, " +
               "and report them as security issues in DevCenter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                tree.getMarkers().findFirst(RecipesThatMadeChanges.class).ifPresent(changes -> {
                    nextChange:
                    for (List<Recipe> recipeStack : changes.getRecipes()) {
                        for (int i = 0; i < recipeStack.size(); i++) {
                            Recipe recipe = recipeStack.get(i);
                            for (Recipe subRecipe : recipe.getRecipeList()) {
                                if (subRecipe == ReportAsSecurityIssues.this) {
                                    Recipe measure = recipeStack.get(i + 1);
                                    List<Recipe> recipeList = recipe.getRecipeList();
                                    for (int j = 0; j < recipeList.size(); j++) {
                                        if (recipeList.get(j).getName().equals(measure.getName())) {
                                            securityIssues.insertRow(ctx, new SecurityIssues.Row(
                                                    j, measure.getInstanceName()));
                                            continue nextChange;
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
                return tree;
            }
        };
    }
}
