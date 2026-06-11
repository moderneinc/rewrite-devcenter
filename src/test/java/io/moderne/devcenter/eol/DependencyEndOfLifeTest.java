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
package io.moderne.devcenter.eol;

import io.moderne.devcenter.eol.table.EndOfLifeReport;
import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.test.RewriteTest;

import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class DependencyEndOfLifeTest implements RewriteTest {

    // Reference date held constant; assertions run against the fixed test feed (not the bundled
    // snapshot) so they don't break when the bundled feed's real dates are regenerated.
    private static final String AS_OF = "2026-05-28";
    private static final String TEST_FEED = "/feeds/test-feed.yaml";

    private DependencyEndOfLife springBootCard() {
        return new DependencyEndOfLife("JVM dependency end of life", null, AS_OF, TEST_FEED,
                Collections.singletonList("spring-boot"), null);
    }

    private static Stream<Arguments> springBootVersions() {
        return Stream.of(
                // version, cycle, eol date, expected status
                Arguments.of("3.4.0", "3.4", "2025-12-31", EolStatus.EndOfLife),
                Arguments.of("3.5.0", "3.5", "2026-06-30", EolStatus.EndOfLifeApproaching),
                Arguments.of("4.0.0", "4.0", "2026-12-31", EolStatus.Supported)
        );
    }

    @MethodSource("springBootVersions")
    @ParameterizedTest
    void classifiesSpringBootByCycle(String version, String cycle, String eolDate, EolStatus expected) {
        rewriteRun(
                spec -> spec
                        .recipe(springBootCard())
                        .dataTable(UpgradesAndMigrations.Row.class, rows ->
                                assertThat(rows).containsExactly(new UpgradesAndMigrations.Row(
                                        "JVM dependency end of life",
                                        expected.ordinal(),
                                        expected.name(),
                                        version)))
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "JVM",
                                        "dependency",
                                        "org.springframework.boot:spring-boot",
                                        version,
                                        "spring-boot",
                                        cycle,
                                        eolDate,
                                        expected.name()))),
                //language=xml
                pomXml(
                        """
                          <project>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0-SNAPSHOT</version>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot</artifactId>
                                    <version>%s</version>
                                </dependency>
                            </dependencies>
                          </project>
                          """.formatted(version)
                )
        );
    }

    @DocumentExample
    @Test
    void reportsWorstStatusPerRepository() {
        rewriteRun(
                spec -> spec
                        .recipe(springBootCard())
                        // A repo with both an end-of-life (3.4) and an approaching (3.5) dependency
                        // must surface as EndOfLife: UpgradesAndMigrations keeps the worst measure.
                        .dataTable(UpgradesAndMigrations.Row.class, rows -> {
                            assertThat(rows).isNotEmpty();
                            int worstOrdinal = rows.stream()
                                    .mapToInt(UpgradesAndMigrations.Row::getOrdinal)
                                    .min()
                                    .orElseThrow();
                            assertThat(worstOrdinal).isEqualTo(EolStatus.EndOfLife.ordinal());
                        }),
                //language=xml
                pomXml(
                        """
                          <project>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0-SNAPSHOT</version>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot</artifactId>
                                    <version>3.4.0</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-actuator</artifactId>
                                    <version>3.5.0</version>
                                </dependency>
                            </dependencies>
                          </project>
                          """
                )
        );
    }

    @Test
    void invalidAsOfFailsValidation() {
        ExecutionContext ctx = new InMemoryExecutionContext();
        assertThat(new DependencyEndOfLife("c", null, "not-a-date", null, null, null).validate(ctx).isValid()).isFalse();
        assertThat(new DependencyEndOfLife("c", null, "2026-05-28", null, null, null).validate(ctx).isValid()).isTrue();
        assertThat(new DependencyEndOfLife("c", null, null, null, null, null).validate(ctx).isValid()).isTrue();
    }

    @Test
    void reportOnlyUnlessFixRecipeProvided() {
        assertThat(new DependencyEndOfLife("c", null, null, null, null, null).getFixRecipeId()).isNull();
        assertThat(new DependencyEndOfLife("c", null, null, null, null, "com.example.Fix").getFixRecipeId())
                .isEqualTo("com.example.Fix");
    }

    @Test
    void ignoresUntrackedDependencies() {
        rewriteRun(
                spec -> spec
                        .recipe(springBootCard())
                        // No tracked dependency is present, so no UpgradesAndMigrations rows are emitted.
                        .afterRecipe(run -> assertThat(run.getDataTableRows(UpgradesAndMigrations.class))
                                .isEmpty()),
                //language=xml
                pomXml(
                        """
                          <project>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0-SNAPSHOT</version>
                            <dependencies>
                                <dependency>
                                    <groupId>com.google.guava</groupId>
                                    <artifactId>guava</artifactId>
                                    <version>33.0.0-jre</version>
                                </dependency>
                            </dependencies>
                          </project>
                          """
                )
        );
    }

    @Test
    void tracksInternalProductFromOrgSuppliedFeed() {
        // Demonstrates the `feed` override: an internal product, tracked from a hand-authored feed
        // that the bundled endoflife.date snapshot knows nothing about.
        rewriteRun(
                spec -> spec
                        .recipe(new DependencyEndOfLife("JVM dependency end of life", null, AS_OF,
                                "/feeds/test-feed.yaml", null, null))
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).contains(new EndOfLifeReport.Row(
                                        "JVM",
                                        "dependency",
                                        "com.google.guava:guava",
                                        "33.0.0-jre",
                                        "guava",
                                        "33",
                                        "2020-01-01",
                                        EolStatus.EndOfLife.name()))),
                //language=xml
                pomXml(
                        """
                          <project>
                            <groupId>com.example</groupId>
                            <artifactId>example</artifactId>
                            <version>1.0-SNAPSHOT</version>
                            <dependencies>
                                <dependency>
                                    <groupId>com.google.guava</groupId>
                                    <artifactId>guava</artifactId>
                                    <version>33.0.0-jre</version>
                                </dependency>
                            </dependencies>
                          </project>
                          """
                )
        );
    }
}
