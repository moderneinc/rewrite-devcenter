/*
 * Copyright 2026 the original author or authors.
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
import org.junit.jupiter.api.Test;
import org.openrewrite.DataTableExecutionContextView;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryDataTableStore;
import org.openrewrite.InMemoryExecutionContext;

import static org.assertj.core.api.Assertions.assertThat;

class UpgradeMigrationCardGroupTest {

    @Test
    void multipleCardsShareSameDataTable() {
        JavaVersionUpgrade javaCard = new JavaVersionUpgrade(25, null);
        JUnitJupiterUpgrade junitCard = new JUnitJupiterUpgrade();
        BuildToolCard gradleCard = new BuildToolCard("Upgrade to Gradle 9", "Gradle", "9.0.0", null);

        // In production, the platform sets each DataTable's instance name
        // from the owning recipe, producing distinct bucket keys per card
        javaCard.upgradesAndMigrations.withInstanceName(javaCard::getInstanceName);
        junitCard.upgradesAndMigrations.withInstanceName(junitCard::getInstanceName);
        gradleCard.upgradesAndMigrations.withInstanceName(gradleCard::getInstanceName);

        InMemoryDataTableStore store = new InMemoryDataTableStore();
        ExecutionContext ctx = new InMemoryExecutionContext();
        DataTableExecutionContextView.view(ctx).setDataTableStore(store);

        javaCard.upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                "Move to Java 25", 0, "Java 8+", "8"));
        junitCard.upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                "Move to JUnit 6", 0, "JUnit 4", "4.13"));
        gradleCard.upgradesAndMigrations.insertRow(ctx, new UpgradesAndMigrations.Row(
                "Upgrade to Gradle 9", 0, "Major", "8.5.0"));

        // All three cards wrote to the same bucket because they share a group
        assertThat(store.getDataTables()).hasSize(1);
    }
}
