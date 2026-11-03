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
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class QuarkusDevCenterTest implements RewriteTest {

    private static Recipe quarkusVersionCard() {
        Recipe devCenter = Environment.builder()
          .scanRuntimeClasspath("io.moderne.devcenter")
          .build()
          .activateRecipes("io.moderne.devcenter.QuarkusDevCenter");
        return devCenter.getRecipeList().stream()
          .filter(r -> r instanceof LibraryUpgrade && "Move to Quarkus 4.0".equals(((LibraryUpgrade) r).getCardName()))
          .findFirst()
          .orElseThrow();
    }

    @Test
    void measuresTheQuarkusPlatformVersion() {
        // quarkus-core 3.3.1 brings io.quarkus:quarkus-fs-util 0.0.9, which has a version line of its own
        rewriteRun(
          spec -> spec
            .recipe(quarkusVersionCard())
            .dataTable(UpgradesAndMigrations.Row.class, rows -> assertThat(rows).containsExactly(
              new UpgradesAndMigrations.Row("Move to Quarkus 4.0", SemverMeasure.Major.ordinal(), SemverMeasure.Major.toString(), "3.3.1")
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
                        <groupId>io.quarkus</groupId>
                        <artifactId>quarkus-core</artifactId>
                        <version>3.3.1</version>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}
