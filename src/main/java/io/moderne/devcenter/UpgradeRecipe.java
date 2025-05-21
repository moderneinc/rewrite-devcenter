package io.moderne.devcenter;

import org.openrewrite.Recipe;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class UpgradeRecipe extends Recipe {

    /**
     * Returns an ordered list of the measures used by this recipe.
     * These are the names used in the `value` column in the data table. The measure names will be exposed as tags on the recipe to allow
     * showing any measures with 0 results in the DevCenter UI.
     */
    public abstract List<String> measureNames();

    @Override
    public Set<String> getTags() {
        return measureNames().stream()
                .map(measure -> "DevCenter:measure:" + measure)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
