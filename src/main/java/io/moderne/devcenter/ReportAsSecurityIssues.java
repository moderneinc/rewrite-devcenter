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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.RecipesThatMadeChanges;

import java.util.List;

@SuppressWarnings("unused")
public class ReportAsSecurityIssues extends Recipe {
    private final transient SecurityIssues securityIssues = new SecurityIssues(this);

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
                    for (List<Recipe> recipeStack : changes.getRecipes()) {
                        for (int i = 0; i < recipeStack.size(); i++) {
                            Recipe recipe = recipeStack.get(i);
                            if (recipe.getTags().contains("DevCenter:security")) {
                                Recipe measure = recipeStack.get(i + 1);
                                List<Recipe> recipeList = recipe.getRecipeList();
                                for (int j = 0; j < recipeList.size(); j++) {
                                    if (recipeList.get(j).getName().equals(measure.getName())) {
                                        securityIssues.insertRow(ctx, new SecurityIssues.Row(
                                                j, measure.getInstanceName()));
                                        break;
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
