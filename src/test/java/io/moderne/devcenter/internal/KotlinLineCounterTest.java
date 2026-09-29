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
package io.moderne.devcenter.internal;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SearchResult;

import java.util.Optional;
import java.util.function.Function;

import static io.moderne.devcenter.internal.LineCountAssertions.assertParsedCountsMatchSource;
import static io.moderne.devcenter.internal.LineCountAssertions.sanitizedPrint;
import static org.assertj.core.api.Assertions.assertThat;

class KotlinLineCounterTest {

    @Test
    void classesAndFunctions() {
        assertMatchesSource(kotlin(false), 20,
          """
            @file:JvmName("Utils")
            package com.example

            import java.util.concurrent.atomic.AtomicInteger as Counter

            /**
             * KDoc on a data class.
             */
            data class Point(
                val x: Int,
                val y: Int = 0, // trailing comment
            ) : Comparable<Point> {
                override fun compareTo(other: Point): Int =
                    compareValuesBy(this, other, { it.x }, { it.y })
            }

            class Box<T : Comparable<T>>(private val value: T) where T : Any {
                constructor(
                    value: T,
                    label: String
                ) : this(value) {
                    println(label)
                }

                init {
                    require(value != null)
                }

                val size: Int
                    get() = 1

                var label: String = ""
                    set(value) {
                        field = value.trim()
                    }

                companion object {
                    const val MAX = 10
                }
            }

            object Registry {
                private val entries = mutableMapOf<String, Int>()
            }

            enum class Color(val rgb: Int) {
                RED(0xFF0000),
                GREEN(0x00FF00),
                ;
            }

            sealed interface Shape
            typealias Handler<T> =
                (T) -> Unit

            fun String.shout(): String = uppercase() + "!"
            val String.firstChar: Char
                get() = this[0]
            """,
          """
            fun describe(x: Any?, items: List<Int>): String {
                val size = items?.size ?: 0
                if (x !in items) return "missing"
                val n = x as? Int
                val counter = Counter(0)
                items.forEachIndexed { index,
                                       value ->
                    if (value > 3) return@forEachIndexed
                    counter.addAndGet(index)
                }
                val sum = items.fold(0) { acc, i -> acc + i }
                val (first, second) = items
                val f = fun(a: Int): Int {
                    return a * 2
                }
                val g: suspend (Int) -> String = { "$it" }
                return when (x) {
                    is String,
                    is CharSequence -> "text"
                    in 1..10 -> "small"
                    else -> {
                        "other ${'$'}size"
                    }
                }
            }
            """
        );
    }

    @Test
    void stringsAndTemplates() {
        assertMatchesSource(kotlin(false), 20,
          """
            val name = "world"
            val raw = \"""
                |Hello, $name!
                |${name.uppercase()
                }
                \""".trimMargin()
            val escaped = "line\\nline"
            val nested = "outer ${"inner ${name.length}"}"
            val empty = \"""\"""
            """,
          """
            fun render(items: List<String>) = buildString {
                for (item in items) {
                    append(\"""
                        - $item
                        \""")
                }
            }
            """
        );
    }

    @Test
    void scriptsCommentsAndEdges() {
        assertMatchesSource(kotlin(true), 20,
          """
            plugins {
                id("java")
                kotlin("jvm") version "2.0.0"
            }

            dependencies {
                implementation("org.example:lib:1.0")
                testImplementation(
                    "junit:junit:4.13.2"
                )
            }

            tasks.withType<Test> {
                useJUnitPlatform()
            }
            """,
          "#!/usr/bin/env kotlin\nprintln(\"hi\")\n",
          "val x = 1\n// trailing comment",
          "val x = 1\n/* block\n   comment */",
          "/* only a comment */\n"
        );
    }

    @Test
    void deeplyNested() {
        StringBuilder source = new StringBuilder("fun main() {\n");
        for (int i = 0; i < 40; i++) {
            source.append("    ".repeat(i + 1)).append("run {\n");
        }
        for (int i = 39; i >= 0; i--) {
            source.append("    ".repeat(i + 1)).append("}\n");
        }
        source.append("}\n");
        assertMatchesSource(kotlin(false), 5, source.toString());
    }

    @Test
    void markerTextIsNotCounted() {
        K.CompilationUnit cu = (K.CompilationUnit) kotlin(false).apply("class A {\n    fun f() = 1\n}\n").orElseThrow();
        K.CompilationUnit marked = (K.CompilationUnit) new KotlinIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                return "f".equals(identifier.getSimpleName()) ?
                  SearchResult.found(identifier, "multi\nline") :
                  Markup.warn(identifier, new IllegalStateException("one\ntwo"));
            }
        }.visitNonNull(cu, 0);
        assertThat(marked.printAll()).isNotEqualTo(sanitizedPrint(marked));
        assertThat(KotlinLineCounter.count(marked)).isEqualTo(KotlinLineCounter.count(cu)).isEqualTo(3);
    }

    private static void assertMatchesSource(Function<String, Optional<SourceFile>> parser, int mutations, String... fixtures) {
        assertParsedCountsMatchSource(parser, K.CompilationUnit.class, KotlinLineCounter::count, 42, mutations, fixtures);
    }

    private static Function<String, Optional<SourceFile>> kotlin(boolean script) {
        KotlinParser parser = KotlinParser.builder().isKotlinScript(script).build();
        return source -> {
            parser.reset();
            return parser.parse(new InMemoryExecutionContext(), source).findFirst();
        };
    }
}
