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
                            if (recipeStack.get(i).getTags().contains("DevCenter:security")) {
                                securityIssues.insertRow(ctx, new SecurityIssues.Row(
                                        recipeStack.get(i + 1).getInstanceName()
                                ));
                            }
                        }
                    }
                });
                return tree;
            }
        };
    }
}
