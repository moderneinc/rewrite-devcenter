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
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.openrewrite.java.Assertions.java;

class BucketedMetricCardTest implements RewriteTest {

    private static final String CARD_RECIPE_NAME = "io.moderne.devcenter.test.EmitAndBucket";

    @Language("yaml")
    private static String pipelineYaml(String aggregation, String values) {
        return """
          type: specs.openrewrite.org/v1beta/recipe
          name: %s
          displayName: Emit then bucket
          description: Pipeline that emits a data table and then buckets it.
          recipeList:
            - io.moderne.devcenter.EmitLcomValues:
                values: %s
            - io.moderne.devcenter.BucketedMetricCard:
                inputDataTable: io.moderne.devcenter.LcomTable
                cardName: Class cohesion
                column: lcom4
                aggregation: %s
                buckets:
                  - name: LOW
                    moreThan: 10
                  - name: MEDIUM
                    moreThan: 3
                  - name: HIGH
                    moreThan: 0
          """.formatted(CARD_RECIPE_NAME, values, aggregation);
    }

    @Test
    void averageLandsInMedium() {
        // AVERAGE of [2, 4, 6] = 4 → MEDIUM (largest moreThan ≤ 4 is 3) → ordinal 1
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("AVERAGE", "[2.0, 4.0, 6.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 1, "MEDIUM", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void maxLandsInLow() {
        // MAX of [2, 4, 12] = 12 → LOW (largest moreThan ≤ 12 is 10) → ordinal 0
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("MAX", "[2.0, 4.0, 12.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 0, "LOW", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void minLandsInHigh() {
        // MIN of [2, 4, 12] = 2 → HIGH (largest moreThan ≤ 2 is 0) → ordinal 2
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("MIN", "[2.0, 4.0, 12.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 2, "HIGH", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void sumLandsInLow() {
        // SUM of [4, 4, 4] = 12 → LOW (largest moreThan ≤ 12 is 10) → ordinal 0
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("SUM", "[4.0, 4.0, 4.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 0, "LOW", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void countLandsInMedium() {
        // COUNT of 5 rows = 5 → MEDIUM (largest moreThan ≤ 5 is 3) → ordinal 1
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("COUNT", "[1.0, 1.0, 1.0, 1.0, 1.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 1, "MEDIUM", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void uniqueCountsDistinctValues() {
        // Distinct values in [2, 4, 2, 4, 6] = {2, 4, 6} → 3 → MEDIUM (largest moreThan ≤ 3 is 3) → ordinal 1
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("UNIQUE", "[2.0, 4.0, 2.0, 4.0, 6.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 1, "MEDIUM", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void aggregationIsCaseInsensitive() {
        // lowercase "max" should still resolve to MAX
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("max", "[2.0, 4.0, 12.0]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 0, "LOW", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void noRowsWhenAverageHasNoInput() {
        // AVERAGE returns null on empty input → no row inserted
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("AVERAGE", "[]"), CARD_RECIPE_NAME)
            .afterRecipe(run -> assertThat(run.<UpgradesAndMigrations.Row>getDataTableRows(
              "io.moderne.devcenter.table.UpgradesAndMigrations",
              "io.moderne.devcenter.upgradesAndMigrations"))
              .isEmpty()),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void countOnEmptyInputStillInserts() {
        // COUNT of 0 rows = 0 → HIGH (largest moreThan ≤ 0 is 0) → ordinal 2
        rewriteRun(
          spec -> spec
            .recipeFromYaml(pipelineYaml("COUNT", "[]"), CARD_RECIPE_NAME)
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Class cohesion", 2, "HIGH", null)
              )),
          //language=java
          java("class Test {}")
        );
    }

    @Test
    void measuresFollowListOrder() {
        BucketedMetricCard card = new BucketedMetricCard(
                LcomTable.class.getName(),
                "Class cohesion",
                "lcom4",
                AggregationFunction.AVERAGE,
                new Bucket[]{
                        new Bucket("LOW", 10),
                        new Bucket("MEDIUM", 3),
                        new Bucket("HIGH", 0)
                });

        assertThat(card.getMeasures())
          .extracting(DevCenterMeasure::getName, DevCenterMeasure::ordinal)
          .containsExactly(
            tuple("LOW", 0),
            tuple("MEDIUM", 1),
            tuple("HIGH", 2));
    }
}
