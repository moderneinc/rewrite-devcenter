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
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.json.Assertions.json;

class NpmDependencyEndOfLifeTest implements RewriteTest {

    private NpmDependencyEndOfLife card() {
        return new NpmDependencyEndOfLife("npm dependency end of life", null, "2026-05-28",
                "/feeds/test-feed.yaml", null, null);
    }

    @DocumentExample
    @Test
    void classifiesNpmDependencyByDeclaredRange() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactly(new EndOfLifeReport.Row(
                                        "npm",
                                        "dependency",
                                        "express",
                                        "4.18.2",
                                        "express",
                                        "4",
                                        "2024-01-01",
                                        EolStatus.EndOfLife.name()))),
                //language=json
                json(
                        """
                          {
                            "name": "app",
                            "dependencies": {
                              "express": "^4.18.2"
                            }
                          }
                          """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void scansDevAndPeerDependencyBlocksBeyondRuntimeDependencies() {
        // The happy-path test covers `dependencies`; npm also declares versions under
        // devDependencies/optionalDependencies/peerDependencies, all of which must be scanned.
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .dataTable(EndOfLifeReport.Row.class, rows ->
                                assertThat(rows).containsExactlyInAnyOrder(
                                        new EndOfLifeReport.Row("npm", "dependency", "express", "4.18.2",
                                                "express", "4", "2024-01-01", EolStatus.EndOfLife.name()),
                                        new EndOfLifeReport.Row("npm", "dependency", "express", "5.0.0",
                                                "express", "5", "2030-01-01", EolStatus.Supported.name()))),
                //language=json
                json(
                        """
                          {
                            "name": "app",
                            "devDependencies": { "express": "~4.18.2" },
                            "peerDependencies": { "express": "^5.0.0" }
                          }
                          """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void ignoresUntrackedAndUnpinnedDependencies() {
        rewriteRun(
                spec -> spec
                        .recipe(card())
                        .afterRecipe(run -> assertThat(run.getDataTableRows(EndOfLifeReport.class)).isEmpty()),
                //language=json
                json(
                        """
                          {
                            "name": "app",
                            "dependencies": {
                              "left-pad": "1.3.0",
                              "express": "*"
                            }
                          }
                          """,
                        spec -> spec.path("package.json")
                )
        );
    }
}
