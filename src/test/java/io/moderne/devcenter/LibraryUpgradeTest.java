package io.moderne.devcenter;

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.moderne.devcenter.LibraryUpgrade.Measures.Minor;
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
