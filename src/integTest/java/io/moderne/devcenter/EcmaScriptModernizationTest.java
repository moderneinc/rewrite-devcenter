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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static io.moderne.devcenter.EcmaScriptModernization.Measure.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

@SuppressWarnings({"ES6ConvertVarToLetConst", "JSUnusedLocalSymbols", "JSUnresolvedReference"})
class EcmaScriptModernizationTest implements RewriteTest {

    private final EcmaScriptModernization recipe = new EcmaScriptModernization(null);

    @AfterEach
    void after() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe);
    }

    private void expect(RecipeSpec spec, EcmaScriptModernization.Measure measure, String share) {
        spec.dataTable(UpgradesAndMigrations.Row.class, rows ->
          assertThat(rows).containsExactly(
            new UpgradesAndMigrations.Row("Move to ECMAScript 6", recipe.ordinal(measure), measure.getName(), share)
          ));
    }

    @DocumentExample
    @Test
    void mostlyVarInModule() {
        rewriteRun(
          spec -> expect(spec, MostlyVar, "67%"),
          javascript(
            """
              import fs from "fs";
              var a = 1;
              let b = 2;
              var c = 3;
              """,
            spec -> spec.path("src/index.js")
          )
        );
    }

    @Test
    void topLevelVarInScriptIsNotCounted() {
        rewriteRun(
          spec -> expect(spec, PartlyVar, "50%"),
          javascript(
            """
              var a = 1;
              if (a) {
                  var b = 2;
              }
              function f() {
                  var c = 3;
                  let d = 4;
              }
              """,
            spec -> spec.path("src/script.js")
          )
        );
    }

    @Test
    void topLevelVarInCommonJsModuleIsCounted() {
        rewriteRun(
          spec -> expect(spec, MostlyVar, "100%"),
          javascript(
            """
              var fs = require("fs");
              var a = 1;
              """,
            spec -> spec.path("src/index.js")
          )
        );
    }

    @Test
    void varInNestedFunctionsOfScriptIsCounted() {
        rewriteRun(
          spec -> expect(spec, MostlyVar, "67%"),
          javascript(
            """
              const o = {
                  m: function () {
                      var a = 1;
                  },
                  n: () => {
                      var b = 2;
                  }
              };
              """,
            spec -> spec.path("src/script.js")
          )
        );
    }

    @Test
    void littleVar() {
        rewriteRun(
          spec -> expect(spec, LittleVar, "9%"),
          javascript(
            """
              export function f() {
                  var a = 1;
                  let b = 2, c = 3;
                  const d = 4, e = 5, g = 6;
                  for (let i = 0; i < 1; i++) {
                  }
                  for (const x of []) {
                  }
                  let h = 7;
                  const j = 8;
                  let k = 9;
                  const l = 10;
                  let m = 11;
                  const n = 12;
              }
              """,
            spec -> spec.path("src/index.js")
          )
        );
    }

    @Test
    void completedWithOnlyLetAndConst() {
        rewriteRun(
          spec -> expect(spec, Completed, "0%"),
          javascript(
            """
              let a = 1;
              const b = 2;
              """,
            spec -> spec.path("src/index.js")
          )
        );
    }

    @Test
    void exportedAndAmbientVarIsNotCounted() {
        rewriteRun(
          spec -> expect(spec, Completed, "0%"),
          typescript(
            """
              export var a = 1;
              declare var b: number;
              declare namespace N {
                  var c: number;
              }
              let d = 4;
              """,
            spec -> spec.path("src/index.ts")
          )
        );
    }

    @Test
    void declarationFilesAreSkipped() {
        rewriteRun(
          spec -> expect(spec, Completed, "0%"),
          typescript(
            """
              import "./polyfills";
              var a: number;
              """,
            spec -> spec.path("src/types.d.ts")
          ),
          typescript(
            "export const b = 2;",
            spec -> spec.path("src/index.ts")
          )
        );
    }

    @Test
    void vendoredAndBundledFilesAreSkipped() {
        rewriteRun(
          spec -> expect(spec, Completed, "0%"),
          javascript(
            """
              function g() {
                  var b = 2;
              }
              """,
            spec -> spec.path("public/app.bundle.js")
          ),
          javascript(
            "export const c = 3;",
            spec -> spec.path("src/index.js")
          ),
          javascript(
            """
              function f() {
                  var a = 1;
              }
              """,
            spec -> spec.path("vendor/jquery.js")
          )
        );
    }

    @Test
    void noRowWithoutVariableDeclarations() {
        rewriteRun(
          spec -> spec.afterRecipe(run -> assertThat(run.getDataTableRows(UpgradesAndMigrations.class)).isEmpty()),
          javascript(
            "console.log(\"hello\");",
            spec -> spec.path("src/index.js")
          )
        );
    }
}
