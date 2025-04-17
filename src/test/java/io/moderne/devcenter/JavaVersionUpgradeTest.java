package io.moderne.devcenter;

import io.moderne.devcenter.table.UpgradesAndMigrations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Completed;
import static io.moderne.devcenter.JavaVersionUpgrade.Measure.Java8Plus;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

public class JavaVersionUpgradeTest implements RewriteTest {

    private static Stream<Arguments> javaVersions() {
        return Stream.of(
          Arguments.of(21, 8, Java8Plus),
          Arguments.of(17, 17, Completed),
          Arguments.of(21, 21, Completed),
          Arguments.of(21, 24, Completed)
        );
    }

    @ParameterizedTest
    @MethodSource("javaVersions")
    void java8(int targetVersion, int actualVersion, JavaVersionUpgrade.Measure measure) {
        rewriteRun(
          spec -> spec
            .recipe(new JavaVersionUpgrade(targetVersion))
            .dataTable(UpgradesAndMigrations.Row.class, rows ->
              assertThat(rows).containsExactly(
                new UpgradesAndMigrations.Row("Move to Java " + targetVersion,
                  measure.ordinal(), measure.toString(), Integer.toString(actualVersion))
              )),
          version(
            //language=java
            java("class Test {}"),
            actualVersion
          )
        );
    }
}
