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
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.moderne.devcenter.LibraryUpgrade.Measure.Minor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

public class LibraryUpgradeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LibraryUpgrade("Move to latest Jackson",
          "com.fasterxml*", "*", "2.16.0"));
    }

    @Test
    void libraryUpgrade() {
        rewriteRun(
          spec -> spec.dataTable(UpgradesAndMigrations.Row.class, rows ->
            assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to latest Jackson",
                Minor.ordinal(), Minor.toString(), "2.12.3")
            )),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.module</groupId>
                        <artifactId>jackson-module-parameter-names</artifactId>
                        <version>2.12.3</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <!--~~>--><dependency>
                        <groupId>com.fasterxml.jackson.module</groupId>
                        <artifactId>jackson-module-parameter-names</artifactId>
                        <version>2.12.3</version>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}
